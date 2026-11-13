# Insert port\_asset_section.md into README.md just before "### 神社（胶片复活）",
# i.e. right after the altar subsection it belongs to.
$ErrorActionPreference = 'Stop'
$repo   = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$readme = Join-Path $repo 'README.md'
$data   = Join-Path $repo 'port\_asset_section.md'
$utf8   = New-Object System.Text.UTF8Encoding($false)

$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($readme, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

if (($lines -join "`n").Contains('BedrockBlockModels')) { throw 'already applied' }

$anchor = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '### ' + [char]0x795E + [char]0x793E + [char]0xFF08 + [char]0x80F6 + [char]0x7247 + [char]0x590D + [char]0x6D3B + [char]0xFF09) {
        $anchor = $i; break
    }
}
if ($anchor -lt 0) { throw 'shrine heading not found' }
if ($lines[$anchor - 1] -ne '') { throw 'expected blank line before shrine heading' }

$block = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($data, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }

# the data file ends with a '---' separator; README wants a blank line then the heading
$lines.InsertRange($anchor - 1, $block)
$lines.Insert($anchor - 1 + $block.Count, '')

$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement char' }
[System.IO.File]::WriteAllText($readme, $text, $utf8)

$back = [System.IO.File]::ReadAllText($readme, [System.Text.Encoding]::UTF8)
if ($back -ne $text) { throw 'read-back mismatch' }
if ($back.Contains([char]0xFFFD)) { throw 'replacement char after write' }

[System.IO.File]::WriteAllText((Join-Path $repo 'port\_readme_log.txt'),
    ("lines " + $before + " -> " + $lines.Count + "`nok"), $utf8)
Write-Host 'inserted'
