param([switch]$InitAdmin,[string]$Config='config/server.properties')
$ErrorActionPreference='Stop'
# A new Windows PowerShell console can reset to an OEM code page even when
# run.bat used chcp 65001. Match the Server's UTF-8 log bytes in this process.
$nqbUtf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$nqbUtf8
[Console]::OutputEncoding=$nqbUtf8
$OutputEncoding=$nqbUtf8
$nqbRoot=Split-Path -Parent $PSScriptRoot
$nqbSecretName=if($InitAdmin){'NQB_BOOTSTRAP_PASSWORD'}else{'NQB_KEYSTORE_PASSWORD'}
$nqbOldSecret=[Environment]::GetEnvironmentVariable($nqbSecretName,'Process')
Push-Location -LiteralPath $nqbRoot
try {
    $nqbArgs='"--config='+$Config+'"'
    $nqbPrompt=if($InitAdmin){'Mat khau Admin moi (8-128 ky tu)'}else{'Mat khau keystore TLS Server (it nhat 6 ky tu)'}
    $nqbMinimumLength=if($InitAdmin){8}else{6}
    $nqbMaximumLength=if($InitAdmin){128}else{[int]::MaxValue}
    Write-Host 'Nhap duoc ca chu va so. Ky tu hien thanh dau *; nhap xong bam Enter.'
    while($true) {
        $nqbSecure=Read-Host $nqbPrompt -AsSecureString
        if($nqbSecure.Length -ge $nqbMinimumLength -and $nqbSecure.Length -le $nqbMaximumLength){break}
        $nqbSecure.Dispose()
        Write-Host $(if($InitAdmin){'Mat khau Admin can 8-128 ky tu. Vui long nhap lai.'}else{'Mat khau TLS can it nhat 6 ky tu. Vui long nhap lai.'}) -ForegroundColor Yellow
    }
    $nqbPtr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($nqbSecure)
    try{[Environment]::SetEnvironmentVariable($nqbSecretName,[Runtime.InteropServices.Marshal]::PtrToStringBSTR($nqbPtr),'Process')}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($nqbPtr);$nqbSecure.Dispose()}
    if($InitAdmin){$nqbArgs+=' --init-admin'}
    & mvn -o -B -ntp -f nqb-server/pom.xml exec:java '-Dexec.mainClass=vn.edu.hutech.nqb.server.ServerMain' "-Dexec.args=$nqbArgs"
    if($LASTEXITCODE -ne 0){throw 'Server command failed. See non-secret diagnostics above.'}
} finally {[Environment]::SetEnvironmentVariable($nqbSecretName,$nqbOldSecret,'Process');Pop-Location}
