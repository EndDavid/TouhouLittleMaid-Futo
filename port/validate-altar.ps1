# Sanity-check the copied altar recipes.
#
#   - every recipe must declare type touhou_little_maid:altar_crafting
#   - no "forge:" tag may survive
#   - every "touhou_little_maid:<item>" reference must be a registered item
#   - every "c:<tag>" reference must either be shipped by fabric-convention-tags-v1
#     or be one of the tags we define ourselves under data/c/tags/items/
#   - every output entity must be a registered entity type

$ErrorActionPreference = 'Stop'

$root = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$dir  = Join-Path $root 'src\main\resources\data\touhou_little_maid\recipes\altar'
$tags = Join-Path $root 'src\main\resources\data\c\tags\items'

# touhou_little_maid item ids registered by InitItems
$items = @(
  'maid_spawn_egg','fairy_spawn_egg','extinguisher','hakurei_gohei','sanae_gohei',
  'camera','photo','maid_beacon','film','shrine','maid_bed','scarecrow','substitute_jizo',
  'power_point',
  'ultramarine_orb_elixir','explosion_protect_bauble','fire_protect_bauble',
  'projectile_protect_bauble','magic_protect_bauble','fall_protect_bauble',
  'drown_protect_bauble','nimble_fabric','item_magnet_bauble','mute_bauble',
  'maid_backpack_small','maid_backpack_middle','maid_backpack_big',
  'crafting_table_backpack','ender_chest_backpack','furnace_backpack','tank_backpack'
) | ForEach-Object { 'touhou_little_maid:' + $_ }

# entity ids registered by InitEntities
$entities = @(
  'touhou_little_maid:maid','touhou_little_maid:fairy','touhou_little_maid:power_point',
  'touhou_little_maid:extinguishing_agent','touhou_little_maid:danmaku',
  'touhou_little_maid:fishing_hook',
  'minecraft:item','minecraft:lightning_bolt'
)

# conventional tags shipped by fabric-convention-tags-v1 1.1.0 (from the jar listing)
$fabricTags = @(
  'black_dyes','blue_dyes','brown_dyes','cyan_dyes','gray_dyes','green_dyes',
  'light_blue_dyes','light_gray_dyes','lime_dyes','magenta_dyes','orange_dyes',
  'pink_dyes','purple_dyes','red_dyes','white_dyes','yellow_dyes','dyes',
  'redstone_dusts','diamonds','emeralds','lapis','gold_ingots','iron_ingots',
  'copper_ingots','netherite_ingots','quartz','coal'
) | ForEach-Object { 'c:' + $_ }

$ourTags = @(Get-ChildItem $tags -Filter *.json | ForEach-Object { 'c:' + $_.BaseName })

$problems = 0
$count = 0

foreach ($f in Get-ChildItem $dir -Filter *.json | Sort-Object Name) {
    $count++
    $text = [System.IO.File]::ReadAllText($f.FullName)
    $j = $text | ConvertFrom-Json

    if ($j.type -ne 'touhou_little_maid:altar_crafting') {
        Write-Output ("BAD TYPE  {0} -> {1}" -f $f.Name, $j.type); $problems++
    }
    if ($text -match 'forge:') {
        Write-Output ("FORGE TAG LEFT  {0}" -f $f.Name); $problems++
    }

    $entity = $j.output.type
    if (-not ($entities -contains $entity)) {
        Write-Output ("OUTPUT ENTITY MISSING  {0} -> {1}" -f $f.Name, $entity); $problems++
    }

    foreach ($ing in $j.ingredients) {
        if ($null -ne $ing.item -and $ing.item -like 'touhou_little_maid:*') {
            if (-not ($items -contains $ing.item)) {
                Write-Output ("INGREDIENT ITEM MISSING  {0} -> {1}" -f $f.Name, $ing.item); $problems++
            }
        }
        if ($null -ne $ing.tag -and $ing.tag -like 'c:*') {
            if (-not (($fabricTags -contains $ing.tag) -or ($ourTags -contains $ing.tag))) {
                Write-Output ("TAG NOT DEFINED  {0} -> {1}" -f $f.Name, $ing.tag); $problems++
            }
        }
        if ($null -ne $ing.tag -and $ing.tag -like 'minecraft:*') {
            # vanilla item tags (minecraft:planks / wool / fishes) are always present
        } elseif ($null -ne $ing.tag -and $ing.tag -notlike 'c:*') {
            Write-Output ("NON-CONVENTIONAL TAG  {0} -> {1}" -f $f.Name, $ing.tag); $problems++
        }
    }

    # the copy-ingredient must also be a registered item
    $copy = $j.output.copy
    if ($null -ne $copy -and $null -ne $copy.ingredient.item) {
        if (-not ($items -contains $copy.ingredient.item)) {
            Write-Output ("COPY INGREDIENT MISSING  {0} -> {1}" -f $f.Name, $copy.ingredient.item); $problems++
        }
    }
}

Write-Output ''
Write-Output ("altar recipes={0} ourTags={1} problems={2}" -f $count, $ourTags.Count, $problems)
