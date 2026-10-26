@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
pushd "%~dp0" || exit /b 1
set "NQB_DIRECT=0"
set "NQB_RESULT=0"

if /i "%~1"=="help" goto help
if /i "%~1"=="--help" goto help
if "%~1"=="" goto prepare
set "NQB_DIRECT=1"
if /i "%~1"=="build" goto prepare
if /i "%~1"=="setup" goto prepare
if /i "%~1"=="server" goto prepare
if /i "%~1"=="admin" goto prepare
if /i "%~1"=="player" goto prepare
echo Lenh khong hop le. Dung: run.bat [build^|setup^|server^|admin^|player]
set "NQB_RESULT=1"
goto finish

:prepare
if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
where javac >nul 2>&1
if errorlevel 1 goto missing_java
java -XshowSettings:properties -version 2>&1 | findstr /r /c:"java.specification.version = 21$" >nul
if errorlevel 1 goto missing_java
call :find_maven
if errorlevel 1 (
    echo [LOI] Khong tim thay Maven. Can Maven 3.9.x trong PATH hoac MAVEN_HOME.
    goto failed
)
if "%NQB_DIRECT%"=="1" goto direct

:menu
echo.
echo =============== QUIZ BATTLE ===============
echo 1. Build / cap nhat sau khi sua code
echo 2. Thiet lap Server lan dau tren may nay
echo 3. Chay Server
echo 4. Mo Client Admin / Giang vien
echo 5. Mo Client Hoc sinh
echo 0. Thoat
echo.
echo Lan dau: chon 2, sau do 3. Doi NQB_SERVER_READY roi chon 4 hoac 5.
echo Lan sau: chon 3, 4 hoac 5 de dung ban da build. Sua code: chon 1 de cap nhat.
choice /c 123450 /n /m "Bam mot phim [1-5,0], khong can Enter: "
if errorlevel 6 goto finish
if errorlevel 5 (call :player & goto menu)
if errorlevel 4 (call :admin & goto menu)
if errorlevel 3 (call :server & goto menu)
if errorlevel 2 (call :setup & goto menu)
if errorlevel 1 (call :build & goto menu)
goto menu

:find_maven
where mvn >nul 2>&1
if not errorlevel 1 exit /b 0
if exist "%MAVEN_HOME%\bin\mvn.cmd" set "PATH=%MAVEN_HOME%\bin;%PATH%"
if exist "%M2_HOME%\bin\mvn.cmd" set "PATH=%M2_HOME%\bin;%PATH%"
where mvn >nul 2>&1
if not errorlevel 1 exit /b 0
for /d %%D in ("%ProgramFiles%\NetBeans-*") do if exist "%%~D\netbeans\java\maven\bin\mvn.cmd" set "PATH=%%~D\netbeans\java\maven\bin;%PATH%"
where mvn >nul 2>&1
exit /b %errorlevel%

:build
if exist "tmp\run-build.ok" del /q "tmp\run-build.ok"
echo [BUILD] Bien dich, kiem thu va luu ban chay. Chi tai thu vien con thieu neu can.
echo Lan dau can Internet. Cac lan mo app sau se dung lai ban da build.
call mvn -B -ntp install
if errorlevel 1 exit /b 1
rem Cache the launcher plugin because Run-Quiz.ps1 starts Maven offline.
call mvn -B -ntp exec:help -Ddetail=false
if errorlevel 1 exit /b 1
if not exist tmp mkdir tmp
if errorlevel 1 exit /b 1
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Source-Build.ps1" -Record
if errorlevel 1 exit /b 1
echo [OK] Build thanh cong.
echo Neu Server/Client cu dang mo, hay tat va mo lai ca hai de dung ban cap nhat.
exit /b 0

