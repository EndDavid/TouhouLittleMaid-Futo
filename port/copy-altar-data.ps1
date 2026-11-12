# Copy the altar multiblock structure templates and the altar recipes.
#
# The recipes are copied verbatim except for one mechanical change: Forge's
# "forge:" item-tag namespace becomes Fabric's conventional "c:" namespace.
# Tags that Fabric API 1.18.2 does not ship are defined by us under
# data/c/tags/items/ (see the commit message for the mapping table).
#
# Recipes whose result is not ported yet stay upstream; they are listed in
# $Skip so that nothing silently references an unregistered item.

$ErrorActionPreference = 'Stop'

$repo   = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$ref    = 'D:\codename_EndDavid\hello java\_ref\tlm-1182'
$srcAlt = Join-Path $ref 'src\main\resources\data\touhou_little_maid\recipes\altar'
$dstAlt = Join-Path $repo 'src\main\resources\data\touhou_little_maid\recipes\altar'
$srcStr = Join-Path $ref 'src\main\resources\data\touhou_little_maid\structures'
$dstStr = Join-Path $repo 'src\main\resources\data\touhou_little_maid\structures'

New-Item -ItemType Directory -Force -Path $dstAlt | Out-Null
New-Item -ItemType Directory -Force -Path $dstStr | Out-Null

# structure templates (4 orientations) -- byte copies, binary
Get-ChildItem $srcStr -Filter 'altar_*.nbt' | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $dstStr $_.Name) -Force
}

# forge: -> c: tag mapping, longest first so that no prefix can match twice
$TagMap = [ordered]@{
    'forge:storage_blocks/quartz' = 'c:quartz_blocks'
    'forge:chests/wooden'         = 'c:wooden_chests'
    'forge:crops/nether_wart'     = 'c:nether_wart'
    'forge:dusts/redstone'        = 'c:redstone_dusts'
    'forge:ender_pearls'          = 'c:ender_pearls'
    'forge:nuggets/gold'          = 'c:gold_nuggets'
    'forge:rods/wooden'           = 'c:wooden_rods'
    'forge:ingots/gold'           = 'c:gold_ingots'
    'forge:ingots/iron'           = 'c:iron_ingots'
    'forge:gems/diamond'          = 'c:diamonds'
    'forge:gems/emerald'          = 'c:emeralds'
    'forge:gems/lapis'            = 'c:lapis'
    'forge:dyes/light_blue'       = 'c:light_blue_dyes'
    'forge:dyes/light_gray'       = 'c:light_gray_dyes'
    'forge:dyes/black'            = 'c:black_dyes'
    'forge:dyes/blue'             = 'c:blue_dyes'
    'forge:dyes/cyan'             = 'c:cyan_dyes'
    'forge:dyes/gray'             = 'c:gray_dyes'
    'forge:dyes/green'            = 'c:green_dyes'
    'forge:dyes/lime'             = 'c:lime_dyes'
    'forge:dyes/magenta'          = 'c:magenta_dyes'
    'forge:dyes/orange'           = 'c:orange_dyes'
    'forge:dyes/pink'             = 'c:pink_dyes'
    'forge:dyes/purple'           = 'c:purple_dyes'
    'forge:dyes/red'              = 'c:red_dyes'
    'forge:dyes/white'            = 'c:white_dyes'
    'forge:dyes/yellow'           = 'c:yellow_dyes'
    'forge:feathers'              = 'c:feathers'
    'forge:gunpowder'             = 'c:gunpowder'
    'forge:obsidian'              = 'c:obsidian'
}

# Results that are not ported yet (see the subsystem list in the commit message).
$Skip = @(
    'craft_bookshelf.json'
    'craft_broom.json'
    'craft_cchess.json'
    'craft_chisel.json'
    'craft_computer.json'
    'craft_gomoku.json'
    'craft_kappa_compass.json'
    'craft_keyboard.json'
    'craft_picnic_basket.json'
    'craft_red_fox_scroll.json'
    'craft_trumpet.json'
    'craft_wchess.json'
    'craft_white_fox_scroll.json'
    'craft_wireless_io.json'
    'servant_bell.json'
    'spawn_box.json'
)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$copied = 0
$skipped = 0

Get-ChildItem $srcAlt -Filter '*.json' | Sort-Object Name | ForEach-Object {
    if ($Skip -contains $_.Name) {
        $skipped++
        return
    }
    $text = [System.IO.File]::ReadAllText($_.FullName)
    foreach ($key in $TagMap.Keys) {
        $text = $text.Replace('"' + $key + '"', '"' + $TagMap[$key] + '"')
    }
    if ($text -match '"forge:') {
        throw ("unmapped forge tag left in " + $_.Name)
    }
    [System.IO.File]::WriteAllText((Join-Path $dstAlt $_.Name), $text, $utf8NoBom)
    $copied++
}

Write-Host ("copied recipes = " + $copied + ", skipped = " + $skipped)
Write-Host ("structures = " + (Get-ChildItem $dstStr -Filter 'altar_*.nbt').Count)

