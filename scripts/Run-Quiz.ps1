param(
    [ValidateSet('Server','Admin','Player')][string]$Role = 'Admin',
    [ValidateRange(1024,3840)][int]$Width = 1366,
    [ValidateRange(640,2160)][int]$Height = 768
)
$ErrorActionPreference = 'Stop'
$nqbProjectRoot = Split-Path -Parent $PSScriptRoot
$nqbLaunchers = @{
    Server = @('nqb-server', 'vn.edu.hutech.nqb.server.ServerMain')
    Admin = @('nqb-admin-client', 'vn.edu.hutech.nqb.admin.AdminMain')
    Player = @('nqb-player-client', 'vn.edu.hutech.nqb.player.PlayerMain')
}
$nqbSelected = $nqbLaunchers[$Role]
Push-Location -LiteralPath $nqbProjectRoot
try {
    if ($Role -eq 'Server') { & (Join-Path $PSScriptRoot 'Start-Server.ps1'); return }
    $nqbArguments = "--width=$Width --height=$Height"
    & mvn -o -B -ntp -f (Join-Path $nqbSelected[0] 'pom.xml') exec:java "-Dexec.mainClass=$($nqbSelected[1])" "-Dexec.args=$nqbArguments"
    if ($LASTEXITCODE -ne 0) { throw 'Java launcher failed. Run mvn install at the project root first.' }
} finally {
    Pop-Location
}
