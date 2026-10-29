param([switch]$Offline,[string]$WixBin='',[string]$Version='0.6.2',
    [Parameter(Mandatory=$true)][string]$ClientTruststore,
    [string]$ServerHost='auto',[ValidateRange(1,65535)][int]$ServerPort=5050,[ValidateRange(1,65535)][int]$DiscoveryPort=5051)
$ErrorActionPreference='Stop'
$nqbRoot=Split-Path -Parent $PSScriptRoot
$nqbTag=Get-Date -Format 'yyyyMMdd-HHmmss'
$nqbOutput=Join-Path $nqbRoot "dist/QuizBattle-Windows-$nqbTag"
$nqbStage=Join-Path $nqbRoot "build/windows-$nqbTag"
$nqbOldPath=$env:PATH
if(-not $WixBin){$WixBin=Join-Path $nqbRoot 'build/tools/wix-3.14.1/tools'}
if(-not(Test-Path -LiteralPath (Join-Path $WixBin 'candle.exe'))){throw 'WiX 3 tools required. Pass -WixBin pointing to candle.exe/light.exe directory.'}
if(Test-Path -LiteralPath $nqbOutput){throw 'Output already exists; no release overwritten'}
$ClientTruststore=(Resolve-Path -LiteralPath $ClientTruststore).Path
# A Client gets trusted certificate entries only, never server.p12 or any private key.
$nqbTrustInfo=@(& keytool '-J-Duser.language=en' '-J-Duser.country=US' -list -keystore $ClientTruststore -storetype PKCS12 -storepass changeit 2>&1)
if($LASTEXITCODE -ne 0 -or ($nqbTrustInfo -join ' ') -match 'PrivateKeyEntry|SecretKeyEntry' -or ($nqbTrustInfo -join ' ') -notmatch 'trustedCertEntry'){throw 'ClientTruststore must contain only public trusted certificates, with the public-store password changeit.'}
if($ServerHost -notmatch '^[a-zA-Z0-9.:-]+$'){throw 'Invalid ServerHost'}
function Assert-NqbExit([string]$Step){if($LASTEXITCODE -ne 0){throw "$Step failed ($LASTEXITCODE)"}}
Push-Location -LiteralPath $nqbRoot
try {
    $env:PATH=$WixBin+';'+$env:PATH
    $nqbMaven=@('-B','-ntp');if($Offline){$nqbMaven+='-o'}
    & mvn @nqbMaven clean install
    Assert-NqbExit 'Build and tests'
    New-Item -ItemType Directory -Path $nqbOutput,$nqbStage -Force | Out-Null
    $nqbRuntime=Join-Path $nqbStage 'runtime'
    & jlink --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.zipfs --strip-debug --no-header-files --no-man-pages --output $nqbRuntime
    Assert-NqbExit 'Embedded Java runtime'
    foreach($nqbRole in @('Client','Server')) {
        $nqbInput=Join-Path $nqbStage "$nqbRole-input"
        New-Item -ItemType Directory -Path $nqbInput -Force | Out-Null
        $nqbModule=if($nqbRole -eq 'Client'){'nqb-admin-client'}else{'nqb-server'}
        & mvn @nqbMaven -f "$nqbModule/pom.xml" org.apache.maven.plugins:maven-dependency-plugin:3.7.0:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$nqbInput"
        Assert-NqbExit 'Runtime dependencies'
        $nqbJar=Get-ChildItem -LiteralPath "$nqbModule/target" -Filter "$nqbModule-*.jar" | Where-Object Name -notmatch '(sources|javadoc|tests)' | Select-Object -Last 1
        if(-not $nqbJar){throw 'Missing module JAR'}
        Copy-Item -LiteralPath $nqbJar.FullName -Destination $nqbInput
        if($nqbRole -eq 'Client') {
            New-Item -ItemType Directory -Path "$nqbInput/certs" -Force | Out-Null
            Copy-Item -LiteralPath $ClientTruststore -Destination "$nqbInput/certs/client-truststore.p12"
            [IO.File]::WriteAllLines("$nqbInput/client.properties",[string[]]@('# Public LAN profile. No user passwords or private keys.',"server.host=$ServerHost","server.port=$ServerPort","discovery.port=$DiscoveryPort",'tls.truststore=certs/client-truststore.p12','tls.truststorePassword=changeit'),[Text.UTF8Encoding]::new($false))
            $nqbImageRoot=Join-Path $nqbStage 'image'
            & jpackage --type app-image --name QuizBattle --app-version $Version --vendor 'Nhom VUA - HUTECH' --description 'Network Quiz Battle LAN Client' --input $nqbInput --main-jar $nqbJar.Name --main-class vn.edu.hutech.nqb.admin.AdminMain --runtime-image $nqbRuntime --dest $nqbImageRoot --java-options '-Dnqb.app.dir=$APPDIR' --java-options '-Dfile.encoding=UTF-8' --java-options '-Dstdout.encoding=UTF-8' --java-options '-Dstderr.encoding=UTF-8'
            Assert-NqbExit 'Client executable image'
            $nqbImage=Join-Path $nqbImageRoot 'QuizBattle'
            & jpackage --type exe --name QuizBattle --app-version $Version --vendor 'Nhom VUA - HUTECH' --app-image $nqbImage --dest $nqbOutput --win-per-user-install --win-menu --win-menu-group 'Quiz Battle' --win-shortcut --win-dir-chooser --win-upgrade-uuid '213b9051-4e86-4ccd-83b8-20acc0d9458f'
            Assert-NqbExit 'Client EXE installer'
            Move-Item -LiteralPath (Join-Path $nqbOutput "QuizBattle-$Version.exe") -Destination (Join-Path $nqbOutput 'QuizBattle-Setup.exe')
        } else {
            $nqbServer=Join-Path $nqbOutput 'Server'
            New-Item -ItemType Directory -Path "$nqbServer/scripts","$nqbServer/config","$nqbServer/certs","$nqbServer/data" -Force | Out-Null
            Copy-Item -LiteralPath $nqbInput -Destination "$nqbServer/lib" -Recurse
            Copy-Item -LiteralPath $nqbRuntime -Destination "$nqbServer/runtime" -Recurse
            Get-ChildItem -LiteralPath 'packaging/server' -Filter '*.bat' | ForEach-Object {Copy-Item -LiteralPath $_.FullName -Destination $nqbServer}
            Copy-Item -LiteralPath 'packaging/server/Server-Control.ps1','packaging/server/Server-Settings.ps1','packaging/server/Allow-Lan.ps1','packaging/server/Allow-Tailscale.ps1','scripts/Setup-Lan.ps1' -Destination "$nqbServer/scripts"
            Copy-Item -LiteralPath 'config/server.example.properties' -Destination "$nqbServer/config/server.properties"
            Copy-Item -LiteralPath 'packaging/server/server.json' -Destination "$nqbServer/config/server.json"
            Copy-Item -LiteralPath 'packaging/README-Windows.txt' -Destination "$nqbServer/README.txt"
            if(-not(Test-Path -LiteralPath "$nqbServer/runtime/bin/keytool.exe")){throw 'Embedded runtime is missing keytool'}
        }
    }
    Copy-Item -LiteralPath 'packaging/README-Windows.txt' -Destination "$nqbOutput/README.txt"
    # Only the explicitly validated public Client truststore is bundled inside the installer.
    # No user's private keys, database, passwords or local Server config are distributed.
    $nqbSensitive=Get-ChildItem -LiteralPath $nqbOutput -Recurse -File | Where-Object {$_.Name -match '\.(p12|jks|db|key)$'}
    if($nqbSensitive){throw 'Unexpected credential/database in release; do not distribute'}
    $nqbReleaseConfig=Get-Content -LiteralPath "$nqbOutput/Server/config/server.json" -Raw | ConvertFrom-Json
    if($nqbReleaseConfig.tls.password -or $nqbReleaseConfig.tls.passwordProtected -or $nqbReleaseConfig.bootstrap.adminPassword -or $nqbReleaseConfig.bootstrap.adminPasswordProtected){throw 'Startup credentials must not be distributed in the release'}
    Compress-Archive -LiteralPath "$nqbOutput/Server" -DestinationPath "$nqbOutput/QuizBattle-Server.zip"
    $nqbChecks=Get-FileHash -LiteralPath "$nqbOutput/QuizBattle-Setup.exe","$nqbOutput/QuizBattle-Server.zip" -Algorithm SHA256 | ForEach-Object {$_.Hash.ToLowerInvariant()+'  '+[IO.Path]::GetFileName($_.Path)}
    [IO.File]::WriteAllLines("$nqbOutput/SHA256SUMS.txt",$nqbChecks,[Text.UTF8Encoding]::new($false))
    Write-Host "WINDOWS_RELEASE_READY $nqbOutput"
    Write-Host "CLIENT_TEST_IMAGE $nqbStage/image/QuizBattle/QuizBattle.exe"
} finally {$env:PATH=$nqbOldPath;Pop-Location}
