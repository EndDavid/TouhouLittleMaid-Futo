# 1) Delete the duplicated SLEEPING translate inside GeoReplacedEntityRenderer#render.
# 2) Re-add EntityMaid#getBedOrientation() (bed facing) so the single remaining
#    vanilla translate/rotation is used.
#
# ASCII only (PowerShell 5.1 reads BOM-less .ps1 as ANSI).

$ErrorActionPreference = 'Stop'

$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$utf8 = New-Object System.Text.UTF8Encoding($false)

# ---------------------------------------------------------------- part 1
$geo  = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\geckolib3\geo\GeoReplacedEntityRenderer.java'
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($geo, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }

$probe = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '        if (entity.getPose() == Pose.SLEEPING) {') { $probe = $i; break }
}
if ($probe -lt 0) { throw 'SLEEPING block not found in the gecko renderer' }
if (-not $lines[$probe + 1].Contains('getBedOrientation')) { throw 'unexpected line after the SLEEPING brace' }

# the block is exactly 7 lines: if / dir / if / eyeOffset / translate / } / }
$end = -1
$depth = 0
for ($i = $probe; $i -lt $probe + 10; $i++) {
    foreach ($ch in $lines[$i].ToCharArray()) {
        if ($ch -eq '{') { $depth++ } elseif ($ch -eq '}') { $depth-- }
    }
    if ($depth -eq 0) { $end = $i; break }
}
if ($end -lt 0 -or ($end - $probe) -ne 6) { throw ('unexpected block size: ' + ($end - $probe)) }

$replacement = @(
    '        // NOTE: the SLEEPING translate is intentionally NOT duplicated here.',
    '        // setupRotations() below reaches LivingEntityRenderer#setupRotations, which',
    '        // already applies  translate(-bedFacing * (getEyeHeight(STANDING) - 0.1)).',
    '        // geckolib shipped both copies; upstream never noticed because its maid bed',
    '        // reports no bed orientation, so neither copy ever ran. Now that the maid bed',
    '        // returns a real facing, keeping both would shift her by 2.35 blocks.',
    '        // See EntityMaid#getBedOrientation.'
)
$lines.RemoveRange($probe, ($end - $probe) + 1)
$lines.InsertRange($probe, [string[]]$replacement)

$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement character in the gecko renderer' }
[System.IO.File]::WriteAllText($geo, $text, $utf8)

$check = [System.IO.File]::ReadAllText($geo, [System.Text.Encoding]::UTF8)
if ($check.Contains('if (entity.getPose() == Pose.SLEEPING)')) { throw 'SLEEPING block still present' }
$open = ([regex]::Matches($check, '\{')).Count
$close = ([regex]::Matches($check, '\}')).Count
if ($open -ne $close) { throw "geo brace mismatch $open / $close" }
Write-Host 'part 1 ok (duplicate translate removed)'

# ---------------------------------------------------------------- part 2
$maid = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\passive\EntityMaid.java'
$snippet = Join-Path $repo 'port\_patch_bedoriented.txt'
$lines2 = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($maid, [System.Text.Encoding]::UTF8)) { $lines2.Add($l) }
$beforeCount = $lines2.Count

if (($lines2 | Select-String -SimpleMatch 'public Direction getBedOrientation()' -Quiet)) {
    Write-Host 'part 2 skipped (already present)'
} else {
    $sig = -1
    for ($i = 0; $i -lt $lines2.Count; $i++) {
        if ($lines2[$i] -eq '    public void stopSleeping() {') { $sig = $i; break }
    }
    if ($sig -lt 0) { throw 'stopSleeping() not found' }

    $end2 = -1
    $depth2 = 0
    for ($i = $sig; $i -lt $lines2.Count; $i++) {
        foreach ($ch in $lines2[$i].ToCharArray()) {
            if ($ch -eq '{') { $depth2++ } elseif ($ch -eq '}') { $depth2-- }
        }
        if ($depth2 -eq 0) { $end2 = $i; break }
    }
    if ($end2 -lt 0) { throw 'end of stopSleeping() not found' }

    $block = [System.Collections.Generic.List[string]]::new()
    foreach ($l in [System.IO.File]::ReadAllLines($snippet, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
    while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }
    $block.Add('')
    $lines2.InsertRange($end2 + 1, $block)

    $text2 = ($lines2 -join "`r`n") + "`r`n"
    if ($text2.Contains([char]0xFFFD)) { throw 'replacement character in EntityMaid' }
    [System.IO.File]::WriteAllText($maid, $text2, $utf8)

    $check2 = [System.IO.File]::ReadAllText($maid, [System.Text.Encoding]::UTF8)
    if (-not $check2.Contains('public Direction getBedOrientation()')) { throw 'getBedOrientation missing after write' }
    $open2 = ([regex]::Matches($check2, '\{')).Count
    $close2 = ([regex]::Matches($check2, '\}')).Count
    if ($open2 -ne $close2) { throw "maid brace mismatch $open2 / $close2" }
    Write-Host ('part 2 ok (lines ' + $beforeCount + ' -> ' + $lines2.Count + ')')
}

