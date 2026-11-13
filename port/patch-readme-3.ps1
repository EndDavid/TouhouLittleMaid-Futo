# Correct the three README lines that still claim the altar uses floorOffset = 0.125.
#
# ASCII only: replacement text comes from port\_readme_fix.md (UTF-8), split on @@.
# Target lines are found by an ASCII substring so the script never has to embed CJK.

$ErrorActionPreference = 'Stop'
$repo   = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$readme = Join-Path $repo 'README.md'
$data   = Join-Path $repo 'port\_readme_fix.md'
$log    = Join-Path $repo 'port\_readmefix.txt'
$utf8   = New-Object System.Text.UTF8Encoding($false)

# ---- read the replacement blocks (--- separated by @@) -------------------
$blocks = New-Object System.Collections.Generic.List[string]
$cur = New-Object System.Collections.Generic.List[string]
foreach ($l in [System.IO.File]::ReadAllLines($data, [System.Text.Encoding]::UTF8)) {
    if ($l -eq '@@') {
        $blocks.Add(($cur -join "`r`n"))
        $cur = New-Object System.Collections.Generic.List[string]
        continue
    }
    $cur.Add($l)
}
while ($cur.Count -gt 0 -and $cur[$cur.Count - 1] -eq '') { $cur.RemoveAt($cur.Count - 1) }
$blocks.Add(($cur -join "`r`n"))
if ($blocks.Count -ne 3) { throw "expected 3 replacement blocks, got $($blocks.Count)" }

$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($readme, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

# ---- locate the three stale lines by ASCII substrings --------------------
function Find-Line([string]$needle) {
    $hit = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].Contains($needle)) {
            if ($hit -ge 0) { throw "ambiguous: $needle" }
            $hit = $i
        }
    }
    if ($hit -lt 0) { throw "not found: $needle" }
    return $hit
}

$i1 = Find-Line 'floorOffset=0.125'
$i2 = Find-Line '`floorOffset` = 0.125'
# the middle one is the "shrine offset is 0, altar is 0.125" sentence. Other
# lines contain 0.125 too and must be left alone:
#   - the held-item translate table row (contains "translate(")
#   - the "real value is -0.125 .. 5.93" sentence (contains -0.125)
$cands = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($i -eq $i1 -or $i -eq $i2) { continue }
    if (-not $lines[$i].Contains('0.125')) { continue }
    if ($lines[$i].Contains('translate(')) { continue }
    if ($lines[$i].Contains('-0.125')) { continue }
    if ($lines[$i].Contains('5.93')) { continue }   # the bbox sentence uses a real minus sign, not a hyphen
    $cands += $i
}
if ($cands.Count -ne 1) { throw "expected exactly one remaining 0.125 line, got $($cands.Count)" }
$i3 = $cands[0]

$targets = @($i1, $i2, $i3) | Sort-Object
[void]$targets

# replace highest index first so earlier indices stay valid
$map = @{}
$map[$i1] = $blocks[0]
$map[$i2] = $blocks[2]
$map[$i3] = $blocks[1]
foreach ($idx in ($targets | Sort-Object -Descending)) {
    $lines[$idx] = $map[$idx]
}

$out = ($lines -join "`r`n") + "`r`n"
if ($out.Contains([char]0xFFFD)) { throw 'replacement char' }
if ($out.Contains('floorOffset=0.125')) { throw 'stale offset=0.125 left' }
if ($out.Contains('`floorOffset` = 0.125')) { throw 'stale note left' }

[System.IO.File]::WriteAllText($readme, $out, $utf8)
if ([System.IO.File]::ReadAllText($readme, [System.Text.Encoding]::UTF8) -ne $out) { throw 'read-back mismatch' }

[System.IO.File]::WriteAllText($log, "lines $before -> $($lines.Count)`nreplaced $($targets -join ',')`nok", $utf8)
Write-Host 'fixed'
