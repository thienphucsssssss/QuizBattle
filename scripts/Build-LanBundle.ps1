param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$nqbRoot = Split-Path -Parent $PSScriptRoot
$nqbOutput = Join-Path $nqbRoot ('dist/NetworkQuizBattle-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $nqbOutput) { throw 'Bundle path already exists; no files overwritten.' }
Push-Location -LiteralPath $nqbRoot
try {
    $nqbMaven = @('-B','-ntp'); if ($Offline) { $nqbMaven += '-o' }
    & mvn @nqbMaven install -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    foreach ($nqbRole in @('Client','Server')) {
        $nqbDirectory = Join-Path $nqbOutput $nqbRole
        $nqbLib = Join-Path $nqbDirectory 'lib'
        New-Item -ItemType Directory -Path $nqbLib -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $nqbDirectory 'certs') -Force | Out-Null
        $nqbModule = if ($nqbRole -eq 'Client') { 'nqb-player-client' } else { 'nqb-server' }
        & mvn @nqbMaven -f "$nqbModule/pom.xml" org.apache.maven.plugins:maven-dependency-plugin:3.7.0:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$nqbLib"
        if ($LASTEXITCODE -ne 0) { throw 'Dependency copy failed; no ready bundle was produced.' }
        $nqbJar = Get-ChildItem -LiteralPath "$nqbModule/target" -Filter "$nqbModule-*.jar" | Where-Object { $_.Name -notmatch '(sources|javadoc|tests)' } | Select-Object -Last 1
        if (-not $nqbJar) { throw 'Module JAR missing' }
        Copy-Item -LiteralPath $nqbJar.FullName -Destination $nqbLib
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Launch-Lan.ps1') -Destination $nqbDirectory
        Copy-Item -LiteralPath (Join-Path $nqbRoot 'docs/Chay_LAN_va_kiem_thu.md') -Destination $nqbDirectory
        if ($nqbRole -eq 'Client') {
            $nqbTrust = Join-Path $nqbRoot 'certs/client-truststore.p12'
            if (Test-Path -LiteralPath $nqbTrust) { Copy-Item -LiteralPath $nqbTrust -Destination (Join-Path $nqbDirectory 'certs') }
        } else {
            New-Item -ItemType Directory -Path (Join-Path $nqbDirectory 'config') -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $nqbRoot 'config/server.properties') -Destination (Join-Path $nqbDirectory 'config')
            New-Item -ItemType Directory -Path (Join-Path $nqbDirectory 'scripts') -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Setup-Lan.ps1') -Destination (Join-Path $nqbDirectory 'scripts')
        }
    }
    Write-Host "NQB_BUNDLE_READY $nqbOutput"
    Write-Host 'Windows x64 + JDK 21 required. Client needs public truststore. Server key/database/passwords were NOT copied.'
} finally { Pop-Location }
