# Insert the missing default inner animation (sit/skirt_rotation) into MaidModelInfo.
# ASCII only; the Chinese javadoc lives in port\_skirt_snippet.txt (UTF-8).

$ErrorActionPreference = 'Stop'

$repo    = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$file    = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\client\resource\pojo\MaidModelInfo.java'
$snippet = Join-Path $repo 'port\_skirt_snippet.txt'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) { $lines.Add($l) }
$before = $lines.Count

if (($lines | Select-String -SimpleMatch 'applyDefaultInnerAnimations' -Quiet)) {
    throw 'patch already applied'
}

# --- 1) import TouhouLittleMaid (before the gson imports) -----------------
$imp = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq 'import com.google.gson.JsonSyntaxException;') { $imp = $i; break }
}
if ($imp -lt 0) { throw 'gson import not found' }
$lines.Insert($imp, 'import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;')

# --- 2) call it from decorate(), just before the scale clamp -------------
$anchor = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -like '*renderEntityScale = Mth.clamp*') { $anchor = $i; break }
}
if ($anchor -lt 0) { throw 'scale clamp anchor not found' }
$lines.Insert($anchor, '        applyDefaultInnerAnimations();')

# --- 3) insert the method right after decorate() -------------------------
$sig = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -eq '    public MaidModelInfo decorate() {') { $sig = $i; break }
}
if ($sig -lt 0) { throw 'decorate() not found' }
$end = -1
$depth = 0
for ($i = $sig; $i -lt $lines.Count; $i++) {
    foreach ($ch in $lines[$i].ToCharArray()) {
        if ($ch -eq '{') { $depth++ } elseif ($ch -eq '}') { $depth-- }
    }
    if ($depth -eq 0) { $end = $i; break }
}
if ($end -lt 0) { throw 'end of decorate() not found' }

$block = [System.Collections.Generic.List[string]]::new()
foreach ($l in [System.IO.File]::ReadAllLines($snippet, [System.Text.Encoding]::UTF8)) { $block.Add($l) }
while ($block.Count -gt 0 -and $block[$block.Count - 1] -eq '') { $block.RemoveAt($block.Count - 1) }
$lines.InsertRange($end + 1, $block)

# --- 4) write + verify ---------------------------------------------------
$text = ($lines -join "`r`n") + "`r`n"
if ($text.Contains([char]0xFFFD)) { throw 'replacement character in output' }
[System.IO.File]::WriteAllText($file, $text, $utf8)

$check = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
if (-not $check.Contains('private void applyDefaultInnerAnimations()')) { throw 'method missing' }
if (-not $check.Contains('applyDefaultInnerAnimations();')) { throw 'call missing' }
if (-not $check.Contains('import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;')) { throw 'import missing' }
$open = ([regex]::Matches($check, '\{')).Count
$close = ([regex]::Matches($check, '\}')).Count
if ($open -ne $close) { throw "brace mismatch $open / $close" }

Write-Host ("lines: " + $before + " -> " + $lines.Count)
Write-Host 'ok'

