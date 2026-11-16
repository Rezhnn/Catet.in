[CmdletBinding()]
param(
    [ValidateRange(7, 40)]
    [int]$ShortShaLength = 7
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$distDir = Join-Path $repoRoot 'dist'
$apkDir = Join-Path $repoRoot 'app/build/outputs/apk/release'
$metadataPath = Join-Path $apkDir 'output-metadata.json'
$checksumsPath = Join-Path $distDir 'checksums.txt'
$isWindowsPlatform = $env:OS -eq 'Windows_NT'

function Invoke-Git {
    param([Parameter(Mandatory)][string[]]$Arguments)

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

function Assert-SigningEnvironment {
    foreach ($name in @(
        'CATETIN_KEYSTORE_PATH',
        'CATETIN_KEYSTORE_PASSWORD',
        'CATETIN_KEY_ALIAS',
        'CATETIN_KEY_PASSWORD'
    )) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "Environment variable $name wajib diisi untuk APK release."
        }
    }
    if (-not (Test-Path -LiteralPath $env:CATETIN_KEYSTORE_PATH -PathType Leaf)) {
        throw "Keystore release tidak ditemukan: $env:CATETIN_KEYSTORE_PATH"
    }
}

function Find-ApkSigner {
    $fromPath = Get-Command apksigner -ErrorAction SilentlyContinue
    if ($null -ne $fromPath) { return $fromPath.Source }

    $fromPath = Get-Command apksigner.bat -ErrorAction SilentlyContinue
    if ($null -ne $fromPath) { return $fromPath.Source }

    $androidHome = [Environment]::GetEnvironmentVariable('ANDROID_HOME')
    if ([string]::IsNullOrWhiteSpace($androidHome)) {
        $androidHome = [Environment]::GetEnvironmentVariable('ANDROID_SDK_ROOT')
    }
    if ([string]::IsNullOrWhiteSpace($androidHome)) {
        throw 'ANDROID_HOME atau ANDROID_SDK_ROOT wajib disetel untuk memverifikasi APK.'
    }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $androidHome 'build-tools') -Directory |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($null -eq $buildTools) { throw 'Android build-tools tidak ditemukan.' }
    $candidate = Join-Path $buildTools.FullName $(if ($isWindowsPlatform) { 'apksigner.bat' } else { 'apksigner' })
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "apksigner tidak ditemukan: $candidate"
    }
    return $candidate
}

if (-not (Test-Path -LiteralPath $repoRoot -PathType Container)) {
    throw "Direktori repositori tidak ditemukan: $repoRoot"
}

Assert-SigningEnvironment
Assert-CleanWorktree

$head = ((Invoke-Git -Arguments @('rev-parse', 'HEAD')) -join '').Trim()
if ($head -notmatch '^[0-9a-fA-F]{40}$') { throw "HEAD bukan SHA commit valid: $head" }
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
    ':app:assembleRelease'
)

if ($isWindowsPlatform) {
    & $gradleWrapper @gradleArguments
} else {
    & /bin/sh $gradleWrapper @gradleArguments
}
if ($LASTEXITCODE -ne 0) { throw "Build release gagal dengan exit code $LASTEXITCODE." }

Assert-CleanWorktree

if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
    throw "Metadata APK release tidak ditemukan: $metadataPath"
}
$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$elements = @($metadata.elements)
if ($elements.Count -ne 1) { throw "Metadata APK harus memuat satu elemen; ditemukan $($elements.Count)." }

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
    throw "APK release tidak ditemukan: $sourceApk"
}

$signer = Find-ApkSigner
$verifyOutput = & $signer verify --verbose --print-certs $sourceApk 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "APK release tidak lolos verifikasi apksigner:`n$($verifyOutput -join [Environment]::NewLine)"
}

New-Item -ItemType Directory -Path $distDir -Force | Out-Null
$artifactName = "catetin-$versionName-$shortSha.apk"
$artifactPath = Join-Path $distDir $artifactName
Copy-Item -LiteralPath $sourceApk -Destination $artifactPath -Force

$artifactHash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
$apkFiles = @(Get-ChildItem -LiteralPath $distDir -Filter '*.apk' -File | Sort-Object Name)
$checksumLines = foreach ($apk in $apkFiles) {
    $hash = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($apk.Name)"
}
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($checksumsPath, (($checksumLines -join "`n") + "`n"), $utf8NoBom)

Write-Host "APK release siap: $artifactPath"
Write-Host "SHA-256 APK: $artifactHash"
Write-Host "Daftar checksum: $checksumsPath"
Write-Host "Sertifikat signer:"
$verifyOutput | Where-Object { $_ -match 'Verified using|Signer #1 certificate DN|Signer #1 certificate SHA-256' } |
    ForEach-Object { Write-Host $_ }
