param(
    [ValidateSet('Player','Admin','Server')][string]$Role = 'Player',
    [switch]$InitAdmin
)
$ErrorActionPreference = 'Stop'
$nqbMain = switch ($Role) { 'Player' { 'vn.edu.hutech.nqb.player.PlayerMain' } 'Admin' { 'vn.edu.hutech.nqb.admin.AdminMain' } 'Server' { 'vn.edu.hutech.nqb.server.ServerMain' } }
$nqbSecretName = if ($InitAdmin) { 'NQB_BOOTSTRAP_PASSWORD' } else { 'NQB_KEYSTORE_PASSWORD' }
$nqbOldSecret = [Environment]::GetEnvironmentVariable($nqbSecretName, 'Process')
Push-Location -LiteralPath $PSScriptRoot
try {
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'lib'))) { throw 'Run this launcher inside a built Client or Server bundle.' }
    $nqbArgs = @()
    if ($Role -eq 'Server') {
            $nqbSecure = Read-Host $(if ($InitAdmin) { 'Mat khau Admin moi (8-128 ky tu)' } else { 'Mat khau keystore TLS Server' }) -AsSecureString
            $nqbPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($nqbSecure)
            try { [Environment]::SetEnvironmentVariable($nqbSecretName, [Runtime.InteropServices.Marshal]::PtrToStringBSTR($nqbPointer), 'Process') }
            finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($nqbPointer); $nqbSecure.Dispose() }
            if ($InitAdmin) { $nqbArgs += '--init-admin' }
    } else {
        if ($InitAdmin) { throw 'InitAdmin is Server-only' }
    }
    & java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp (Join-Path $PSScriptRoot 'lib/*') $nqbMain @nqbArgs
    if ($LASTEXITCODE -ne 0) { throw 'Java application failed; see diagnostics above.' }
} finally { [Environment]::SetEnvironmentVariable($nqbSecretName, $nqbOldSecret, 'Process'); Pop-Location }
