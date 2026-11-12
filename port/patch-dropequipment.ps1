# Replace EntityMaid#dropFilmOnDeath with the real upstream dropEquipment override.
#
# ASCII only on purpose (PowerShell 5.1 reads BOM-less .ps1 as ANSI); the Chinese
# javadoc lives in port\_patch_dropequipment.txt and is spliced in verbatim.

$ErrorActionPreference = 'Stop'

$repo    = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$file    = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\passive\EntityMaid.java'
$snippet = Join-Path $repo 'port\_patch_dropequipment.txt'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

# --- 1) drop the old call inside die() --------------------------------------
$callIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i].Trim() -eq 'this.dropFilmOnDeath();') { $callIdx = $i; break }
}
if ($callIdx -lt 0) { throw 'dropFilmOnDeath() call not found' }
# also drop the comment line directly above it
$from = $callIdx
if ($from -gt 0 -and $lines[$from - 1].Trim().StartsWith('//')) { $from = $from - 1 }
$lines.RemoveRange($from, ($callIdx - $from) + 1)

# --- 2) locate the old method (plus its javadoc) -----------------------------
$idx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '    private void dropFilmOnDeath() {') { $idx = $i; break }
}
if ($idx -lt 0) { throw 'dropFilmOnDeath() method not found' }

$start = $idx
for ($i = $idx - 1; $i -ge [Math]::Max(0, $idx - 40); $i--) {
    $t = $lines[$i].Trim()
    if ($t -eq '/**') { $start = $i; break }
    if ($t -ne '' -and -not $t.StartsWith('*')) { break }
}

# brace-match to find the end of the method
$end = -1
$depth = 0
$seenOpen = $false
for ($i = $idx; $i -lt $lines.Count; $i++) {
    foreach ($ch in $lines[$i].ToCharArray()) {
        if ($ch -eq '{') { $depth++; $seenOpen = $true }
        elseif ($ch -eq '}') { $depth-- }
    }
    if ($seenOpen -and $depth -eq 0) { $end = $i; break }
}
if ($end -lt 0) { throw 'could not find the end of dropFilmOnDeath()' }

$lines.RemoveRange($start, ($end - $start) + 1)

# --- 3) splice the replacement in ------------------------------------------
$block = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($snippet, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }
$block.Add('')
$lines.InsertRange($start, $block)

# --- 4) write back ----------------------------------------------------------
$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement character in output' }
[System.IO.File]::WriteAllText($file, $text, $utf8)

# --- 5) verify --------------------------------------------------------------
$after = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
foreach ($need in @(
    'protected void dropEquipment()',
    'public void remove(net.minecraft.world.entity.Entity.RemovalReason reason)',
    'private boolean alreadyDropped;',
    'EntityTombstone',
    'MaidWorldData.get(this.level)'
)) {
    if (-not $after.Contains($need)) { throw ("missing after write: " + $need) }
}
if ($after.Contains('dropFilmOnDeath')) { throw 'dropFilmOnDeath still referenced' }
$open = ([regex]::Matches($after, '\{')).Count
$close = ([regex]::Matches($after, '\}')).Count
if ($open -ne $close) { throw "brace mismatch $open / $close" }

Write-Host ("lines: " + $before + " -> " + $lines.Count)
Write-Host 'ok'

