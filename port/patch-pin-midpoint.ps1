# Replace EntityMaid#pinToBed (plus its javadoc) with the midpoint version.
# ASCII only; the Chinese javadoc is spliced in from a UTF-8 snippet file.

$ErrorActionPreference = 'Stop'

$repo    = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$file    = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\passive\EntityMaid.java'
$snippet = Join-Path $repo 'port\_pin_snippet.txt'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

$sig = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '    private void pinToBed(BlockPos headPos) {') { $sig = $i; break }
}
if ($sig -lt 0) { throw 'pinToBed not found' }

$start = -1
for ($i = $sig - 1; $i -ge [Math]::Max(0, $sig - 20); $i--) {
    if ($lines[$i].Trim() -eq '/**') { $start = $i; break }
}
if ($start -lt 0) { throw 'javadoc above pinToBed not found' }

$end = -1
$depth = 0
for ($i = $sig; $i -lt $lines.Count; $i++) {
    foreach ($ch in $lines[$i].ToCharArray()) {
        if ($ch -eq '{') { $depth++ } elseif ($ch -eq '}') { $depth-- }
    }
    if ($depth -eq 0) { $end = $i; break }
}
if ($end -lt 0) { throw 'end of pinToBed not found' }

$block = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($snippet, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }

$lines.RemoveRange($start, ($end - $start) + 1)
$lines.InsertRange($start, $block)

$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement character in output' }
[System.IO.File]::WriteAllText($file, $text, $utf8)

$after = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
if (-not $after.Contains('cx -= facing.getStepX() * 0.5D;')) { throw 'midpoint math missing' }
if ($after.Contains('BlockPos center = headPos;')) { throw 'old body still present' }
$open = ([regex]::Matches($after, '\{')).Count
$close = ([regex]::Matches($after, '\}')).Count
if ($open -ne $close) { throw "brace mismatch $open / $close" }

Write-Host ("lines: " + $before + " -> " + $lines.Count)
Write-Host 'ok'

