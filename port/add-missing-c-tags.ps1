# Create the 12 missing c: conventional item tags.
# ASCII-only on purpose: Windows PowerShell 5.1 parses BOM-less .ps1 as ANSI.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root 'src\main\resources\data\c\tags\items'
if (-not (Test-Path $dir)) { throw "tag dir not found: $dir" }

# tag name -> vanilla items (upstream forge:basis taken from the 1.18.2 datapack)
$tags = [ordered]@{
    'diamonds'       = @('minecraft:diamond')
    'emeralds'       = @('minecraft:emerald')
    'lapis'          = @('minecraft:lapis_lazuli')
    'redstone_dusts' = @('minecraft:redstone')
    'gold_ingots'    = @('minecraft:gold_ingot')
    'iron_ingots'    = @('minecraft:iron_ingot')
    'blue_dyes'      = @('minecraft:blue_dye')
    'cyan_dyes'      = @('minecraft:cyan_dye')
    'lime_dyes'      = @('minecraft:lime_dye')
    'orange_dyes'    = @('minecraft:orange_dye')
    'red_dyes'       = @('minecraft:red_dye')
    'yellow_dyes'    = @('minecraft:yellow_dye')
}

$crlf = "`r`n"
foreach ($name in $tags.Keys) {
    $items = $tags[$name]
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('{')
    $lines.Add('  "replace": false,')
    $lines.Add('  "values": [')
    for ($i = 0; $i -lt $items.Count; $i++) {
        $sep = if ($i -eq $items.Count - 1) { '' } else { ',' }
        $lines.Add('    "' + $items[$i] + '"' + $sep)
    }
    $lines.Add('  ]')
    $lines.Add('}')
    $text = ($lines -join $crlf) + $crlf

    $path = Join-Path $dir ($name + '.json')
    if (Test-Path $path) { throw "already exists (refusing to overwrite): $path" }
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output ("created " + $name + '.json (' + $items.Count + ' item(s))')
}

# ---- verification -------------------------------------------------------
$missing = @('diamonds','emeralds','lapis','redstone_dusts','gold_ingots','iron_ingots',
             'blue_dyes','cyan_dyes','lime_dyes','orange_dyes','red_dyes','yellow_dyes')
foreach ($m in $missing) {
    $p = Join-Path $dir ($m + '.json')
    if (-not (Test-Path $p)) { throw "verify failed, missing $p" }
    $raw = Get-Content $p -Raw
    $parsed = $raw | ConvertFrom-Json
    if ($parsed.replace -ne $false) { throw "verify failed, replace != false in $p" }
    if ($parsed.values.Count -lt 1) { throw "verify failed, no values in $p" }
}
Write-Output 'verify: 12 tags present, all parse, all have values'
