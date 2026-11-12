# Replace EntityMaid#onMaidSleep (javadoc + body) with the version that pins the
# maid to the bed surface and to the centre of the two-block maid bed.
#
# ASCII only (PowerShell 5.1 reads BOM-less .ps1 as ANSI).

$ErrorActionPreference = 'Stop'

$repo    = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$file    = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\passive\EntityMaid.java'
$snippet = Join-Path $repo 'port\_patch_pinToBed.txt'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

if (($lines | Select-String -SimpleMatch 'private void pinToBed(' -Quiet)) {
    throw 'pinToBed already present'
}

# locate the method
$sig = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '    private void onMaidSleep() {') { $sig = $i; break }
}
if ($sig -lt 0) { throw 'onMaidSleep() not found' }

# locate the javadoc that belongs to it
$start = -1
for ($i = $sig - 1; $i -ge [Math]::Max(0, $sig - 20); $i--) {
    if ($lines[$i].Trim() -eq '/**') { $start = $i; break }
}
if ($start -lt 0) { throw 'javadoc above onMaidSleep() not found' }

# end of the method
$end = -1
$depth = 0
for ($i = $sig; $i -lt $lines.Count; $i++) {
    foreach ($ch in $lines[$i].ToCharArray()) {
        if ($ch -eq '{') { $depth++ } elseif ($ch -eq '}') { $depth-- }
    }
    if ($depth -eq 0) { $end = $i; break }
}
if ($end -lt 0) { throw 'end of onMaidSleep() not found' }

$block = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($snippet, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }

$lines.RemoveRange($start, ($end - $start) + 1)
$lines.InsertRange($start, $block)

$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement character in output' }
[System.IO.File]::WriteAllText($file, $text, $utf8)

$after = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
if (-not $after.Contains('private void pinToBed(BlockPos headPos)')) { throw 'pinToBed missing' }
if ($after.Contains('pos.getY() + 0.8')) { throw 'the 0.8 height is still there' }
$open = ([regex]::Matches($after, '\{')).Count
$close = ([regex]::Matches($after, '\}')).Count
if ($open -ne $close) { throw "brace mismatch $open / $close" }

Write-Host ("lines: " + $before + " -> " + $lines.Count)
Write-Host 'ok'

