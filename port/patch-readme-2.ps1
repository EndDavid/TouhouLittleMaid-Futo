# Append port\_sec2.md to README.md, right after the "没换的" table that ends the
# "1.20.1 的模型与纹理已回填" section (i.e. just before the "### 神社（胶片复活）" heading).
$ErrorActionPreference = 'Stop'
$repo   = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$readme = Join-Path $repo 'README.md'
$data   = Join-Path $repo 'port\_sec2.md'
$utf8   = New-Object System.Text.UTF8Encoding($false)

$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($readme, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

if (($lines -join "`n").Contains('floorOffset')) { throw 'already applied' }

# the shrine heading is unchanged CJK, build it from codepoints so the script stays ASCII
$shrineHead = '### ' + [char]0x795E + [char]0x793E + [char]0xFF08 + [char]0x80F6 + [char]0x7247 + [char]0x590D + [char]0x6D3B + [char]0xFF09
$anchor = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq $shrineHead) { $anchor = $i; break }
}
if ($anchor -lt 0) { throw 'shrine heading not found' }
if ($lines[$anchor - 1] -ne '') { throw 'expected blank line before shrine heading' }

$block = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($data, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }

$lines.InsertRange($anchor - 1, $block)
$lines.Insert($anchor - 1 + $block.Count, '')

$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement char' }
[System.IO.File]::WriteAllText($readme, $text, $utf8)

$back = [System.IO.File]::ReadAllText($readme, [System.Text.Encoding]::UTF8)
if ($back -ne $text) { throw 'read-back mismatch' }
if ($back.Contains([char]0xFFFD)) { throw 'replacement char after write' }

[System.IO.File]::WriteAllText((Join-Path $repo 'port\_sec2log.txt'),
    ("lines " + $before + " -> " + $lines.Count + "`nok"), $utf8)
Write-Host 'appended'

