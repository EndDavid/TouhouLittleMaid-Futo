# Fix the blank garage-kit tab icon, and repair the mojibake javadoc above it.
#
# Root cause of the blank icon: GARAGE_KIT_ICON was a static final field declared up
# next to CHAIR_TAB, i.e. BEFORE the GARAGE_KIT item field is registered further down
# the file. Java initialises static fields in declaration order, so the field captured
# a null item and the icon stack stayed empty -- an empty stack is never drawn.
# Upstream builds it lazily (MaidGroup#makeIcon), so go back to a lazy supplier.
#
# Also repairs the GARAGE_KIT_TAB javadoc, which an earlier script wrote as mojibake:
# that script had Chinese literals inside a BOM-less .ps1, which Windows PowerShell 5.1
# parses as ANSI, so the text was mangled before it ever reached the file.
#
# ASCII-only script. All Chinese comes from UTF-8 snippets next to this file.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$p = Join-Path $root 'src\main\java\com\github\tartaricacid\touhoulittlemaid\init\InitItems.java'

function Read-Utf8([string]$path) {
    $t = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    if ($t.Length -gt 0 -and [int]$t[0] -eq 0xFEFF) { $t = $t.Substring(1) }
    return $t
}

$raw = Read-Utf8 $p
$lines = New-Object System.Collections.Generic.List[string]
foreach ($l in ($raw -replace "`r`n", "`n").Split("`n")) { $lines.Add($l) }
$before = $lines.Count

# ---- 1. drop the eager field and the javadoc that documents it ----
$fieldIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'private static final ItemStack GARAGE_KIT_ICON = new ItemStack') { $fieldIdx = $i; break }
}
if ($fieldIdx -lt 0) { throw 'GARAGE_KIT_ICON field line not found' }
$docStart = -1
for ($i = $fieldIdx - 1; $i -ge 0; $i--) {
    if ($lines[$i].Trim() -eq '/**') { $docStart = $i; break }
}
if ($docStart -lt 0) { throw 'javadoc opening for GARAGE_KIT_ICON not found' }
Write-Output ("  removing lines " + ($docStart + 1) + ".." + ($fieldIdx + 1))
$lines.RemoveRange($docStart, $fieldIdx - $docStart + 1)

# ---- 2. rewrite the GARAGE_KIT_TAB javadoc from the UTF-8 snippet ----
$tabIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'public static final CreativeModeTab GARAGE_KIT_TAB') { $tabIdx = $i; break }
}
if ($tabIdx -lt 0) { throw 'GARAGE_KIT_TAB declaration not found' }
$tabDocStart = -1
for ($i = $tabIdx - 1; $i -ge 0; $i--) {
    if ($lines[$i].Trim() -eq '/**') { $tabDocStart = $i; break }
}
if ($tabDocStart -lt 0) { throw 'javadoc opening for GARAGE_KIT_TAB not found' }
$doc = (Read-Utf8 (Join-Path $PSScriptRoot '_fav_tab_doc.txt')) -replace "`r`n", "`n"
$docLines = @()
foreach ($l in $doc.TrimEnd([char[]]@(13, 10)).Split("`n")) { $docLines += $l }
Write-Output ("  replacing TAB javadoc lines " + ($tabDocStart + 1) + ".." + $tabIdx)
$lines.RemoveRange($tabDocStart, $tabIdx - $tabDocStart)
$lines.InsertRange($tabDocStart, [string[]]$docLines)

# ---- 3. lazy icon ----
$iconIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'GARAGE_KIT_ICON\)') { $iconIdx = $i; break }
}
if ($iconIdx -lt 0) { throw 'icon usage line not found' }
$lines[$iconIdx] = '            .icon(() -> new ItemStack(InitItems.GARAGE_KIT))'
Write-Output ("  icon line " + ($iconIdx + 1) + " is lazy again")

$out = [string]::Join("`r`n", $lines.ToArray())
if ($out.Contains('GARAGE_KIT_ICON')) { throw 'GARAGE_KIT_ICON still referenced' }
if ($out.Contains([string][char]0xFFFD)) { throw 'U+FFFD introduced' }
foreach ($need in @('GARAGE_KIT_TAB', 'public static final Item GARAGE_KIT = Registry.register',
                    'new ItemStack(InitItems.GARAGE_KIT)', 'CHAIR_TAB')) {
    if (-not $out.Contains($need)) { throw "lost: $need" }
}

[System.IO.File]::WriteAllText($p, $out, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("  InitItems: " + $before + " -> " + $lines.Count + " lines")
Write-Output 'done'

