# How are the altar's cubes distributed vertically near the bottom?
#
# This decides one question: is the model's y=0 the ground plane (a few cubes dip
# below it on purpose), or is the model's *lowest cube* the ground plane?
# Lifting by -minY assumes the latter; if the bulk sits at 0 and only a handful of
# cubes poke below, the former is true and lifting creates a gap.
$ErrorActionPreference = 'Stop'
$p   = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid\models\bedrock\block\altar.json'
$out = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_dist.txt'
$sb = New-Object System.Text.StringBuilder

$j = [System.IO.File]::ReadAllText($p) | ConvertFrom-Json

$rows = New-Object System.Collections.Generic.List[object]
foreach ($g in $j.'minecraft:geometry') {
    foreach ($b in $g.bones) {
        if (-not $b.cubes) { continue }
        foreach ($c in $b.cubes) {
            # a cube is only "at the bottom" if its bone is not rotated, otherwise the
            # raw origin says nothing about where it ends up. Record the bone rotation.
            $rot = '0,0,0'
            if ($b.rotation) { $rot = ($b.rotation -join ',') }
            $rows.Add([pscustomobject]@{
                bone   = $b.name
                parent = $b.parent
                rot    = $rot
                y      = [double]$c.origin[1]
                h      = [double]$c.size[1]
                top    = [double]$c.origin[1] + [double]$c.size[1]
                size   = ($c.size -join 'x')
            })
        }
    }
}

$minY = ($rows | Measure-Object -Property y -Minimum).Minimum
[void]$sb.AppendLine("cubes total = $($rows.Count)   raw min bottom = $minY")
[void]$sb.AppendLine('')
[void]$sb.AppendLine('=== cubes whose bottom is within 4 units of the minimum (unrotated bones only) ===')
$near = $rows | Where-Object { $_.rot -eq '0,0,0' -and $_.y -le ($minY + 4) } | Sort-Object y
foreach ($r in $near) {
    [void]$sb.AppendLine(("  bone={0,-6} parent={1,-6} bottom={2,8} h={3,6} size={4}" -f $r.bone, $r.parent, $r.y, $r.h, $r.size))
}
[void]$sb.AppendLine('')
[void]$sb.AppendLine('=== lowest bottom per bone, unrotated bones (sorted) ===')
$byBone = $rows | Where-Object { $_.rot -eq '0,0,0' } | Group-Object bone |
    ForEach-Object { [pscustomobject]@{ bone = $_.Name; bottom = ($_.Group | Measure-Object -Property y -Minimum).Minimum; n = $_.Count } } |
    Sort-Object bottom
foreach ($r in ($byBone | Select-Object -First 20)) {
    [void]$sb.AppendLine(("  bone={0,-8} cubes={1,-4} lowest={2}" -f $r.bone, $r.n, $r.bottom))
}
[void]$sb.AppendLine('')
$zero = ($rows | Where-Object { [Math]::Abs($_.y) -lt 0.001 }).Count
$below = ($rows | Where-Object { $_.y -lt -0.001 }).Count
[void]$sb.AppendLine("cubes with bottom exactly 0 : $zero")
[void]$sb.AppendLine("cubes with bottom below 0   : $below")
[void]$sb.AppendLine('')
[void]$sb.AppendLine('note: bones with a rotation are excluded from the per-bone table, because their')
[void]$sb.AppendLine('      raw origin does not describe where the cube ends up. Only ' +
    ($rows | Where-Object { $_.rot -ne '0,0,0' }).Count + ' of ' + $rows.Count + ' cubes are rotated.')

[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'done'

