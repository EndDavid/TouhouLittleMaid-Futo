# Show the texture references of the 1.20.1 block models we may adopt, and the
# particle-only stubs, plus our own render-layer registration.
$ErrorActionPreference = 'Stop'
$outPath = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_probe.txt'
$base = 'D:\codename_EndDavid\hello java\_ref\_1201assets'
$sb = New-Object System.Text.StringBuilder

function Refs([string]$p) {
    if (-not (Test-Path $p)) { return '(missing)' }
    $t = [System.IO.File]::ReadAllText($p)
    $ids = [regex]::Matches($t, '"([a-z0-9_]+:[^"]*)"') | ForEach-Object { $_.Groups[1].Value }
    return (($ids | Sort-Object -Unique) -join ', ')
}

[void]$sb.AppendLine('=== 1.20.1 models/block texture refs ===')
foreach ($n in @('altar','shrine','statue','maid_bed_head','maid_bed_foot','picnic_mat','bookshelf',
                 'maid_beacon_down','maid_beacon_up','scarecrow_lower','scarecrow_upper','model_switcher')) {
    [void]$sb.AppendLine(("{0,-20} {1}" -f $n, (Refs "$base\models\block\$n.json")))
}

[void]$sb.AppendLine('')
[void]$sb.AppendLine('=== 1.20.1 blockstates diff vs ours ===')
$mine = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid'
foreach ($n in @('altar','garage_kit','maid_beacon','maid_bed','model_switcher','scarecrow','statue')) {
    $a = Join-Path "$base\blockstates" "$n.json"
    $b = Join-Path "$mine\blockstates" "$n.json"
    $ta = if (Test-Path $a) { [System.IO.File]::ReadAllText($a) } else { '' }
    $tb = if (Test-Path $b) { [System.IO.File]::ReadAllText($b) } else { '' }
    [void]$sb.AppendLine(("{0,-16} newLen={1,-6} ourLen={2,-6} identical={3}" -f $n, $ta.Length, $tb.Length, ($ta -eq $tb)))
}

[void]$sb.AppendLine('')
[void]$sb.AppendLine('=== our RenderLayer registrations ===')
$src = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\java'
$hits = Select-String -Path (Join-Path $src '**\*.java') -Pattern 'ItemBlockRenderTypes|setRenderLayer|RenderType\.cutout' -ErrorAction SilentlyContinue
[void]$sb.AppendLine("count=" + ($hits | Measure-Object).Count)
foreach ($h in $hits) { [void]$sb.AppendLine('  ' + (Split-Path $h.Path -Leaf) + ':' + $h.LineNumber + '  ' + $h.Line.Trim()) }

[System.IO.File]::WriteAllText($outPath, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'done'

