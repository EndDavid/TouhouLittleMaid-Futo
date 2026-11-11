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
  'touhou_little_maid:tank_backpack'
)
$entities = @('touhou_little_maid:maid','touhou_little_maid:fairy','touhou_little_maid:power_point')

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

Write-Output ''
Write-Output ("entries={0} categories={1} problems={2}" -f (Get-ChildItem -Recurse -Filter *.json (Join-Path $book 'entries')).Count, $cats.Count, $problems)

