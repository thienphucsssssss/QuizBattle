param(
    [Parameter(Mandatory=$true)][string]$SurveyJson,
    [Parameter(Mandatory=$true)][string]$OutputJson,
    [Parameter(Mandatory=$true)][string[]]$HoldoutRunIds,
    [ValidateRange(1,100)][int]$K = 3
)
$ErrorActionPreference = 'Stop'
$nqbProjectRoot = Split-Path -Parent $PSScriptRoot
$nqbInput = (Resolve-Path -LiteralPath $SurveyJson).Path
$nqbOutput = [IO.Path]::GetFullPath($OutputJson)
if (Test-Path -LiteralPath $nqbOutput) { throw 'Output already exists. Choose a new report filename.' }
if ($HoldoutRunIds.Count -eq 0) { throw 'Choose entire held-out run IDs before evaluation.' }
Push-Location -LiteralPath $nqbProjectRoot
try {
    & mvn -q -pl nqb-shared -am -DskipTests compile
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    & mvn -q -f nqb-shared/pom.xml dependency:build-classpath '-Dmdep.outputFile=target/fingerprint-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Could not resolve Java dependencies.' }
    $nqbDependencies = (Get-Content -LiteralPath 'nqb-shared/target/fingerprint-classpath.txt' -Raw).Trim()
    $nqbClasspath = (Join-Path $nqbProjectRoot 'nqb-shared/target/classes') + [IO.Path]::PathSeparator + $nqbDependencies
    & java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $nqbClasspath vn.edu.hutech.nqb.shared.fingerprint.FingerprintCli $nqbInput $nqbOutput ($HoldoutRunIds -join ',') $K
    if ($LASTEXITCODE -ne 0) { throw 'Evaluation failed. Survey data was not modified.' }
} finally {
    Pop-Location
}
