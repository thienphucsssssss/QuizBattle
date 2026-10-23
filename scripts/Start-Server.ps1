param([switch]$InitAdmin,[string]$Config='config/server.properties')
$ErrorActionPreference='Stop'
$nqbRoot=Split-Path -Parent $PSScriptRoot
$nqbSecretName=if($InitAdmin){'NQB_BOOTSTRAP_PASSWORD'}else{'NQB_KEYSTORE_PASSWORD'}
$nqbOldSecret=[Environment]::GetEnvironmentVariable($nqbSecretName,'Process')
Push-Location -LiteralPath $nqbRoot
try {
    $nqbArgs='"--config='+$Config+'"'
    $nqbPrompt=if($InitAdmin){'Mat khau Admin moi (8-128 ky tu)'}else{'Mat khau keystore TLS Server'}
    $nqbSecure=Read-Host $nqbPrompt -AsSecureString
    $nqbPtr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($nqbSecure)
    try{[Environment]::SetEnvironmentVariable($nqbSecretName,[Runtime.InteropServices.Marshal]::PtrToStringBSTR($nqbPtr),'Process')}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($nqbPtr);$nqbSecure.Dispose()}
    if($InitAdmin){$nqbArgs+=' --init-admin'}
    & mvn -o -B -ntp -f nqb-server/pom.xml exec:java '-Dexec.mainClass=vn.edu.hutech.nqb.server.ServerMain' "-Dexec.args=$nqbArgs"
    if($LASTEXITCODE -ne 0){throw 'Server command failed. See non-secret diagnostics above.'}
} finally {[Environment]::SetEnvironmentVariable($nqbSecretName,$nqbOldSecret,'Process');Pop-Location}
