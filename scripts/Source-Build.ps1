param([switch]$Record)
$ErrorActionPreference='Stop'
$nqbRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$nqbStamp=Join-Path $nqbRoot 'tmp/run-build.ok'
$nqbModules=@('nqb-shared','nqb-server','nqb-client-core','nqb-admin-client','nqb-player-client')
try {
    if(-not $Record) {
        if(-not (Test-Path -LiteralPath $nqbStamp -PathType Leaf)){exit 1}
        $nqbSaved=Get-Content -LiteralPath $nqbStamp -Raw | ConvertFrom-Json
        # Version 1 was also written only after a successful build and plugin preparation.
        if($nqbSaved.version -notin @(1,2)){exit 1}
    }
    # Reuse the prepared runtime even after source edits. Updating source is an explicit build.
    # Missing outputs (for example after mvn clean) still require preparation again.
    foreach($nqbModule in $nqbModules) {
        $nqbModuleRoot=Join-Path $nqbRoot $nqbModule
        $nqbJars=@(Get-ChildItem -LiteralPath (Join-Path $nqbModuleRoot 'target') -Filter "$nqbModule-*.jar" -File)
        $nqbClasses=@(Get-ChildItem -LiteralPath (Join-Path $nqbModuleRoot 'target/classes') -Filter '*.class' -Recurse -File)
        if($nqbJars.Count -eq 0 -or $nqbClasses.Count -eq 0){throw "Missing build outputs: $nqbModule"}
    }
    if($Record) {
        [void][IO.Directory]::CreateDirectory((Split-Path -Parent $nqbStamp))
        $nqbContent=@{version=2}|ConvertTo-Json -Compress
        [IO.File]::WriteAllText($nqbStamp,$nqbContent,[Text.UTF8Encoding]::new($false))
    }
    exit 0
} catch {
    if($Record){Write-Error "Cannot record completed build: $($_.Exception.Message)"}
    exit 1
}
