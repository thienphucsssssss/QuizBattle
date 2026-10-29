param([string]$ServerAddress='auto',[string]$SecretEnvironment='')
$ErrorActionPreference='Stop'
$nqbRoot=Split-Path -Parent $PSScriptRoot
$nqbAddress=$null
$nqbAuto=$ServerAddress -in @('auto','0.0.0.0')
if(-not $nqbAuto) {
    if(-not [System.Net.IPAddress]::TryParse($ServerAddress,[ref]$nqbAddress) -or $nqbAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork){throw 'Use auto or an IPv4 address of this server.'}
    $nqbBytes=$nqbAddress.GetAddressBytes()
    if(-not($nqbBytes[0] -eq 10 -or $nqbBytes[0] -eq 127 -or ($nqbBytes[0] -eq 172 -and $nqbBytes[1] -ge 16 -and $nqbBytes[1] -le 31) -or ($nqbBytes[0] -eq 192 -and $nqbBytes[1] -eq 168))){throw 'Use auto or a private LAN/loopback IPv4 address.'}
}
$nqbCerts=Join-Path $nqbRoot 'certs'
$nqbKey=Join-Path $nqbCerts 'server.p12'
$nqbTrust=Join-Path $nqbCerts 'client-truststore.p12'
$nqbPublic=Join-Path $nqbCerts 'server.cer'
foreach($nqbFile in @($nqbKey,$nqbTrust,$nqbPublic)){if(Test-Path -LiteralPath $nqbFile){throw "Existing certificate file preserved: $nqbFile. Back up/rotate explicitly; setup never overwrites keys."}}
New-Item -ItemType Directory -Path $nqbCerts -Force | Out-Null
$nqbSecure=$null
if($SecretEnvironment) {
    if($SecretEnvironment -notmatch '^NQB_[A-Z0-9_]+$'){throw 'Invalid secret environment variable name'}
    $nqbValue=[Environment]::GetEnvironmentVariable($SecretEnvironment,'Process')
    if([string]::IsNullOrEmpty($nqbValue)){throw 'Missing TLS setup password'}
    $nqbSecure=[Security.SecureString]::new()
    foreach($nqbCharacter in $nqbValue.ToCharArray()){$nqbSecure.AppendChar($nqbCharacter)}
    $nqbValue=$null
} else {$nqbSecure=Read-Host 'Mat khau keystore TLS (it nhat 6 ky tu, khong phai mat khau Admin)' -AsSecureString}
$nqbPtr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($nqbSecure)
$nqbOld=[Environment]::GetEnvironmentVariable('NQB_SETUP_SECRET','Process')
try {
    [Environment]::SetEnvironmentVariable('NQB_SETUP_SECRET',[Runtime.InteropServices.Marshal]::PtrToStringBSTR($nqbPtr),'Process')
    if($env:NQB_SETUP_SECRET.Length -lt 6){throw 'Keystore password needs at least 6 characters'}
    $nqbSan=if($nqbAuto -or $ServerAddress -eq '127.0.0.1'){'SAN=dns:quizbattle-server,dns:localhost,ip:127.0.0.1'}else{"SAN=dns:quizbattle-server,ip:$ServerAddress,ip:127.0.0.1,dns:localhost"}
    & keytool -genkeypair -alias nqb-server -keyalg RSA -keysize 3072 -validity 365 -dname 'CN=Network Quiz Battle LAN' -ext $nqbSan -storetype PKCS12 -keystore $nqbKey -storepass:env NQB_SETUP_SECRET
    if($LASTEXITCODE -ne 0){throw 'Certificate generation failed'}
    & keytool -exportcert -alias nqb-server -keystore $nqbKey -storepass:env NQB_SETUP_SECRET -file $nqbPublic
    if($LASTEXITCODE -ne 0){throw 'Public certificate export failed'}
    # Public certificate only; this password is NOT the private-key or account password.
    & keytool -importcert -noprompt -alias nqb-server -file $nqbPublic -storetype PKCS12 -keystore $nqbTrust -storepass changeit
    if($LASTEXITCODE -ne 0){throw 'Client truststore creation failed'}
    # Configuration writes are performed by this explicit setup script, never credentials.
    $nqbConfig=Join-Path $nqbRoot 'config/server.properties'
    $nqbLines=[IO.File]::ReadAllText($nqbConfig)
    $nqbLines=[regex]::Replace($nqbLines,'(?m)^server\.bind=.*$',"server.bind=$ServerAddress")
    [IO.File]::WriteAllText($nqbConfig,$nqbLines,[Text.UTF8Encoding]::new($false))
    Write-Host 'TLS Server identity ready. IP changes do not require new keys with Client 0.4.0+.'
    Write-Host 'Private server.p12 stays on Server. No firewall/router settings were changed.'
    & keytool -printcert -file $nqbPublic
} finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($nqbPtr);$nqbSecure.Dispose();[Environment]::SetEnvironmentVariable('NQB_SETUP_SECRET',$nqbOld,'Process')}
