$ErrorActionPreference = 'Stop'
$root = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$book = Join-Path $root 'src\main\resources\assets\touhou_little_maid\patchouli_books\memorizable_gensokyo\en_us'

$registered = @(
  'touhou_little_maid:maid_spawn_egg','touhou_little_maid:fairy_spawn_egg',
  'touhou_little_maid:extinguisher','touhou_little_maid:hakurei_gohei',
  'touhou_little_maid:sanae_gohei','touhou_little_maid:camera','touhou_little_maid:photo',
  'touhou_little_maid:maid_beacon','touhou_little_maid:power_point',
  'touhou_little_maid:maid_backpack_small','touhou_little_maid:maid_backpack_middle',
  'touhou_little_maid:maid_backpack_big','touhou_little_maid:crafting_table_backpack',
  'touhou_little_maid:ender_chest_backpack','touhou_little_maid:furnace_backpack',
  'touhou_little_maid:tank_backpack',
  # --- maid baubles (registered by InitItems) ---
  'touhou_little_maid:ultramarine_orb_elixir',
  'touhou_little_maid:explosion_protect_bauble',
  'touhou_little_maid:fire_protect_bauble',
  'touhou_little_maid:projectile_protect_bauble',
  'touhou_little_maid:magic_protect_bauble',
  'touhou_little_maid:fall_protect_bauble',
  'touhou_little_maid:drown_protect_bauble',
  'touhou_little_maid:nimble_fabric',
  'touhou_little_maid:item_magnet_bauble',
  'touhou_little_maid:mute_bauble',
  'touhou_little_maid:wireless_io',
  # --- shrine revival milestone: film / shrine / bed / jizo ---
  'touhou_little_maid:film',
  'touhou_little_maid:shrine',
  'touhou_little_maid:maid_bed',
  'touhou_little_maid:substitute_jizo',
  # --- chair milestone: chair / chair_show / scarecrow ---
  'touhou_little_maid:chair',
  'touhou_little_maid:chair_show',
  'touhou_little_maid:scarecrow',
  'touhou_little_maid:chisel',
  'touhou_little_maid:garage_kit',
  'touhou_little_maid:statue',
  'touhou_little_maid:trumpet',
  'touhou_little_maid:servant_bell',
  'touhou_little_maid:red_fox_scroll',
  'touhou_little_maid:white_fox_scroll',
  'touhou_little_maid:kappa_compass',
  'touhou_little_maid:cchess',
  'touhou_little_maid:gomoku',
  'touhou_little_maid:wchess',
  'touhou_little_maid:gomoku_board_state',
  'touhou_little_maid:cchess_board_state',
  'touhou_little_maid:wchess_board_state',
  'touhou_little_maid:keyboard',
  'touhou_little_maid:bookshelf',
  'touhou_little_maid:computer',
  'touhou_little_maid:broom',
  # --- favorability tools (creative-only debug items) ---
  'touhou_little_maid:favorability_tool_add',
  'touhou_little_maid:favorability_tool_reduce',
  'touhou_little_maid:favorability_tool_full'
)
$entities = @('touhou_little_maid:maid','touhou_little_maid:fairy','touhou_little_maid:power_point','touhou_little_maid:box')

function Get-Id([string]$s) {
  # "namespace:path{...}" or "namespace:path@meta" -> "namespace:path"
  $i = $s.IndexOfAny([char[]]('{','@'))
  if ($i -ge 0) { $s = $s.Substring(0, $i) }
  return $s.Trim()
}

$problems = 0
$cats = @{}
foreach ($f in Get-ChildItem -Filter *.json (Join-Path $book 'categories')) {
  $j = Get-Content $f.FullName -Raw | ConvertFrom-Json
  $cats['touhou_little_maid:' + $f.BaseName] = $true
  $id = Get-Id $j.icon
  if ($id -like 'touhou_little_maid:*' -and -not ($registered -contains $id)) {
    Write-Output ("CATEGORY ICON MISSING  {0} -> {1}" -f $f.BaseName, $j.icon)
    $problems++
  }
}