:ensure_build
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Source-Build.ps1" >nul 2>&1
if not errorlevel 1 (
    echo [RUN] Dang dung ban da build. Sua code: chon 1 de cap nhat.
    exit /b 0
)
echo [SETUP] Chua co ban chay san sang. Chuan bi lan dau hoac khoi phuc file build bi thieu.
goto build

:setup
call :ensure_build
if errorlevel 1 exit /b 1
if not exist "config\server.properties" copy /y "config\server.example.properties" "config\server.properties" >nul
if not exist "config\server.properties" exit /b 1
if not exist "client.properties" copy /y "client.example.properties" "client.properties" >nul
if not exist "client.properties" exit /b 1
if exist "certs\server.p12" goto check_admin
echo [SETUP] Tao chung chi TLS cho 127.0.0.1, dung de chay tren cung may.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Setup-Lan.ps1" -ServerAddress 127.0.0.1
if errorlevel 1 exit /b 1

:check_admin
call mvn -o -B -ntp -f nqb-server/pom.xml exec:java "-Dexec.mainClass=vn.edu.hutech.nqb.server.ServerMain" "-Dexec.args=--check-admin" >"tmp\run-check-admin.log" 2>&1
if errorlevel 1 (
    type "tmp\run-check-admin.log"
    exit /b 1
)
findstr /c:"NQB_ADMIN_PRESENT" "tmp\run-check-admin.log" >nul
if not errorlevel 1 (
    echo [OK] Da co Admin. Dung tai khoan hien tai de dang nhap.
    exit /b 0
)
findstr /c:"NQB_ADMIN_MISSING" "tmp\run-check-admin.log" >nul
if errorlevel 1 exit /b 1
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Start-Server.ps1" -InitAdmin
if errorlevel 1 exit /b 1
echo [OK] Da thiet lap Server. Ten dang nhap Admin: admin.
exit /b 0

:server
call :ensure_build
if errorlevel 1 exit /b 1
if not exist "config\server.properties" goto missing_server
if not exist "certs\server.p12" goto missing_server
start "QuizBattle - Server" powershell.exe -NoLogo -NoProfile -NoExit -ExecutionPolicy Bypass -File "%~dp0scripts\Start-Server.ps1"
exit /b %errorlevel%

:admin
set "NQB_ROLE=Admin"
goto client

:player
set "NQB_ROLE=Player"

:client
call :ensure_build
if errorlevel 1 exit /b 1
if not exist "client.properties" copy /y "client.example.properties" "client.properties" >nul
if not exist "client.properties" exit /b 1
start "QuizBattle - %NQB_ROLE%" powershell.exe -NoLogo -NoProfile -NoExit -ExecutionPolicy Bypass -File "%~dp0scripts\Run-Quiz.ps1" -Role %NQB_ROLE%
exit /b %errorlevel%

:missing_server
echo [LOI] Chua cau hinh Server. Chon 2 hoac chay run.bat setup truoc.
exit /b 1

:missing_java
echo [LOI] Can JDK 21 x64. Kiem tra JAVA_HOME va PATH bang java -version.
goto failed

:failed
set "NQB_RESULT=1"
if "%NQB_DIRECT%"=="0" pause
goto finish

:help
echo Dung: run.bat [build^|setup^|server^|admin^|player]
echo Nhan doi run.bat de mo menu.
echo Lan dau tren cung may: setup, server, doi NQB_SERVER_READY, admin/player.
echo Chi tu build khi chua chuan bi hoac thieu file chay. Lan dau can Internet.
echo Lan sau dung ban da build, chay offline. Can JDK 21 va Maven 3.9.x.
echo Sau khi sua code: chon 1 hoac chay run.bat build de cap nhat.
echo Sau khi cap nhat, tat Server/Client cu roi mo lai ca hai.
echo Chay nhieu may LAN: xem docs\PHAT_TRIEN_NHOM.md, muc 3.
goto finish

:direct
call :%~1
set "NQB_RESULT=%errorlevel%"

:finish
popd
exit /b %NQB_RESULT%
