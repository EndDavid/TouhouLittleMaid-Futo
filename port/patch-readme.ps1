# Insert the two verified README additions.
# ASCII only (PowerShell 5.1 reads BOM-less .ps1 as ANSI); Chinese lives in UTF-8 snippets.

$ErrorActionPreference = 'Stop'

$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$file = Join-Path $repo 'README.md'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

function Read-Snippet($name) {
    $p = Join-Path $repo ('port\' + $name)
    $out = [System.Collections.Generic.List[string]]::new()
    foreach ($l in [System.IO.File]::ReadAllLines($p, [System.Text.Encoding]::UTF8)) { $out.Add($l) }
    while ($out.Count -gt 0 -and $out[0] -eq '') { $out.RemoveAt(0) }
    while ($out.Count -gt 0 -and $out[$out.Count - 1] -eq '') { $out.RemoveAt($out.Count - 1) }
    # leading comma: return the List itself, otherwise PowerShell unrolls it into a fixed-size array
    return ,$out
}

# --- anchor 1: the "build passes != runnable" paragraph -------------------
$i = -1
for ($k = 0; $k -lt $lines.Count; $k++) {
    if ($lines[$k] -like '*latest.log*') { $i = $k; break }
}
if ($i -lt 0) { throw 'anchor 1 not found' }
$blkA = Read-Snippet '_blkA.txt'
$blkA.Add('')
$lines.InsertRange($i + 1, $blkA)

# --- anchor 2: the AccessWidener bullet ----------------------------------
$j = -1
for ($k = 0; $k -lt $lines.Count; $k++) {
    if ($lines[$k] -like '*StructureTemplate#palettes*Accessor*') { $j = $k; break }
}
if ($j -lt 0) { throw 'anchor 2 not found' }
$blkB = Read-Snippet '_blkB.txt'
$lines.InsertRange($j + 1, $blkB)

# --- write + verify ------------------------------------------------------
$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement character in output' }
if (-not $text.Contains('getDeclaredMethods')) { throw 'block B missing' }
if (-not $text.Contains('intermediary')) { throw 'block A missing' }
[System.IO.File]::WriteAllText($file, $text, $utf8)

$after = [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)
Write-Host ("lines: " + $before + " -> " + $after.Count)
Write-Host 'ok'