foreach ($f in Get-ChildItem -Recurse -Filter *.json (Join-Path $book 'entries')) {
  $rel = $f.FullName.Substring((Join-Path $book 'entries').Length + 1)
  $j = Get-Content $f.FullName -Raw | ConvertFrom-Json

  if (-not $cats.ContainsKey($j.category)) {
    Write-Output ("BAD CATEGORY  {0} -> {1}" -f $rel, $j.category)
    $problems++
  }

  $icon = Get-Id $j.icon
  if ($icon -like 'touhou_little_maid:*' -and -not ($registered -contains $icon)) {
    Write-Output ("ENTRY ICON MISSING  {0} -> {1}" -f $rel, $j.icon)
    $problems++
  }

  $n = 0
  foreach ($p in $j.pages) {
    if ($p.type -eq 'spotlight') {
      $items = $p.item
      if ($null -eq $items) { $items = $p.items }
      foreach ($it in ($items -split ',')) {
        $id = Get-Id $it
        if (-not ($registered -contains $id)) {
          Write-Output ("FATAL SPOTLIGHT ITEM MISSING  {0} [p{1}] -> {2}" -f $rel, $n, $it)
          $problems++
        }
      }
    }
    if ($p.type -eq 'entity') {
      $id = Get-Id $p.entity
      if (-not ($entities -contains $id)) {
        Write-Output ("ENTITY MISSING  {0} [p{1}] -> {2}" -f $rel, $n, $p.entity)
        $problems++
      }
    }
    $n++
  }
}

# templates must still parse
foreach ($f in Get-ChildItem -Filter *.json (Join-Path $book 'templates')) {
  $null = Get-Content $f.FullName -Raw | ConvertFrom-Json
}

# ---- altar recipes are part of the book: their pages are generated from data ----
# Every "altar_recipe" page names a recipe_id; the recipe itself decides the six
# ingredient slots, the power cost and the output. So a wrong/missing recipe file,
# a missing c: tag or an unported ingredient all show up as an empty page.
$dataRoot = Join-Path $root 'src\main\resources\data'
$altarDir = Join-Path $dataRoot 'touhou_little_maid\recipes\altar'
$tagDefined = @{}
foreach ($f in (Get-ChildItem -Recurse -Filter *.json (Join-Path $dataRoot 'c\tags\items'))) {
  $tagDefined['c:' + $f.BaseName] = $true
}
$recipeFiles = @{}
foreach ($f in (Get-ChildItem -Filter *.json $altarDir)) {
  $recipeFiles['touhou_little_maid:altar/' + $f.BaseName] = $f.FullName
}

function Test-Ingredient([string]$file, [string]$rel) {
  $j = Get-Content $file -Raw | ConvertFrom-Json
  if ($null -eq $j.ingredients) {
    Write-Output ("ALTAR RECIPE NO INGREDIENTS  {0}" -f $rel)
    $script:problems++
    return
  }
  foreach ($ing in $j.ingredients) {
    if ($ing.tag) {
      # c: is our own convention: if we do not ship the file, the tag is empty,
      # the Ingredient has zero items and the recipe can never match.
      if ($ing.tag -like 'c:*' -and -not $tagDefined.ContainsKey($ing.tag)) {
        Write-Output ("ALTAR EMPTY c: TAG  {0} -> {1}" -f $rel, $ing.tag)
        $script:problems++
      }
    } elseif ($ing.item -and $ing.item -like 'touhou_little_maid:*') {
      if (-not ($registered -contains $ing.item)) {
        Write-Output ("ALTAR INGREDIENT MISSING  {0} -> {1}" -f $rel, $ing.item)
        $script:problems++
      }
    }
  }
  if ($j.output -and $j.output.type -like 'touhou_little_maid:*') {
    if (-not ($entities -contains $j.output.type)) {
      Write-Output ("ALTAR OUTPUT ENTITY MISSING  {0} -> {1}" -f $rel, $j.output.type)
      $script:problems++
    }
  }
}

# page -> recipe link: a page whose recipe_id does not exist renders as an empty frame
foreach ($f in (Get-ChildItem -Recurse -Filter *.json (Join-Path $book 'entries'))) {
  $rel = $f.FullName.Substring((Join-Path $book 'entries').Length + 1)
  $j = Get-Content $f.FullName -Raw | ConvertFrom-Json
  $n = 0
  foreach ($p in $j.pages) {
    if ($p.type -like '*altar_recipe') {
      if (-not $p.recipe_id) {
        Write-Output ("ALTAR PAGE WITHOUT recipe_id  {0} [p{1}]" -f $rel, $n)
        $script:problems++
      } elseif (-not $recipeFiles.ContainsKey($p.recipe_id)) {
        Write-Output ("ALTAR RECIPE MISSING  {0} [p{1}] -> {2}" -f $rel, $n, $p.recipe_id)
        $script:problems++
      }
    }
    $n++
  }
}

foreach ($k in $recipeFiles.Keys) {
  Test-Ingredient $recipeFiles[$k] ($k -replace '^touhou_little_maid:','')
}

Write-Output ''
Write-Output ("entries={0} categories={1} problems={2}" -f (Get-ChildItem -Recurse -Filter *.json (Join-Path $book 'entries')).Count, $cats.Count, $problems)
