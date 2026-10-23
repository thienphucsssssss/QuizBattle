param([ValidateSet('Start','Setup','Configure','InitAdmin')][string]$Action='Start')
$ErrorActionPreference='Stop'
$nqbRoot=Split-Path -Parent $PSScriptRoot
$nqbJava=Join-Path $nqbRoot 'runtime/bin/java.exe'
$nqbOldPath=$env:PATH
$nqbOldEncoding=[Console]::OutputEncoding
$nqbOldBootstrap=$env:NQB_BOOTSTRAP_PASSWORD
$nqbOldKey=$env:NQB_KEYSTORE_PASSWORD
$nqbLock=$null
. (Join-Path $PSScriptRoot 'Server-Settings.ps1')
function Invoke-NqbJava([string[]]$NqbArguments) {
    & $nqbJava '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp (Join-Path $nqbRoot 'lib/*') vn.edu.hutech.nqb.server.ServerMain '--config=config/server.runtime.properties' @NqbArguments
    if($LASTEXITCODE -ne 0){throw 'Server command failed. Data was not reset. Read the diagnostic above.'}
}
function Set-NqbSecret([string]$Name,[string]$Prompt) {
    $nqbAdmin=$Name -eq 'NQB_BOOTSTRAP_PASSWORD'
    $nqbMinimum=if($nqbAdmin){8}else{6}
    while($true) {
        $nqbSecure=$null;$nqbRepeat=$null;$nqbPointer=[IntPtr]::Zero;$nqbRepeatPointer=[IntPtr]::Zero
        try {
            $nqbSecure=Read-Host $Prompt -AsSecureString
            if($nqbSecure.Length -lt $nqbMinimum -or ($nqbAdmin -and $nqbSecure.Length -gt 128)) {
                Write-Host $(if($nqbAdmin){'Admin password must contain 8-128 characters. Please try again.'}else{'TLS keystore password needs at least 6 characters. Please try again.'}) -ForegroundColor Yellow
                continue
            }
            $nqbPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($nqbSecure)
            if($nqbAdmin) {
                $nqbRepeat=Read-Host 'Confirm new admin password' -AsSecureString
                $nqbRepeatPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($nqbRepeat)
                if([Runtime.InteropServices.Marshal]::PtrToStringBSTR($nqbPointer) -cne [Runtime.InteropServices.Marshal]::PtrToStringBSTR($nqbRepeatPointer)) {
                    Write-Host 'Passwords do not match. Please try again.' -ForegroundColor Yellow
                    continue
                }
            }
            [Environment]::SetEnvironmentVariable($Name,[Runtime.InteropServices.Marshal]::PtrToStringBSTR($nqbPointer),'Process')
            return
        } finally {
            if($nqbPointer -ne [IntPtr]::Zero){[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($nqbPointer)}
            if($nqbRepeatPointer -ne [IntPtr]::Zero){[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($nqbRepeatPointer)}
            if($nqbSecure){$nqbSecure.Dispose()};if($nqbRepeat){$nqbRepeat.Dispose()}
        }
    }
}
function Test-NqbAdmin {
    $nqbStatus=@(& $nqbJava '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp (Join-Path $nqbRoot 'lib/*') vn.edu.hutech.nqb.server.ServerMain '--config=config/server.runtime.properties' '--check-admin')
    if($LASTEXITCODE -ne 0){throw 'Cannot check Admin status. Existing data was preserved.'}
    if($nqbStatus -contains 'NQB_ADMIN_PRESENT'){return $true}
    if($nqbStatus -contains 'NQB_ADMIN_MISSING'){return $false}
    throw 'Server files are from different versions. Update the Server scripts and library together.'
}
function Configure-NqbStartup($Settings) {
    Write-Host 'ONE-TIME SETUP. Press Enter to keep the existing IP/port.'
    $nqbIp=Read-Host "Server LAN IPv4 [$($Settings.server.bind)]"
    if(-not [string]::IsNullOrWhiteSpace($nqbIp)){$Settings.server.bind=$nqbIp.Trim()}
    $nqbPort=Read-Host "Server TCP port [$($Settings.server.port)]"
    if(-not [string]::IsNullOrWhiteSpace($nqbPort)){$Settings.server.port=[int]$nqbPort}
    Write-NqbRuntimeConfig $Settings
    Set-NqbSecret 'NQB_KEYSTORE_PASSWORD' 'TLS keystore password (saved for future starts)'
    Assert-NqbKeystorePassword $Settings
    $Settings.tls.password='';$Settings.tls.passwordProtected=Protect-NqbPassword $env:NQB_KEYSTORE_PASSWORD
    if(-not(Test-NqbAdmin)) {
        Set-NqbSecret 'NQB_BOOTSTRAP_PASSWORD' 'New admin password (8-128 characters)'
        $Settings.bootstrap.adminPassword='';$Settings.bootstrap.adminPasswordProtected=Protect-NqbPassword $env:NQB_BOOTSTRAP_PASSWORD
    }
    Save-NqbSettings $Settings
    Write-Host 'Saved config/server.json. From now on, double-click start.bat; no password prompt.'
}
Push-Location -LiteralPath $nqbRoot
try {
    [Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
    if(-not(Test-Path -LiteralPath $nqbJava)){throw 'Missing bundled runtime. Extract the entire Server folder before running start.bat.'}
    $env:PATH=(Join-Path $nqbRoot 'runtime/bin')+';'+$env:PATH
    # Prevent two packaged launchers from changing one database/config at once.
    try {$nqbLock=[IO.File]::Open((Join-Path $nqbRoot '.server.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}
    catch {throw 'Another Server/setup is using this folder. Stop it before continuing.'}
    $nqbSettings=Read-NqbSettings
    Write-NqbRuntimeConfig $nqbSettings
    switch($Action) {
        {$_ -eq 'Setup' -or $_ -eq 'Configure'} {Configure-NqbStartup $nqbSettings}
        'InitAdmin' {Set-NqbSecret 'NQB_BOOTSTRAP_PASSWORD' 'New admin password (8-128 characters)';Invoke-NqbJava @('--init-admin')}
        'Start' {
            $env:NQB_KEYSTORE_PASSWORD=Read-NqbConfiguredPassword $nqbSettings 'TLS'
            $nqbKey=Resolve-NqbFile $nqbSettings.tls.keyStore
            if(-not(Test-Path -LiteralPath $nqbKey)) {
                if($nqbKey -ne [IO.Path]::GetFullPath((Join-Path $nqbRoot 'certs/server.p12'))){throw 'For first-time certificate generation, tls.keyStore must be certs/server.p12.'}
                & (Join-Path $PSScriptRoot 'Setup-Lan.ps1') -ServerAddress $nqbSettings.server.bind -SecretEnvironment 'NQB_KEYSTORE_PASSWORD'
            }
            Assert-NqbKeystorePassword $nqbSettings
            if(-not(Test-NqbAdmin)) {
                $env:NQB_BOOTSTRAP_PASSWORD=Read-NqbConfiguredPassword $nqbSettings 'Admin'
                if($nqbSettings.bootstrap.adminUsername -notmatch '^[a-zA-Z0-9_.-]{3,40}$'){throw 'Invalid bootstrap.adminUsername in config/server.json.'}
                Invoke-NqbJava @('--init-admin',"--username=$($nqbSettings.bootstrap.adminUsername)")
            }
            if($nqbSettings.bootstrap.adminPassword -or $nqbSettings.bootstrap.adminPasswordProtected){$nqbSettings.bootstrap.adminPassword='';$nqbSettings.bootstrap.adminPasswordProtected='';Save-NqbSettings $nqbSettings}
            $env:NQB_BOOTSTRAP_PASSWORD=$null
            Write-Host 'Starting TLS Server. Keep this window open. Ctrl+C stops the Server.'
            Invoke-NqbJava @()
        }
    }
} catch {Write-Host $_.Exception.Message -ForegroundColor Red;exit 1}
finally {
    if($nqbLock){$nqbLock.Dispose()}
    $env:PATH=$nqbOldPath;$env:NQB_BOOTSTRAP_PASSWORD=$nqbOldBootstrap;$env:NQB_KEYSTORE_PASSWORD=$nqbOldKey
    [Console]::OutputEncoding=$nqbOldEncoding;Pop-Location
}
