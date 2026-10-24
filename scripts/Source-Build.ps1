param([switch]$Record)
$ErrorActionPreference='Stop'
$nqbRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$nqbStamp=Join-Path $nqbRoot 'tmp/run-build.ok'
$nqbModules=@('nqb-shared','nqb-server','nqb-client-core','nqb-admin-client','nqb-player-client')
$nqbHash=[Security.Cryptography.SHA256]::Create()
try {
    # Hash only build inputs and runtime outputs. Never read database, certificates or local settings.
    $nqbFiles=@(Get-Item -LiteralPath (Join-Path $nqbRoot 'pom.xml'))
    foreach($nqbModule in $nqbModules) {
        $nqbModuleRoot=Join-Path $nqbRoot $nqbModule
        $nqbFiles+=Get-Item -LiteralPath (Join-Path $nqbModuleRoot 'pom.xml')
        $nqbFiles+=Get-ChildItem -LiteralPath (Join-Path $nqbModuleRoot 'src') -Recurse -File
        $nqbJars=@(Get-ChildItem -LiteralPath (Join-Path $nqbModuleRoot 'target') -Filter "$nqbModule-*.jar" -File)
        $nqbClasses=@(Get-ChildItem -LiteralPath (Join-Path $nqbModuleRoot 'target/classes') -Recurse -File)
        if($nqbJars.Count -eq 0 -or $nqbClasses.Count -eq 0){throw "Missing build outputs: $nqbModule"}
        $nqbFiles+=$nqbJars
        $nqbFiles+=$nqbClasses
    }
    foreach($nqbRelative in @('.mvn','run.bat','scripts/Source-Build.ps1','scripts/Run-Quiz.ps1','scripts/Start-Server.ps1')) {
        $nqbPath=Join-Path $nqbRoot $nqbRelative
        if(Test-Path -LiteralPath $nqbPath -PathType Container){$nqbFiles+=Get-ChildItem -LiteralPath $nqbPath -Recurse -File}
        elseif(Test-Path -LiteralPath $nqbPath -PathType Leaf){$nqbFiles+=Get-Item -LiteralPath $nqbPath}
    }
    $nqbManifest=[Text.StringBuilder]::new()
    foreach($nqbFile in ($nqbFiles | Sort-Object -Property FullName -Unique)) {
        $nqbStream=[IO.File]::OpenRead($nqbFile.FullName)
        try{$nqbDigest=[BitConverter]::ToString($nqbHash.ComputeHash($nqbStream)).Replace('-','')}
        finally{$nqbStream.Dispose()}
        $nqbRelative=$nqbFile.FullName.Substring($nqbRoot.Length).Replace('\','/')
        [void]$nqbManifest.Append($nqbRelative).Append("`t").Append($nqbDigest).Append("`n")
    }
    $nqbFingerprint=[BitConverter]::ToString($nqbHash.ComputeHash([Text.Encoding]::UTF8.GetBytes($nqbManifest.ToString()))).Replace('-','')
    if($Record) {
        [void][IO.Directory]::CreateDirectory((Split-Path -Parent $nqbStamp))
        $nqbContent=@{version=1;fingerprint=$nqbFingerprint}|ConvertTo-Json -Compress
        [IO.File]::WriteAllText($nqbStamp,$nqbContent,[Text.UTF8Encoding]::new($false))
        exit 0
    }
    if(-not (Test-Path -LiteralPath $nqbStamp -PathType Leaf)){exit 1}
    $nqbSaved=Get-Content -LiteralPath $nqbStamp -Raw | ConvertFrom-Json
    if($nqbSaved.version -eq 1 -and $nqbSaved.fingerprint -eq $nqbFingerprint){exit 0}
    exit 1
} catch {
    if($Record){Write-Error "Cannot record source build: $($_.Exception.Message)"}
    exit 1
} finally {$nqbHash.Dispose()}
