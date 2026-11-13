# Extract the touhou_little_maid assets of both upstream jars side by side, so the
# JSON models can be diffed as text.
#
#   1.18.2 -> _ref\_1182assets\
#   1.20.1 -> _ref\_1201assets\

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = 'D:\codename_EndDavid\hello java'
$jobs = @(
    @{ Jar = (Join-Path $root '[forge+1.18.2]touhoulittlemaid-1.18.2-release-1.2.2.jar'); Dest = (Join-Path $root '_ref\_1182assets') },
    @{ Jar = (Join-Path $root '[forge+1.20.1]touhoulittlemaid-1.5.3-forge+mc1.20.1.jar');        Dest = (Join-Path $root '_ref\_1201assets') }
)

foreach ($j in $jobs) {
    if (Test-Path $j.Dest) { Remove-Item $j.Dest -Recurse -Force }
    New-Item -ItemType Directory -Path $j.Dest -Force | Out-Null
    $z = [System.IO.Compression.ZipFile]::OpenRead($j.Jar)
    $n = 0
    foreach ($e in $z.Entries) {
        if ($e.FullName -notlike 'assets/touhou_little_maid/*') { continue }
        if ($e.FullName.EndsWith('/')) { continue }
        $rel = $e.FullName.Substring('assets/touhou_little_maid/'.Length)
        $target = Join-Path $j.Dest $rel
        $dir = Split-Path $target -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $target, $true)
        $n++
    }
    $z.Dispose()
    Write-Host ("$($j.Dest)  <- $n entries")
}

