# Add the missing statue / chisel / garage-kit lang keys to en_us.json and zh_cn.json,
# and make the garage-kit tab icon a stable ItemStack.
#
# Values are taken verbatim from upstream (_ref/_1201assets/lang/*.json), not invented.
# The two lang files are stored with mixed EOL and are edited by byte-level insertion
# before the closing brace, so no whole-file rewrite can produce a phantom diff.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$langDir = Join-Path $root 'src\main\resources\assets\touhou_little_maid\lang'

function Read-Utf8([string]$p) {
    $t = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
    if ($t.Length -gt 0 -and [int]$t[0] -eq 0xFEFF) { $t = $t.Substring(1) }
    return $t
}

function Add-LangKeys([string]$file, [string[]]$lines) {
    $t = Read-Utf8 $file
    $nl = if ($t.Contains("`r`n")) { "`r`n" } else { "`n" }
    $missing = @()
    foreach ($l in $lines) {
        $key = ($l -split '":')[0].TrimStart()
        $key = $key.Substring(1)   # drop the leading double quote
        if (-not $t.Contains('"' + $key + '"')) { $missing += $l }
    }
    if ($missing.Count -eq 0) {
        Write-Output ("  =   " + (Split-Path $file -Leaf) + " : nothing missing")
        return
    }
    # insert before the last closing brace
    $i = $t.LastIndexOf('}')
    if ($i -lt 0) { throw "no closing brace in $file" }
    # the character before the brace decides whether we need a leading comma
    $j = $i - 1
    while ($j -ge 0 -and " `t$nl".Contains($t[$j])) { $j-- }
    if ($j -lt 0) { throw "cannot find the last entry in $file" }
    $needComma = ($t[$j] -ne '{')
    $block = ""
    for ($k = 0; $k -lt $missing.Count; $k++) {
        $isLast = ($k -eq $missing.Count - 1)
        if ($k -eq 0 -and $needComma) { $block += "," }
        $block += $nl + "  " + $missing[$k].TrimEnd(',', ' ')
        if (-not $isLast) { $block += "," }
    }
    $t = $t.Substring(0, $i) + $block + $nl + $t.Substring($i)
    [System.IO.File]::WriteAllText($file, $t, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output ("  +   " + (Split-Path $file -Leaf) + " : added " + $missing.Count + " key(s)")
}

Write-Output '--- 1. lang keys ---'
$en = @(
    '  "item.touhou_little_maid.chisel": "Chisel",',
    '  "block.touhou_little_maid.statue": "Statue",',
    '  "block.touhou_little_maid.garage_kit": "Garage Kit",',
    '  "block.touhou_little_maid.garage_kit.prefix": "Garage Kit: ",',
    '  "itemGroup.touhou_little_maid.garage_kit": "Garage Kit"'
)
Add-LangKeys (Join-Path $langDir 'en_us.json') $en

$zhText = Read-Utf8 (Join-Path $PSScriptRoot '_lang_statue_zh.txt')
$zh = @()
foreach ($l in ($zhText -replace "`r`n", "`n").Split("`n")) {
    if ($l.Trim().Length -gt 0) { $zh += $l }
}
Add-LangKeys (Join-Path $langDir 'zh_cn.json') $zh

Write-Output '--- 2. verify both files still parse ---'
foreach ($f in 'en_us.json', 'zh_cn.json') {
    $p = Join-Path $langDir $f
    try { $null = (Read-Utf8 $p) | ConvertFrom-Json } catch { throw "$f does not parse: $_" }
    $t = Read-Utf8 $p
    foreach ($needle in '"item.touhou_little_maid.chisel"', '"block.touhou_little_maid.statue"',
                      '"block.touhou_little_maid.garage_kit"', '"block.touhou_little_maid.garage_kit.prefix"',
                      '"itemGroup.touhou_little_maid.garage_kit"') {
        if (-not $t.Contains($needle)) { throw "$f still missing $needle" }
    }
    Write-Output ("  ok  " + $f + " parses and has all 5 keys")
}

Write-Output '--- 3. stable tab icon ---'
$initItems = Join-Path $root 'src\main\java\com\github\tartaricacid\touhoulittlemaid\init\InitItems.java'
$t = [System.IO.File]::ReadAllText($initItems, [System.Text.Encoding]::UTF8)
$wasCrlf = $t.Contains("`r`n")
$t = $t -replace "`r`n", "`n"
$from = '            .icon(() -> new ItemStack(InitItems.GARAGE_KIT))'
if (([regex]::Matches($t, [regex]::Escape($from))).Count -ne 1) { throw 'tab icon anchor not unique' }
$to = '            .icon(() -> GARAGE_KIT_ICON)'
$t = $t.Replace($from, $to)
# declare the stable stack right above the tab
$anchor = '    public static final CreativeModeTab GARAGE_KIT_TAB = FabricItemGroupBuilder'
if (([regex]::Matches($t, [regex]::Escape($anchor))).Count -ne 1) { throw 'GARAGE_KIT_TAB anchor not unique' }
$decl = @'
    /**
     * 手办栏的图标物品。必须是一个【复用】的 ItemStack。
     *
     * <p>写成 {@code () -> new ItemStack(...)} 也可以跑，但 icon lambda 每帧都会求值，
     * 于是每帧新建一个 ItemStack、每帧喂给物品渲染器一个全新的键。手办图标的 3D 实体
     * 缓存曾经因为这种「每帧新对象」被撑爆过（见 EntityCacheUtil.GARAGE_KIT_CACHE），
     * 所以这里从一开始就复用同一个栈。</p>
     */
    private static final ItemStack GARAGE_KIT_ICON = new ItemStack(InitItems.GARAGE_KIT);

'@ -replace "`r`n", "`n"
$t = $t.Replace($anchor, $decl + $anchor)
if ($t.Contains([string][char]0xFFFD)) { throw 'U+FFFD in InitItems' }
if ($wasCrlf) { $t = $t -replace "`n", "`r`n" }
[System.IO.File]::WriteAllText($initItems, $t, (New-Object System.Text.UTF8Encoding($false)))
Write-Output '  ok  GARAGE_KIT_ICON'

Write-Output 'fix script done'

