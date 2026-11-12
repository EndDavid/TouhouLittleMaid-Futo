# Insert the SLEEPING centring translate into GeoReplacedEntityRenderer#render,
# immediately before the "float lerpedAge = ..." line (unique in the file).
#
# ASCII only: PowerShell 5.1 parses a BOM-less .ps1 as ANSI, so the Chinese
# comment is kept in port\_sleep_block.txt (UTF-8) and spliced in verbatim.

$ErrorActionPreference = 'Stop'

$repo    = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$file    = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\geckolib3\geo\GeoReplacedEntityRenderer.java'
$snippet = Join-Path $repo 'port\_sleep_block.txt'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

$text0 = ($lines -join "`n")
if ($text0.Contains('Pose.SLEEPING')) { throw 'a SLEEPING block is already present' }

$anchor = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '        float lerpedAge = entity.tickCount + partialTick;') { $anchor = $i; break }
}
if ($anchor -lt 0) { throw 'lerpedAge anchor not found' }

# collapse extra blank lines just above the anchor (the previous edit left two)
$cut = $anchor
while ($cut -gt 0 -and $lines[$cut - 1].Trim() -eq '') { $cut-- }
if ($cut -lt $anchor) { $lines.RemoveRange($cut, $anchor - $cut); $anchor = $cut }

$block = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($snippet, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }
$block.Add('')

$lines.InsertRange($anchor, $block)

$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement character in output' }
[System.IO.File]::WriteAllText($file, $text, $utf8)

$check = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
if (-not $check.Contains('if (entity.getPose() == Pose.SLEEPING) {')) { throw 'SLEEPING block missing after write' }
if (-not $check.Contains('getDimensions(Pose.STANDING).height')) { throw 'standing-height lookup missing' }
if ($check.Contains('getBbHeight() * 0.5F')) { throw 'getBbHeight still used for the half body' }
$open = ([regex]::Matches($check, '\{')).Count
$close = ([regex]::Matches($check, '\}')).Count
if ($open -ne $close) { throw "brace mismatch $open / $close" }

Write-Host ("lines: " + $before + " -> " + $lines.Count)
Write-Host 'ok'

