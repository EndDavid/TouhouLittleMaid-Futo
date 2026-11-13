# Probe: PNG dimensions of the old vs new block/item textures.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = 'D:\codename_EndDavid\hello java\_ref'
$outPath = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_probe.txt'
$sb = New-Object System.Text.StringBuilder

function Dims([string]$p) {
    if (-not (Test-Path $p)) { return '(missing)' }
    $i = [System.Drawing.Image]::FromFile($p)
    $s = "$($i.Width)x$($i.Height)"
    $i.Dispose()
    return $s
}

$names = @(
    'textures/block/maid_beacon.png',
    'textures/block/scarecrow.png',
    'textures/block/scarecrow_lower.png',
    'textures/block/scarecrow_upper.png',
    'textures/block/maid_bed.png',
    'textures/item/maid_beacon.png',
    'textures/item/scarecrow.png',
    'textures/item/shrine.png',
    'textures/item/film.png',
    'textures/item/reborn_maid.png',
    'textures/item/spawn_box.png',
    'textures/item/camera.png',
    'textures/item/camera_2d.png'
)

foreach ($n in $names) {
    $a = Dims (Join-Path $root "_1182assets\$n")
    $b = Dims (Join-Path $root "_1201assets\$n")
    [void]$sb.AppendLine(("{0,-44} old={1,-10} new={2}" -f $n, $a, $b))
}

[void]$sb.AppendLine('')
[void]$sb.AppendLine('--- our own models that already use texture_size:')
$mine = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid'
$g = Get-ChildItem $mine -Recurse -Filter '*.json' | Where-Object { $_.FullName -like '*models*' }
$hit = 0
foreach ($f in $g) {
    $t = [System.IO.File]::ReadAllText($f.FullName)
    if ($t.Contains('"texture_size"')) {
        $hit++
        if ($hit -le 15) { [void]$sb.AppendLine('  ' + $f.FullName.Substring($mine.Length + 1)) }
    }
}
[void]$sb.AppendLine("  total=$hit of $($g.Count)")

[System.IO.File]::WriteAllText($outPath, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'done'

