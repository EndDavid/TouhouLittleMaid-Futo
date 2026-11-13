# Port the trumpet: item registration, lang keys, altar recipe, book page.
# ASCII-only script; all Chinese comes from UTF-8 snippets next to this file.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Read-Utf8([string]$path) {
    $t = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    if ($t.Length -gt 0 -and [int]$t[0] -eq 0xFEFF) { $t = $t.Substring(1) }
    return $t
}
function Save-Text([string]$path, [string]$t) {
    if ($t.Contains([string][char]0xFFFD)) { throw "U+FFFD in result: $path" }
    [System.IO.File]::WriteAllText($path, $t, (New-Object System.Text.UTF8Encoding($false)))
}
function Add-LangKeys([string]$file, [string]$snippet) {
    $t = Read-Utf8 $file
    $nl = if ($t.Contains("`r`n")) { "`r`n" } else { "`n" }
    $lines = @()
    foreach ($l in (Read-Utf8 $snippet) -replace "`r`n", "`n" | Out-Null) { }
    foreach ($l in ((Read-Utf8 $snippet) -replace "`r`n", "`n").Split("`n")) {
        if ($l.Trim().Length -gt 0) { $lines += $l }
    }
    $missing = @()
    foreach ($l in $lines) {
        $key = ($l -split '":')[0].TrimStart([char]0x22)
        if (-not $t.Contains([char]0x22 + $key + [char]0x22)) { $missing += $l }
    }
    if ($missing.Count -eq 0) { Write-Output ("  =   " + (Split-Path $file -Leaf) + " nothing missing"); return }
    $i = $t.LastIndexOf('}')
    if ($i -lt 0) { throw "no closing brace in $file" }
    $j = $i - 1
    while ($j -ge 0 -and " `t$nl".Contains($t[$j])) { $j-- }
    if ($j -lt 0) { throw "cannot find last entry in $file" }
    $needComma = ($t[$j] -ne '{')
    $block = ""
    for ($k = 0; $k -lt $missing.Count; $k++) {
        if ($k -eq 0 -and $needComma) { $block += "," }
        $block += $nl + "  " + $missing[$k].TrimEnd(',', ' ')
        if ($k -lt $missing.Count - 1) { $block += "," }
    }
    Save-Text $file ($t.Substring(0, $i) + $block + $nl + $t.Substring($i))
    Write-Output ("  +   " + (Split-Path $file -Leaf) + " added " + $missing.Count)
}

Write-Output '--- 1. InitItems: register TRUMPET ---'
$initItems = Join-Path $root 'src\main\java\com\github\tartaricacid\touhoulittlemaid\init\InitItems.java'
$t = Read-Utf8 $initItems
$wasCrlf = $t.Contains("`r`n")
$t = $t -replace "`r`n", "`n"
$anchor = '            new com.github.tartaricacid.touhoulittlemaid.item.ItemGarageKit());'
if (([regex]::Matches($t, [regex]::Escape($anchor))).Count -ne 1) { throw 'GARAGE_KIT registration anchor not unique' }
$add = (Read-Utf8 (Join-Path $PSScriptRoot '_trumpet_inititems.txt')) -replace "`r`n", "`n"
$add = $add.TrimEnd([char[]]@(13, 10))
$i = $t.IndexOf($anchor) + $anchor.Length
$t = $t.Substring(0, $i) + "`n" + $add + $t.Substring($i)
if ($wasCrlf) { $t = $t -replace "`n", "`r`n" }
Save-Text $initItems $t
Write-Output '  ok  TRUMPET registered'

Write-Output '--- 2. lang keys ---'
$langDir = Join-Path $root 'src\main\resources\assets\touhou_little_maid\lang'
Add-LangKeys (Join-Path $langDir 'en_us.json') (Join-Path $PSScriptRoot '_trumpet_lang_en.txt')
Add-LangKeys (Join-Path $langDir 'zh_cn.json') (Join-Path $PSScriptRoot '_trumpet_lang_zh.txt')
foreach ($f in 'en_us.json', 'zh_cn.json') {
    $p = Join-Path $langDir $f
    try { $null = (Read-Utf8 $p) | ConvertFrom-Json } catch { throw "$f does not parse: $_" }
    Write-Output ("  ok  " + $f + " parses")
}

Write-Output '--- 3. altar recipe ---'
$recipe = @'
{
  "type": "touhou_little_maid:altar_crafting",
  "output": {
    "type": "minecraft:item",
    "nbt": {
      "Item": {
        "id": "touhou_little_maid:trumpet",
        "Count": 1
      }
    }
  },
  "power": 0.2,
  "ingredients": [
    { "tag": "c:gold_ingots" },
    { "tag": "c:gold_ingots" },
    { "tag": "c:iron_ingots" },
    { "tag": "c:iron_ingots" },
    { "tag": "c:iron_ingots" },
    { "item": "minecraft:note_block" }
  ]
}
'@ -replace "`r`n", "`n"
$recipePath = Join-Path $root 'src\main\resources\data\touhou_little_maid\recipes\altar\craft_trumpet.json'
Save-Text $recipePath (($recipe.TrimEnd([char[]]@(13, 10))) -replace "`n", "`r`n")
$null = (Read-Utf8 $recipePath) | ConvertFrom-Json
Write-Output '  ok  craft_trumpet.json written and parses'

Write-Output '--- 4. book page ---'
$page = Join-Path $root 'port\book_pages_held_back\other\trumpet.json'
$pageDst = Join-Path $root 'src\main\resources\assets\touhou_little_maid\patchouli_books\memorizable_gensokyo\en_us\entries\other\trumpet.json'
Copy-Item -LiteralPath $page -Destination $pageDst -Force
Remove-Item -LiteralPath $page -Force
$null = (Read-Utf8 $pageDst) | ConvertFrom-Json
Write-Output '  ok  other/trumpet.json restored and parses'

Write-Output '--- 5. validator knows the id ---'
$v = Join-Path $root 'port\book_pages_held_back\validate-book.ps1'
$vt = Read-Utf8 $v
$from = "  'touhou_little_maid:statue',"
if (([regex]::Matches($vt, [regex]::Escape($from))).Count -ne 1) { throw 'validator anchor not unique' }
Save-Text $v $vt.Replace($from, $from + "`r`n  'touhou_little_maid:trumpet',")
Write-Output '  ok  trumpet added to validate-book.ps1'

Write-Output 'done'

