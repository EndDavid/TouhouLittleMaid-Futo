# Patch port/book_pages_held_back/README.md:
#   1. "14 pages held back"  -> 13 (there are 13 page files)
#   2. the trailing "AltarRecipeComponent is still a placeholder" paragraph
# ASCII-only: PowerShell 5.1 parses BOM-less .ps1 as ANSI, so the Chinese text lives
# in the UTF-8 snippet files.
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
    while ($t.EndsWith("`n")) { $t = $t.Substring(0, $t.Length - 1) }
    return ($t -split "`n")
}

$raw = Read-Utf8 $doc
$crlf = ([regex]::Matches($raw, "`r`n")).Count
$lf = ([regex]::Matches($raw, "`n")).Count
if ($crlf -ne $lf) { throw "mixed EOL: crlf=$crlf lf=$lf" }

$lines = New-Object System.Collections.Generic.List[string]
foreach ($l in ($raw -split "`r`n")) { $lines.Add($l) }
$before = $lines.Count

# ---- 1. the "14 pages" heading line -------------------------------------
$hits = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    # the heading, not the "ItemStackUtil.java:114" stack-trace line above it
    if ($lines[$i] -match '14' -and $lines[$i] -match 'java' -eq $false -and $lines[$i].Trim().EndsWith([char]0x3A)) { $hits += $i }
}
if ($hits.Count -ne 1) {
    # fall back to any line with 14 that is not the stack trace
    $hits = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '14' -and $lines[$i] -notmatch 'java:') { $hits += $i }
    }
}
if ($hits.Count -ne 1) { throw "count anchor matched $($hits.Count) lines" }
# NOTE: do not index the result of Load-Snippet here. PowerShell unrolls a
# one-element array returned from a function into a bare string, so $new[0]
# would be the first *character*.
$countLine = (Read-Utf8 (Join-Path $PSScriptRoot '_hb_count.txt')).TrimEnd()
if ($countLine.IndexOf("`n") -ge 0) { throw "count snippet must be a single line" }
Write-Output ("count line at " + ($hits[0] + 1))
$lines[$hits[0]] = $countLine

# ---- 2. the trailing altar paragraph ------------------------------------
# it starts at the line beginning with the altar wording and runs to the end of file
$start = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'altar_recipe' -and $lines[$i] -match '43') { $start = $i; break }
}
if ($start -lt 0) {
    # the paragraph may be split so the first line has no numbers; find it by a stable token
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].IndexOf('AltarRecipeComponent') -ge 0) { $start = $i; break }
    }
}
if ($start -lt 0) { throw "altar paragraph not found" }
Write-Output ("altar paragraph starts at line " + ($start + 1) + ", file had " + $lines.Count + " lines")

$replacement = Load-Snippet '_v3_hb_altar.txt'
Write-Output ("replacing " + ($lines.Count - $start) + " line(s) with " + $replacement.Count)
$lines.RemoveRange($start, $lines.Count - $start)
$lines.InsertRange($start, [string[]]$replacement)

$out = [string]::Join("`r`n", $lines.ToArray()) + "`r`n"
if ($out -isnot [string]) { throw "output is not a string: " + $out.GetType().FullName }
Write-Output ("DEBUG out.Length=" + $out.Length + " lines=" + $lines.Count)
Write-Output ("DEBUG contains13=" + $out.Contains([string]'13'))
Write-Output ("DEBUG containsAltar=" + $out.Contains([string]'altar_recipe'))
Write-Output ("DEBUG line35=[" + $lines[35] + "]")

$bad = 0
foreach ($ch in $out.ToCharArray()) { if ([int]$ch -eq 0xFFFD) { $bad++ } }
if ($bad -ne 0) { throw "replacement chars: $bad" }
foreach ($must in @('13', 'altar_recipe', 'validate-book.ps1', 'RecipeMatcher', 'add-missing-c-tags.ps1')) {
    if (-not $out.Contains($must)) { throw "marker lost: $must" }
}
# ASCII-only check: that note line contains the ASCII path `maid/gap.json`
if (-not $out.Contains('maid/gap')) { throw "the maid/gap note was lost" }

[System.IO.File]::WriteAllText($doc, $out, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("held-back README: " + $before + " -> " + $lines.Count + " lines, CRLF, no U+FFFD")
