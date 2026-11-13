# Patch README.md: replace three stale lines with accurate text.
# ASCII-only on purpose: Windows PowerShell 5.1 parses BOM-less .ps1 as ANSI, so any
# non-ASCII literal here would be mangled. All Chinese text lives in the UTF-8
# snippet files next to this script.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$readme = Join-Path $root 'README.md'

function Read-Utf8([string]$path) {
    $t = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    if ($t.Length -gt 0 -and [int]$t[0] -eq 0xFEFF) { $t = $t.Substring(1) }
    return $t
}

$raw = Read-Utf8 $readme

# the repo stores text as CRLF; a whole-file EOL rewrite would produce a 2000-line diff
$crlf = ([regex]::Matches($raw, "`r`n")).Count
$lf = ([regex]::Matches($raw, "`n")).Count
if ($crlf -ne $lf) { throw "mixed EOL in README.md: crlf=$crlf lf=$lf" }

$lines = New-Object System.Collections.Generic.List[string]
foreach ($l in ($raw -split "`r`n")) { $lines.Add($l) }
$before = $lines.Count

function Load-Snippet([string]$name) {
    $t = Read-Utf8 (Join-Path $PSScriptRoot $name)
    $t = $t -replace "`r`n", "`n"
    # snippet files end with an extra CRLF; a stray blank line would break the
    # markdown list it is spliced into, so drop every trailing empty line
    while ($t.EndsWith("`n")) { $t = $t.Substring(0, $t.Length - 1) }
    return ($t -split "`n")
}

function Replace-Line([string]$anchorName, [scriptblock]$pred, [string]$snippet) {
    $hits = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if (& $pred $lines[$i]) { $hits += $i }
    }
    if ($hits.Count -ne 1) { throw "anchor '$anchorName' matched $($hits.Count) lines, expected exactly 1" }
    $new = Load-Snippet $snippet
    Write-Output ("anchor '" + $anchorName + "' at line " + ($hits[0] + 1) + " -> " + $new.Count + " line(s)")
    $lines.RemoveAt($hits[0])
    $lines.InsertRange($hits[0], [string[]]$new)
}

Replace-Line 'book-39' { param($l) $l -match 'validate-book' } '_readme_book39.txt'
Replace-Line 'altar-component' { param($l) $l -match 'AltarRecipeComponent' } '_v2_readme_altar.txt'
Replace-Line 'pages-13' { param($l) ($l -match 'book_pages_held_back') -and ($l -match '23') } '_readme_pages13.txt'

$out = ($lines -join "`r`n")
$bad = 0
foreach ($ch in $out.ToCharArray()) { if ([int]$ch -eq 0xFFFD) { $bad++ } }
if ($bad -ne 0) { throw "replacement characters in result: $bad" }
foreach ($must in @('MountedHeight', 'AltarRecipeComponent', 'validate-book', 'book_pages_held_back')) {
    if ($out.IndexOf($must) -lt 0) { throw "marker lost: $must" }
}

# 3 stale lines replaced by 4 + 10 + 2 lines => net +13
$expected = $before + 14
$after = $lines.Count
if ($after -ne $expected) { throw "line count $after, expected $expected (before=$before)" }

[System.IO.File]::WriteAllText($readme, $out, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("README.md: " + $before + " -> " + $after + " lines, no U+FFFD, CRLF preserved")
