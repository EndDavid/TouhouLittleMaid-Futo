# Update port/book_pages_held_back/README.md now that the favorability page is back:
#   1. "13 pages" heading            -> 12
#   2. drop the favorability_tool gap-table row
#   3. chair bullet loses "(this round)"; a favorability bullet is added
#   4. 39 entries                    -> 40
#   5. the stale "icon replaced by minecraft:cake" note -> restored note
#
# ASCII-only: PowerShell 5.1 parses a BOM-less .ps1 as ANSI, so any Chinese literal
# here would be mangled. Also note that char + char is *integer* addition in
# PowerShell, so the needles below are built from .ToString() calls only.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$doc = Join-Path $root 'port\book_pages_held_back\README.md'

function Read-Utf8([string]$path) {
    $t = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    if ($t.Length -gt 0 -and [int]$t[0] -eq 0xFEFF) { $t = $t.Substring(1) }
    return $t
}

function Load-Snippet([string]$name) {
    $t = Read-Utf8 (Join-Path $PSScriptRoot $name)
    $t = $t -replace "`r`n", "`n"
    return $t.TrimEnd([char[]]@(13, 10))
}

$raw = Read-Utf8 $doc
$crlf = ([regex]::Matches($raw, "`r`n")).Count
$lf = ([regex]::Matches($raw, "`n")).Count
if ($crlf -ne $lf) { throw "mixed EOL: crlf=$crlf lf=$lf" }
$src = New-Object System.Collections.Generic.List[string]
foreach ($l in ($raw -replace "`r`n", "`n").Split("`n")) { $src.Add($l) }
$linesBefore = $src.Count

# ---- 1. the count heading (the only line that ends with a full-width colon + "13") ----
$hits = @()
for ($i = 0; $i -lt $src.Count; $i++) {
    if ($src[$i].Contains('13') -and $src[$i].TrimEnd().EndsWith([char]0xFF1A)) { $hits += $i }
}
if ($hits.Count -ne 1) { throw "count heading matched $($hits.Count) lines" }
Write-Output ("count heading at line " + ($hits[0] + 1))
$src[$hits[0]] = Load-Snippet '_hb_count12.txt'

# ---- 2. drop the gap-table row ----
$hits = @()
for ($i = 0; $i -lt $src.Count; $i++) {
    if ($src[$i].Contains('other/favorability_tool`')) { $hits += $i }
}
if ($hits.Count -ne 1) { throw "gap-table row matched $($hits.Count) lines" }
Write-Output ("gap-table row at line " + ($hits[0] + 1))
$src.RemoveAt($hits[0])

# ---- 3. chair bullet -> chair + favorability bullets ----
$hits = @()
for ($i = 0; $i -lt $src.Count; $i++) {
    if ($src[$i].Contains('other/chair_show.json')) { $hits += $i }
}
if ($hits.Count -ne 1) { throw "chair bullet matched $($hits.Count) lines" }
Write-Output ("chair bullet at line " + ($hits[0] + 1))
$new = Load-Snippet '_hb_chair_lines2.txt'
$src.RemoveAt($hits[0])
$src.InsertRange($hits[0], [string[]]$new.Split("`n"))

# ---- 4. the entry counters ----
$text = [string]::Join("`n", $src.ToArray())
if (([regex]::Matches($text, 'entries=39 categories=3 problems=0')).Count -ne 1) { throw "entries=39 marker not unique" }
$text = $text.Replace('entries=39 categories=3 problems=0', 'entries=40 categories=3 problems=0')
$entryWord = ([string][char]0x8BCD) + ([string][char]0x6761)
if ($text.Contains('39 ' + $entryWord)) { $text = $text.Replace('39 ' + $entryWord, '40 ' + $entryWord) }
Write-Output 'entry counters updated'
$src.Clear()
foreach ($l in $text.Split("`n")) { $src.Add($l) }

# ---- 5. the stale icon note ----
# The entry list uses "`overview/`" and step 3's bullet no longer mentions the icon at
# all, so "overview/favorability`" matches exactly one line: the first line of the note.
$hits = @()
for ($i = 0; $i -lt $src.Count; $i++) {
    if ($src[$i].Contains('overview/favorability`')) { $hits += $i }
}
if ($hits.Count -ne 1) { throw "icon note matched $($hits.Count) lines" }
$start = $hits[0]
$end = $start
while ($end + 1 -lt $src.Count -and -not $src[$end + 1].StartsWith('## ')) { $end++ }
while ($end -gt $start -and $src[$end].Trim().Length -eq 0) { $end-- }
Write-Output ("icon note lines " + ($start + 1) + ".." + ($end + 1))
$new = Load-Snippet '_hb_icon_note.txt'
$src.RemoveRange($start, $end - $start + 1)
$src.InsertRange($start, [string[]]$new.Split("`n"))

# ---- finalise ----
$out = [string]::Join("`n", $src.ToArray())
if ($out.Contains([string][char]0xFFFD)) { throw "U+FFFD in result" }
foreach ($must in @('favorability_tool_add', 'entries=40', 'validate-book.ps1',
                    'AltarRecipeComponent', 'make_custom_model')) {
    if (-not $out.Contains($must)) { throw "marker lost: $must" }
}
if (([regex]::Matches($out, 'favorability_tool_add/full/reduce')).Count -ne 0) { throw "stale gap row still present" }

[System.IO.File]::WriteAllText($doc, ($out -replace "`n", "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("held-back README: " + $linesBefore + " -> " + $src.Count + " lines")
