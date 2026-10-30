param(
    [string]$Version = '0.6.4',
    [Parameter(Mandatory=$true)][string]$ClientTruststore,
    [switch]$SkipTests,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Version must have three numeric components.' }
$nqbRoot = Split-Path -Parent $PSScriptRoot
$nqbOutput = Join-Path $nqbRoot 'dist/QuizBattle/Client'
$nqbArchive = Join-Path $nqbOutput "QuizBattle-Portable-$Version.zip"
$nqbStage = Join-Path $nqbRoot ('build/portable-' + $Version + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if ((Test-Path -LiteralPath $nqbArchive) -or (Test-Path -LiteralPath $nqbStage)) { throw 'Build output already exists; no files overwritten.' }
$nqbPublicStore = (Resolve-Path -LiteralPath $ClientTruststore).Path
$nqbTrustInfo = @(& keytool '-J-Duser.language=en' '-J-Duser.country=US' -list -keystore $nqbPublicStore -storetype PKCS12 -storepass changeit 2>&1)
if ($LASTEXITCODE -ne 0 -or ($nqbTrustInfo -join ' ') -match 'PrivateKeyEntry|SecretKeyEntry' -or ($nqbTrustInfo -join ' ') -notmatch 'trustedCertEntry') {
    throw 'ClientTruststore must contain only public trusted certificates and use password changeit.'
}
function Assert-NqbPortableExit([string]$Step) { if ($LASTEXITCODE -ne 0) { throw "$Step failed ($LASTEXITCODE)" } }
Push-Location -LiteralPath $nqbRoot
try {
    $nqbMaven = @('-q'); if ($Offline) { $nqbMaven += '-o' }
    $nqbBuildArgs = @('install'); if ($SkipTests) { $nqbBuildArgs += '-DskipTests' }
    & mvn @nqbMaven @nqbBuildArgs
    Assert-NqbPortableExit 'Build'
    $nqbInput = Join-Path $nqbStage 'input'
    New-Item -ItemType Directory -Path $nqbInput,$nqbOutput -Force | Out-Null
    & mvn @nqbMaven -f nqb-admin-client/pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.7.0:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$nqbInput"
    Assert-NqbPortableExit 'Dependencies'
    $nqbJar = Get-ChildItem -LiteralPath 'nqb-admin-client/target' -Filter 'nqb-admin-client-*.jar' | Where-Object Name -NotMatch '(sources|javadoc|tests)' | Select-Object -Last 1
    if (-not $nqbJar) { throw 'Client JAR missing.' }
    Copy-Item -LiteralPath $nqbJar.FullName -Destination $nqbInput
    New-Item -ItemType Directory -Path (Join-Path $nqbInput 'certs') | Out-Null
    Copy-Item -LiteralPath $nqbPublicStore -Destination (Join-Path $nqbInput 'certs/client-truststore.p12')
    [IO.File]::WriteAllLines((Join-Path $nqbInput 'client.properties'),[string[]]@(
        '# Public Client profile. No private keys or saved accounts.',
        'server.host=auto','server.port=5050','discovery.port=5051',
        'tls.truststore=certs/client-truststore.p12','tls.truststorePassword=changeit'
    ),[Text.UTF8Encoding]::new($false))
    $nqbRuntime = Join-Path $nqbStage 'runtime'
    & jlink --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.zipfs --strip-debug --no-header-files --no-man-pages --output $nqbRuntime
    Assert-NqbPortableExit 'Runtime'
    $nqbImageRoot = Join-Path $nqbStage 'image'
    & jpackage --type app-image --name QuizBattle --app-version $Version --vendor 'Nhom VUA - HUTECH' --input $nqbInput --main-jar $nqbJar.Name --main-class vn.edu.hutech.nqb.admin.AdminMain --runtime-image $nqbRuntime --dest $nqbImageRoot --java-options '-Dnqb.app.dir=$APPDIR' --java-options '-Dfile.encoding=UTF-8' --java-options '-Dstdout.encoding=UTF-8' --java-options '-Dstderr.encoding=UTF-8'
    Assert-NqbPortableExit 'Portable application'
    $nqbImage = Join-Path $nqbImageRoot 'QuizBattle'
    Copy-Item -LiteralPath 'THIRD_PARTY_NOTICES.md','packaging/README-Portable.txt' -Destination $nqbImage
    $nqbUnexpected = Get-ChildItem -LiteralPath $nqbImage -Recurse -File | Where-Object {
        $_.Name -match '\.(db|key|jks|p12)$' -and $_.FullName -ne (Join-Path $nqbImage 'app/certs/client-truststore.p12')
    }
    if ($nqbUnexpected) { throw 'Unexpected database or credential in portable app.' }
    Compress-Archive -LiteralPath $nqbImage -DestinationPath $nqbArchive
    $nqbHash = (Get-FileHash -LiteralPath $nqbArchive -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText(($nqbArchive + '.sha256.txt'),$nqbHash+'  '+[IO.Path]::GetFileName($nqbArchive)+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
    Write-Host "PORTABLE_CLIENT_READY $nqbArchive"
    Write-Host "PORTABLE_TEST_IMAGE $nqbImage/QuizBattle.exe"
} finally { Pop-Location }
