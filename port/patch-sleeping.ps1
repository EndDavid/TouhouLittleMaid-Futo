# Remove the invented EntityMaid#getBedOrientation() override and add the
# missing getStandingEyeHeight() (both are upstream-parity fixes for the
# sleeping placement).
#
# ASCII only: PowerShell 5.1 parses a BOM-less .ps1 as ANSI.

$ErrorActionPreference = 'Stop'

$repo    = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$file    = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\passive\EntityMaid.java'
$snippet = Join-Path $repo 'port\_patch_eyeheight.txt'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

# --- 1) drop the getBedOrientation override -------------------------------
$sig = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '    public Direction getBedOrientation() {') { $sig = $i; break }
}
if ($sig -lt 0) { throw 'getBedOrientation() not found' }

$start = $sig
for ($i = $sig - 1; $i -ge [Math]::Max(0, $sig - 20); $i--) {
    if ($lines[$i].Trim() -eq '/**') { $start = $i; break }
}
if ($start -eq $sig) { throw 'javadoc above getBedOrientation() not found' }

$end = -1
$depth = 0
for ($i = $sig; $i -lt $lines.Count; $i++) {
    foreach ($ch in $lines[$i].ToCharArray()) {
        if ($ch -eq '{') { $depth++ } elseif ($ch -eq '}') { $depth-- }
    }
    if ($depth -eq 0) { $end = $i; break }
}
if ($end -lt 0) { throw 'end of getBedOrientation() not found' }

# swallow one trailing blank line so no double separator is left behind
$removeTo = $end
if ($removeTo + 1 -lt $lines.Count -and $lines[$removeTo + 1].Trim() -eq '') { $removeTo++ }
$lines.RemoveRange($start, ($removeTo - $start) + 1)

# --- 2) add getStandingEyeHeight right after isBaby() ---------------------
$anchor = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '    public boolean isBaby() {') { $anchor = $i; break }
}
if ($anchor -lt 0) { throw 'isBaby() not found' }
$anchorEnd = -1
for ($i = $anchor; $i -lt $anchor + 6; $i++) {
    if ($lines[$i] -eq '    }') { $anchorEnd = $i; break }
}
if ($anchorEnd -lt 0) { throw 'end of isBaby() not found' }

$block = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($snippet, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }
$lines.InsertRange($anchorEnd + 1, $block)

# --- 3) write back --------------------------------------------------------
$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement character in output' }
[System.IO.File]::WriteAllText($file, $text, $utf8)

# --- 4) verify ------------------------------------------------------------
$after = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
if ($after.Contains('Direction getBedOrientation()')) { throw 'getBedOrientation still present' }
if (-not $after.Contains('getStandingEyeHeight')) { throw 'getStandingEyeHeight missing after write' }
$open = ([regex]::Matches($after, '\{')).Count
$close = ([regex]::Matches($after, '\}')).Count
if ($open -ne $close) { throw "brace mismatch $open / $close" }

Write-Host ("lines: " + $before + " -> " + $lines.Count)
Write-Host 'ok'

