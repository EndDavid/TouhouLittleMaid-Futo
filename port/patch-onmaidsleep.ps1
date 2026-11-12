# Insert EntityMaid#onMaidSleep and its per-tick call.
#
# ASCII only: Windows PowerShell 5.1 parses a BOM-less .ps1 as ANSI, so any
# non-ASCII literal in this file would be a syntax error. The Chinese javadoc
# is therefore kept in a separate UTF-8 file and spliced in verbatim.

$ErrorActionPreference = 'Stop'

$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$file = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\passive\EntityMaid.java'
$snippet = Join-Path $repo 'port\_patch_onMaidSleep.txt'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

# --- 1) sanity: the method must not already be there -------------------------
if (($lines | Select-String -SimpleMatch 'private void onMaidSleep()' -Quiet)) {
    throw 'onMaidSleep already present'
}

# --- 2) insert the call inside tick() ---------------------------------------
$tickIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '    public void tick() {') { $tickIdx = $i; break }
}
if ($tickIdx -lt 0) { throw 'tick() not found' }

$carryIdx = -1
for ($i = $tickIdx; $i -lt [Math]::Min($tickIdx + 12, $lines.Count); $i++) {
    if ($lines[$i] -eq '        this.updateCarriedPosition();') { $carryIdx = $i; break }
}
if ($carryIdx -lt 0) { throw 'updateCarriedPosition() call not found inside tick()' }

$lines.Insert($carryIdx + 1, '        this.onMaidSleep();')

# --- 3) insert the method body just above the "per tick" section marker -----
# tick() is preceded by "    @Override"; the block goes above that annotation.
$marker = $tickIdx - 1
if ($lines[$marker].Trim() -ne '@Override') { throw ('expected @Override above tick(), found: ' + $lines[$marker]) }

$block = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($snippet, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
# drop the trailing blank line of the snippet; the marker already provides separation
while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }
$block.Add('')
$lines.InsertRange($marker, $block)

# --- 4) write back ----------------------------------------------------------
$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement character in output' }
[System.IO.File]::WriteAllText($file, $text, $utf8)

# --- 5) verify --------------------------------------------------------------
$after = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
if (-not $after.Contains('private void onMaidSleep()')) { throw 'method missing after write' }
if (-not $after.Contains('this.onMaidSleep();')) { throw 'call missing after write' }
$open = ([regex]::Matches($after, '\{')).Count
$close = ([regex]::Matches($after, '\}')).Count
if ($open -ne $close) { throw "brace mismatch $open / $close" }

Write-Host ("lines: " + $before + " -> " + $lines.Count)
Write-Host "ok"
