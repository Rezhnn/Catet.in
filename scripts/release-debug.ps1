[CmdletBinding()]
param(
    [ValidateRange(7, 40)]
    [int]$ShortShaLength = 7
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$distDir = Join-Path $repoRoot 'dist'
$apkDir = Join-Path $repoRoot 'app/build/outputs/apk/debug'
$metadataPath = Join-Path $apkDir 'output-metadata.json'
$checksumsPath = Join-Path $distDir 'checksums.txt'
$isWindowsPlatform = $env:OS -eq 'Windows_NT'

function Invoke-Git {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $output = & git -C $repoRoot @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $detail = $output -join [Environment]::NewLine
        throw "Perintah git gagal (exit $exitCode): git $($Arguments -join ' ')`n$detail"
    }

    return @($output)
}

function Assert-CleanWorktree {
    $statusLines = Invoke-Git -Arguments @('status', '--porcelain=v1', '--untracked-files=normal')
    $details = ($statusLines -join [Environment]::NewLine).Trim()
    if ($details.Length -gt 0) {
        throw "Working tree tidak bersih. Commit atau hapus perubahan berikut sebelum membuat paket:`n$details"
    }
}

if (-not (Test-Path -LiteralPath $repoRoot -PathType Container)) {
    throw "Direktori repositori tidak ditemukan: $repoRoot"
}

Assert-CleanWorktree

$head = ((Invoke-Git -Arguments @('rev-parse', 'HEAD')) -join '').Trim()
if ($head -notmatch '^[0-9a-fA-F]{40}$') {
    throw "HEAD bukan SHA-1 commit yang valid: $head"
}
$shortSha = $head.ToLowerInvariant().Substring(0, $ShortShaLength)

$gradleWrapper = Join-Path $repoRoot $(if ($isWindowsPlatform) { 'gradlew.bat' } else { 'gradlew' })
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper tidak ditemukan: $gradleWrapper"
}

$gradleArguments = @(
    '--no-daemon',
    '--console=plain',
    '--stacktrace',
    ':parser-core:test',
    ':app:testDebugUnitTest',
    ':app:assembleDebug'
)

if ($isWindowsPlatform) {
    & $gradleWrapper @gradleArguments
}
else {
    & /bin/sh $gradleWrapper @gradleArguments
}
if ($LASTEXITCODE -ne 0) {
    throw "Build debug gagal dengan exit code $LASTEXITCODE."
}

Assert-CleanWorktree

if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
    throw "Metadata APK tidak ditemukan: $metadataPath"
}

try {
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
}
catch {
    throw "Metadata APK tidak dapat dibaca: $($_.Exception.Message)"
}

$elements = @($metadata.elements)
if ($elements.Count -ne 1) {
    throw "Metadata APK harus memuat tepat satu elemen; ditemukan $($elements.Count)."
}

$outputFile = [string]$elements[0].outputFile
$versionName = [string]$elements[0].versionName
if ([string]::IsNullOrWhiteSpace($outputFile) -or [IO.Path]::GetFileName($outputFile) -ne $outputFile) {
    throw "Nama berkas APK pada metadata tidak aman: '$outputFile'"
}
if ($versionName -notmatch '^[0-9A-Za-z][0-9A-Za-z0-9._+-]*$') {
    throw "versionName tidak aman untuk nama berkas: '$versionName'"
}

$sourceApk = Join-Path $apkDir $outputFile
if (-not (Test-Path -LiteralPath $sourceApk -PathType Leaf)) {
    throw "APK debug tidak ditemukan: $sourceApk"
}

New-Item -ItemType Directory -Path $distDir -Force | Out-Null
$artifactName = "catetin-$versionName-$shortSha.apk"
$artifactPath = Join-Path $distDir $artifactName
$tempArtifact = Join-Path $distDir ".$artifactName.$PID.tmp"

try {
    Copy-Item -LiteralPath $sourceApk -Destination $tempArtifact -Force
    Move-Item -LiteralPath $tempArtifact -Destination $artifactPath -Force
}
finally {
    if (Test-Path -LiteralPath $tempArtifact) {
        Remove-Item -LiteralPath $tempArtifact -Force
    }
}

$artifactHash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
$apkFiles = @(Get-ChildItem -LiteralPath $distDir -Filter '*.apk' -File | Sort-Object Name)
if ($apkFiles.Count -eq 0) {
    throw "Tidak ada APK di direktori dist."
}

$checksumLines = foreach ($apk in $apkFiles) {
    $hash = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($apk.Name)"
}

$tempChecksums = Join-Path $distDir ".checksums.$PID.tmp"
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
try {
    $content = (($checksumLines -join "`n") + "`n")
    [IO.File]::WriteAllText($tempChecksums, $content, $utf8NoBom)
    Move-Item -LiteralPath $tempChecksums -Destination $checksumsPath -Force
}
finally {
    if (Test-Path -LiteralPath $tempChecksums) {
        Remove-Item -LiteralPath $tempChecksums -Force
    }
}

Write-Host "APK debug siap: $artifactPath"
Write-Host "SHA-256 APK: $artifactHash"
Write-Host "Daftar checksum: $checksumsPath"

