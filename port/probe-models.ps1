# For every 1.20.1 models/block/*.json, say whether it is a real JSON model or a
# particle-only stub (i.e. the visible geometry moved to a GeckoLib bedrock model).
$ErrorActionPreference = 'Stop'
$outPath = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_probe.txt'
$base = 'D:\codename_EndDavid\hello java\_ref\_1201assets'
$sb = New-Object System.Text.StringBuilder

[void]$sb.AppendLine('=== 1.20.1 models/block ===')
foreach ($f in Get-ChildItem "$base\models\block" -File -Filter *.json | Sort-Object Name) {
    $t = [System.IO.File]::ReadAllText($f.FullName)
    $kind = 'ELEMENTS'
    if (-not $t.Contains('"elements"')) { $kind = 'STUB' }
    $par = ''
    $m = [regex]::Match($t, '"parent"\s*:\s*"([^"]+)"')
    if ($m.Success) { $par = $m.Groups[1].Value }
    $sz = ''
    $m2 = [regex]::Match($t, '"texture_size"\s*:\s*\[([^\]]*)\]')
    if ($m2.Success) { $sz = $m2.Groups[1].Value -replace '\s', '' }
    $rt = ''
    $m3 = [regex]::Match($t, '"render_type"\s*:\s*"([^"]+)"')
    if ($m3.Success) { $rt = $m3.Groups[1].Value }
    [void]$sb.AppendLine(("{0,-28} {1,-9} ts={2,-9} rt={3,-9} parent={4}" -f $f.Name, $kind, $sz, $rt, $par))
}

[void]$sb.AppendLine('')
[void]$sb.AppendLine('=== our project models/block ===')
$mine = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid'
foreach ($f in Get-ChildItem "$mine\models\block" -File -Filter *.json | Sort-Object Name) {
    $t = [System.IO.File]::ReadAllText($f.FullName)
    $kind = 'ELEMENTS'
    if (-not $t.Contains('"elements"')) { $kind = 'STUB' }
    $sz = ''
    $m2 = [regex]::Match($t, '"texture_size"\s*:\s*\[\s*(\d+)\s*,\s*(\d+)')
    if ($m2.Success) { $sz = "$($m2.Groups[1].Value)x$($m2.Groups[2].Value)" }
    [void]$sb.AppendLine(("{0,-28} {1,-9} ts={2}" -f $f.Name, $kind, $sz))
}

[void]$sb.AppendLine('')
[void]$sb.AppendLine('=== 1.20.1 blockstates (ours vs upstream names) ===')
foreach ($f in Get-ChildItem "$base\blockstates" -File -Filter *.json | Sort-Object Name) {
    $mineHas = Test-Path "$mine\blockstates\$($f.Name)"
    [void]$sb.AppendLine(("{0,-24} ours={1}" -f $f.Name, $mineHas))
}

[System.IO.File]::WriteAllText($outPath, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'done'

