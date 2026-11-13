# floorOffset must never LIFT a model above the block floor.
#
# Evidence (port/altar-cube-dist.ps1 output): the altar model has 17 unrotated cubes
# whose bottom is exactly 0 -- all six pillar bones, plus bone56 (10 cubes) and
# bone62 (8) -- while 42 cubes sit below 0, down to -0.125. So the model's y = 0 IS
# the ground plane and those 42 are meant to be sunk.
#
# Returning -minY lifted the whole altar by 0.125 (2 px): the "slightly floating,
# can't tell how much" the user reported. The rule is -max(0, minY): push a model
# down if it floats above the floor, never raise one that dips below.
#
# ASCII only: the .ps1 is parsed as ANSI, so Chinese text may not appear as a
# literal here. It is read from port\_floornote.txt (UTF-8) and located by ASCII
# anchors instead.

$ErrorActionPreference = 'Stop'
$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$p    = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\client\resource\BedrockBlockModels.java'
$log  = Join-Path $repo 'port\_floorpatch.txt'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$text = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
$before = $text.Length
if ($text.Contains('Math.max(0, b[1])')) { throw 'already applied' }

$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($p, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }

# ---- 1. locate the method --------------------------------------------------
$sig = '    public static float floorOffset(String name) {'
$sigIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq $sig) { $sigIdx = $i; break }
}
if ($sigIdx -lt 0) { throw 'floorOffset signature not found' }

# walk back to the opening /** of its Javadoc
$docStart = -1
for ($i = $sigIdx - 1; $i -ge 0; $i--) {
    if ($lines[$i] -eq '    /**') { $docStart = $i; break }
    if ($lines[$i] -ne '' -and $lines[$i] -notmatch '^\s+\*') { break }
}
if ($docStart -lt 0) { throw 'Javadoc start not found' }

# ---- 2. replace the Javadoc ------------------------------------------------
$note = [System.IO.File]::ReadAllLines((Join-Path $repo 'port\_floornote.txt'), [System.Text.Encoding]::UTF8)
$lines.RemoveRange($docStart, $sigIdx - $docStart)
for ($i = $note.Count - 1; $i -ge 0; $i--) {
    if ($note[$i] -ne '' -or $true) { $lines.Insert($docStart, $note[$i]) }
}

# ---- 3. replace the body ---------------------------------------------------
$sigIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq $sig) { $sigIdx = $i; break }
}
if ($sigIdx -lt 0) { throw 'signature lost after Javadoc swap' }

$oldRet = '        return b == null ? 0 : -b[1];'
$retIdx = -1
for ($i = $sigIdx; $i -lt $lines.Count -and $i -lt $sigIdx + 12; $i++) {
    if ($lines[$i] -eq $oldRet) { $retIdx = $i; break }
}
if ($retIdx -lt 0) { throw 'old return not found in body' }

$newBody = @(
    '        float[] b = BOUNDS.get(name);',
    '        if (b == null) {',
    '            return 0;',
    '        }',
    '        // push down, never lift: y = 0 is the block floor, geometry below 0 is meant to be sunk',
    '        return -Math.max(0, b[1]);'
)
# remove the old single-line return, keep the get(name); line above it
$lines.RemoveAt($retIdx)
for ($i = $newBody.Count - 1; $i -ge 0; $i--) { $lines.Insert($retIdx, $newBody[$i]) }

# ---- 4. make the log distinguish minY from the applied offset --------------
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i].Contains('floorOffset={}",')) {
        $lines[$i] = $lines[$i].Replace('floorOffset={}",', 'offset={} (model minY={})",')
        break
    }
}

# ---- 5. write + verify -----------------------------------------------------
$out = ($lines -join "`r`n") + "`r`n"
if ($out.Contains([char]0xFFFD)) { throw 'replacement char' }
$o = ([regex]::Matches($out, '\{')).Count
$c = ([regex]::Matches($out, '\}')).Count
if ($o -ne $c) { throw "brace mismatch $o/$c" }
if (-not $out.Contains('return -Math.max(0, b[1]);')) { throw 'new rule missing' }
if ($out.Contains('-b[1];') -and $out.Contains('b == null ? 0 : -b[1]')) { throw 'old rule still present' }

[System.IO.File]::WriteAllText($p, $out, $utf8)
if ([System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8) -ne $out) { throw 'read-back mismatch' }

[System.IO.File]::WriteAllText($log, "len $before -> $($out.Length)`nlines $($lines.Count)`nbraces $o/$c`nok", $utf8)
Write-Host 'patched'

