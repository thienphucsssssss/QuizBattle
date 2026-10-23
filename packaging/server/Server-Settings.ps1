# Shared by the packaged Server launcher. No passwords are logged or put in arguments.
function Save-NqbSettings($Settings) {
    $nqbConfigDirectory=Join-Path $nqbRoot 'config'
    [IO.Directory]::CreateDirectory($nqbConfigDirectory) | Out-Null
    $nqbDestination=Join-Path $nqbConfigDirectory 'server.json'
    $nqbTemporary=$nqbDestination+'.'+[Guid]::NewGuid().ToString('N')+'.tmp'
    [IO.File]::WriteAllText($nqbTemporary,($Settings | ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $nqbTemporary -Destination $nqbDestination -Force
}
function Read-NqbSettings {
    $nqbConfigFile=Join-Path $nqbRoot 'config/server.json'
    if(Test-Path -LiteralPath $nqbConfigFile) {
        try {$nqbSettings=Get-Content -LiteralPath $nqbConfigFile -Raw -Encoding UTF8 | ConvertFrom-Json}
        catch {throw 'Invalid config/server.json. Check JSON quotes, commas and field names.'}
    } else {
        $nqbSettings=[pscustomobject]@{
            server=[pscustomobject]@{bind='127.0.0.1';port=5050;maxPlayers=10;maxConnections=32}
            database=[pscustomobject]@{path='data/quiz.db'}
            tls=[pscustomobject]@{keyStore='certs/server.p12';password='';passwordProtected=''}
            bootstrap=[pscustomobject]@{adminUsername='admin';adminPassword='';adminPasswordProtected=''}
        }
        # Import public settings from an existing installation, preserving its IP/DB/key paths.
        $nqbLegacy=Join-Path $nqbRoot 'config/server.properties'
        if(Test-Path -LiteralPath $nqbLegacy) {
            foreach($nqbLine in [IO.File]::ReadAllLines($nqbLegacy)) {
                if($nqbLine -match '^\s*(server\.bind|server\.port|server\.maxPlayers|server\.maxConnections|database\.path|tls\.keystore)\s*=\s*(.*?)\s*$') {
                    $nqbField=$Matches[1];$nqbValue=$Matches[2]
                    switch($nqbField) {
                        'server.bind' {$nqbSettings.server.bind=$nqbValue}
                        'server.port' {$nqbSettings.server.port=[int]$nqbValue}
                        'server.maxPlayers' {$nqbSettings.server.maxPlayers=[int]$nqbValue}
                        'server.maxConnections' {$nqbSettings.server.maxConnections=[int]$nqbValue}
                        'database.path' {$nqbSettings.database.path=$nqbValue.Replace('\\','\')}
                        'tls.keystore' {$nqbSettings.tls.keyStore=$nqbValue.Replace('\\','\')}
                    }
                }
            }
        }
        Save-NqbSettings $nqbSettings
    }
    if(-not $nqbSettings.server -or -not $nqbSettings.database -or -not $nqbSettings.tls -or -not $nqbSettings.bootstrap){throw 'Missing server/database/tls/bootstrap section in config/server.json.'}
    foreach($nqbProperty in @('password','passwordProtected')) {if($null -eq $nqbSettings.tls.PSObject.Properties[$nqbProperty]){throw "Missing tls.$nqbProperty in config/server.json."}}
    foreach($nqbProperty in @('adminUsername','adminPassword','adminPasswordProtected')) {if($null -eq $nqbSettings.bootstrap.PSObject.Properties[$nqbProperty]){throw "Missing bootstrap.$nqbProperty in config/server.json."}}
    # Existing installations remain compatible; registration can be disabled by the operator.
    if($null -eq $nqbSettings.PSObject.Properties['registration']){$nqbSettings | Add-Member -NotePropertyName registration -NotePropertyValue ([pscustomobject]@{enabled=$true})}
    if($nqbSettings.registration.enabled -isnot [bool]){throw 'registration.enabled must be true or false.'}
    if($null -eq $nqbSettings.PSObject.Properties['discovery']){$nqbSettings | Add-Member -NotePropertyName discovery -NotePropertyValue ([pscustomobject]@{port=5051})}
    $nqbDiscoveryPort=[int]$nqbSettings.discovery.port
    if($nqbDiscoveryPort -lt 1 -or $nqbDiscoveryPort -gt 65535){throw 'discovery.port must be between 1 and 65535.'}
    return $nqbSettings
}
function Write-NqbRuntimeConfig($Settings) {
    $nqbAddress=$null
    if(-not [Net.IPAddress]::TryParse([string]$Settings.server.bind,[ref]$nqbAddress) -or $nqbAddress.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork){throw 'server.bind must be this Server private LAN IPv4 address.'}
    $nqbIpBytes=$nqbAddress.GetAddressBytes()
    if(-not($nqbIpBytes[0] -eq 10 -or $nqbIpBytes[0] -eq 127 -or ($nqbIpBytes[0] -eq 172 -and $nqbIpBytes[1] -ge 16 -and $nqbIpBytes[1] -le 31) -or ($nqbIpBytes[0] -eq 192 -and $nqbIpBytes[1] -eq 168))){throw 'server.bind must be a private LAN or loopback IPv4 address.'}
    $nqbPort=[int]$Settings.server.port;$nqbPlayers=[int]$Settings.server.maxPlayers;$nqbConnections=[int]$Settings.server.maxConnections
    if($nqbPort -lt 0 -or $nqbPort -gt 65535 -or $nqbPlayers -lt 2 -or $nqbPlayers -gt 10 -or $nqbConnections -lt ($nqbPlayers+1) -or $nqbConnections -gt 128){throw 'Invalid port/player/connection limits in config/server.json.'}
    foreach($nqbPath in @($Settings.database.path,$Settings.tls.keyStore)){if($nqbPath -isnot [string] -or [string]::IsNullOrWhiteSpace($nqbPath) -or $nqbPath -match '[\r\n]'){throw 'Invalid database.path or tls.keyStore.'}}
    $nqbLines=@('# Generated public settings. Edit server.json instead.',"server.bind=$($Settings.server.bind)","server.port=$nqbPort","server.maxPlayers=$nqbPlayers","server.maxConnections=$nqbConnections",('database.path='+$Settings.database.path.Replace('\','/')),('tls.keystore='+$Settings.tls.keyStore.Replace('\','/')))
    $nqbLines+=('registration.enabled='+([string]$Settings.registration.enabled).ToLowerInvariant())
    $nqbLines+=('discovery.port='+[int]$Settings.discovery.port)
    [IO.File]::WriteAllLines((Join-Path $nqbRoot 'config/server.runtime.properties'),[string[]]$nqbLines,[Text.UTF8Encoding]::new($false))
}
function Protect-NqbPassword([string]$Password) {
    Add-Type -AssemblyName System.Security
    $nqbBytes=[Text.Encoding]::UTF8.GetBytes($Password)
    try {return [Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect($nqbBytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))}
    finally {[Array]::Clear($nqbBytes,0,$nqbBytes.Length)}
}
function Read-NqbConfiguredPassword($Settings,[ValidateSet('TLS','Admin')][string]$Kind) {
    $nqbSection=if($Kind -eq 'TLS'){$Settings.tls}else{$Settings.bootstrap}
    $nqbPlainField=if($Kind -eq 'TLS'){'password'}else{'adminPassword'}
    $nqbProtectedField=if($Kind -eq 'TLS'){'passwordProtected'}else{'adminPasswordProtected'}
    $nqbPassword=$nqbSection.$nqbPlainField
    if($nqbPassword -isnot [string]){throw "The $Kind password must be a JSON string."}
    if($nqbPassword.Length -gt 0) {
        if(($Kind -eq 'TLS' -and $nqbPassword.Length -lt 6) -or ($Kind -eq 'Admin' -and ($nqbPassword.Length -lt 8 -or $nqbPassword.Length -gt 128))){throw "Invalid $Kind password length in config/server.json."}
        $nqbSection.$nqbProtectedField=Protect-NqbPassword $nqbPassword
        $nqbSection.$nqbPlainField=''
        Save-NqbSettings $Settings
        return $nqbPassword
    }
    if([string]::IsNullOrEmpty($nqbSection.$nqbProtectedField)){throw "Missing $Kind password. Run configure.bat once, or fill the password field in config/server.json."}
    $nqbBytes=$null
    try {
        Add-Type -AssemblyName System.Security
        $nqbBytes=[Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String($nqbSection.$nqbProtectedField),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
        return [Text.Encoding]::UTF8.GetString($nqbBytes)
    } catch {throw "Cannot decrypt the saved $Kind password. Run configure.bat under the Windows account that will run Server."}
    finally {if($nqbBytes){[Array]::Clear($nqbBytes,0,$nqbBytes.Length)}}
}
function Resolve-NqbFile([string]$Path) {
    if([IO.Path]::IsPathRooted($Path)){return [IO.Path]::GetFullPath($Path)}
    return [IO.Path]::GetFullPath((Join-Path $nqbRoot $Path))
}
function Assert-NqbKeystorePassword($Settings) {
    $nqbKey=Resolve-NqbFile $Settings.tls.keyStore
    if(-not(Test-Path -LiteralPath $nqbKey)){return}
    $nqbPreviousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $nqbDiagnostic=@(& (Join-Path $nqbRoot 'runtime/bin/keytool.exe') -list -keystore $nqbKey -storepass:env NQB_KEYSTORE_PASSWORD 2>&1)
        $nqbKeyExit=$LASTEXITCODE
    } finally {$ErrorActionPreference=$nqbPreviousPreference}
    if($nqbKeyExit -ne 0){throw 'TLS keystore password is incorrect, or keyStore cannot be read. Run configure.bat to correct it; existing certificates were preserved.'}
}
