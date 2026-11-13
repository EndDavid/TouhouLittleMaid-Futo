# Fix the two bedrock block renderers:
#
#   1. drop the ZN(180) flip -- it was there for the vanilla EntityModel that these
#      renderers used before, whose vertex space is Y-down. The GeckoLib geometry
#      (see GeoCube) is Y-up, so keeping the flip renders the model upside down.
#      That is exactly the reported symptom (shrine inverted, altar inverted).
#
#   2. replace the hand-tuned vertical offsets (+1.5 for the shrine, -1.5 for the
#      altar) with "sit the model's lowest point on the block floor", computed from
#      GeoBlockRenderer.measureBounds. Those offsets existed to compensate the old
#      models' own origins; the new models have different origins, so any hard-coded
#      number is wrong by construction.
#
# ASCII only. Chinese replacement text comes from the data file.

$ErrorActionPreference = 'Stop'

$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$dir  = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\client\renderer\tileentity'
$log  = Join-Path $repo 'port\_flippatch.txt'
$utf8 = New-Object System.Text.UTF8Encoding($false)

# ---- (no external data file: every replacement below is inlined ASCII) ---
$sb = New-Object System.Text.StringBuilder

# ======================== shrine =========================================
$shrine = Join-Path $dir 'TileEntityShrineRenderer.java'
$src = [System.IO.File]::ReadAllText($shrine, [System.Text.Encoding]::UTF8)
$before = $src.Length

$oldShrineRender = @(
    '        poseStack.pushPose();',
    '        poseStack.translate(0.5, 1.5, 0.5);',
    '        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));',
    '        poseStack.mulPose(Vector3f.YN.rotationDegrees(180 - facing.get2DDataValue() * 90));',
    '        AnimatedGeoModel model = BedrockBlockModels.get("shrine");'
) -join "`r`n"

$newShrineRender = @(
    '        poseStack.pushPose();',
    '        AnimatedGeoModel model = BedrockBlockModels.get("shrine");',
    '        poseStack.translate(0.5, BedrockBlockModels.floorOffset("shrine"), 0.5);',
    '        poseStack.mulPose(Vector3f.YN.rotationDegrees(180 - facing.get2DDataValue() * 90));'
) -join "`r`n"

if (-not $src.Contains($oldShrineRender)) { throw 'shrine render block not found verbatim' }
$src = $src.Replace($oldShrineRender, $newShrineRender)
if ($src.Contains('rotationDegrees(180))')) { throw 'shrine still has a Z flip' }

[System.IO.File]::WriteAllText($shrine, $src, $utf8)
[void]$sb.AppendLine("shrine: $before -> $($src.Length)")

# ======================== altar ==========================================
$altar = Join-Path $dir 'TileEntityAltarRenderer.java'
$src = [System.IO.File]::ReadAllText($altar, [System.Text.Encoding]::UTF8)
$before = $src.Length

$oldAltarRender = @(
    '        if (te.isRender()) {',
    '            poseStack.pushPose();',
    '            this.setTranslateAndPose(te, poseStack);',
    '            poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));',
    '            AnimatedGeoModel model = BedrockBlockModels.get("altar");'
) -join "`r`n"

$newAltarRender = @(
    '        if (te.isRender()) {',
    '            poseStack.pushPose();',
    '            this.setTranslateAndPose(te, poseStack);',
    '            AnimatedGeoModel model = BedrockBlockModels.get("altar");',
    '            poseStack.translate(0, BedrockBlockModels.floorOffset("altar"), 0);'
) -join "`r`n"

if (-not $src.Contains($oldAltarRender)) { throw 'altar render block not found verbatim' }
$src = $src.Replace($oldAltarRender, $newAltarRender)

# the -1.5 in every direction table entry was compensation for the old model's origin
$before15 = ([regex]::Matches($src, ', -1\.5, ')).Count
if ($before15 -ne 4) { throw "expected 4 direction entries with -1.5, found $before15" }
$src = $src.Replace(', -1.5, ', ', 0, ')

[System.IO.File]::WriteAllText($altar, $src, $utf8)
[void]$sb.AppendLine("altar : $before -> $($src.Length)  (flipped Z removed, 4 y offsets -1.5 -> 0)")

# ---- verify -------------------------------------------------------------
foreach ($f in @($shrine, $altar)) {
    $t = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
    if ($t.Contains([char]0xFFFD)) { throw "replacement char in $(Split-Path $f -Leaf)" }
    $o = ([regex]::Matches($t, '\{')).Count
    $c = ([regex]::Matches($t, '\}')).Count
    if ($o -ne $c) { throw "brace mismatch in $(Split-Path $f -Leaf): $o/$c" }
    if (-not $t.Contains('floorOffset')) { throw "floorOffset missing in $(Split-Path $f -Leaf)" }
    [void]$sb.AppendLine("  verified $(Split-Path $f -Leaf) braces=$o/$c")
}

[System.IO.File]::WriteAllText($log, $sb.ToString(), $utf8)
Write-Host 'patched'

