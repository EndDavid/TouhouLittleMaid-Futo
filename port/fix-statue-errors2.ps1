# Continuation of fix-statue-errors.ps1: the first signature replacement already ran,
# the rest did not (the here-string patterns carried CRLF while the text is \n-normalised).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Read-Utf8([string]$p) {
    $t = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
    if ($t.Length -gt 0 -and [int]$t[0] -eq 0xFEFF) { $t = $t.Substring(1) }
    return $t
}
function Get-Text([string]$p) {
    $raw = Read-Utf8 $p
    $script:wasCrlf = $raw.Contains("`r`n")
    return ($raw -replace "`r`n", "`n")
}
function Save-Text([string]$p, [string]$t) {
    if ($t.Contains([string][char]0xFFFD)) { throw "U+FFFD in result: $p" }
    if ($script:wasCrlf) { $t = $t -replace "`n", "`r`n" }
    [System.IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))
}
function N([string]$s) { return ($s -replace "`r`n", "`n") }
function Rep([string]$path, [string]$from, [string]$to, [string]$label) {
    $t = Get-Text $path
    $n = ([regex]::Matches($t, [regex]::Escape($from))).Count
    if ($n -ne 1) { throw "$label : matched $n times (expected 1)" }
    Save-Text $path $t.Replace($from, $to)
    Write-Output ("  ok  " + $label)
}
function RepAll([string]$path, [string]$from, [string]$to, [string]$label) {
    $t = Get-Text $path
    $n = ([regex]::Matches($t, [regex]::Escape($from))).Count
    if ($n -lt 1) { throw "$label : matched 0 times" }
    Save-Text $path $t.Replace($from, $to)
    Write-Output ("  ok  " + $label + " (x" + $n + ")")
}

$java = Join-Path $root 'src\main\java\com\github\tartaricacid\touhoulittlemaid'
$garageBlock = Join-Path $java 'block\BlockGarageKit.java'
$statueBlock = Join-Path $java 'block\BlockStatue.java'
$statueTe = Join-Path $java 'tileentity\TileEntityStatue.java'
$cache = Join-Path $java 'util\EntityCacheUtil.java'
$statueRenderer = Join-Path $java 'client\renderer\tileentity\TileEntityStatueRenderer.java'
$garageRenderer = Join-Path $java 'client\renderer\tileentity\TileEntityGarageKitRenderer.java'
$itemRenderer = Join-Path $java 'client\renderer\item\GarageKitItemRenderer.java'

Write-Output '--- 1. garage kit: drop the orphaned parameter line ---'
Rep $garageBlock (N "                                       BlockPos pos, Player player) {`n") '' 'garage kit orphan param line'

Write-Output '--- 2. LevelEvent package ---'
Rep $statueBlock 'net.minecraft.world.level.LevelEvent.SOUND_EXTINGUISH_FIRE' `
    'net.minecraft.world.level.block.LevelEvent.SOUND_EXTINGUISH_FIRE' 'LevelEvent package'

Write-Output '--- 3. statue TE: renderer owns the culling box in 1.18.2 ---'
$from3 = "    @Override`n    public AABB getRenderBoundingBox() {"
$to3 = "    // 1.18.2 culls block entities through the RENDERER, not the block entity itself.`n" +
       "    // The real box lives in TileEntityStatueRenderer.`n" +
       "    public AABB renderBoundingBoxCompat() {"
Rep $statueTe $from3 $to3 'statue TE drop override + rename'

Write-Output '--- 4. EntityCacheUtil: cache Entity, add createMaidEntity ---'
RepAll $cache 'Cache<Long, EntityMaid> STATUE_CACHE' 'Cache<Long, Entity> STATUE_CACHE' 'cache type statue'
RepAll $cache 'Cache<ItemStack, EntityMaid> GARAGE_KIT_CACHE' 'Cache<ItemStack, Entity> GARAGE_KIT_CACHE' 'cache type garage kit'
$helper = N @'
    /**
     * Build a throwaway maid for rendering.
     *
     * <p>Upstream writes {@code new EntityMaid(world)}; this port's EntityMaid only has the
     * (EntityType, Level) constructor, so the entity has to come from the registered type.
     * It is never added to a level -- it exists only to be loaded with NBT and handed to the
     * EntityRenderDispatcher.</p>
     */
    public static Entity createMaidEntity(Level level) {
        Entity entity = com.github.tartaricacid.touhoulittlemaid.init.InitEntities.MAID.create(level);
        if (entity == null) {
            throw new IllegalStateException("InitEntities.MAID.create(level) returned null");
        }
        return entity;
    }

'@
Rep $cache '    public static void clearMaidDataResidue(' ($helper + '    public static void clearMaidDataResidue(') 'cache helper'

Write-Output '--- 5. renderers: build the maid through the type ---'
foreach ($f in @($statueRenderer, $garageRenderer, $itemRenderer)) {
    $t = Get-Text $f
    $t = $t.Replace('() -> new EntityMaid(world)', '() -> EntityCacheUtil.createMaidEntity(world)')
    $t = $t.Replace('() -> new EntityMaid(level)', '() -> EntityCacheUtil.createMaidEntity(level)')
    Save-Text $f $t
    Write-Output ("  ok  " + (Split-Path $f -Leaf))
}

Write-Output '--- 6. statue renderer: culling box ---'
$box = N @'
    /**
     * Culling box. A 3x6x3 statue reaches far outside its own block, so without this the
     * model pops out once the core block leaves the frustum.
     *
     * <p>This is the 1.18.2 mechanism: {@code BlockEntityRenderer#getRenderBoundingBox(T)}.
     * Upstream puts it on the block entity, which only works on Forge.</p>
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(TileEntityStatue te) {
        return new net.minecraft.world.phys.AABB(
                te.getBlockPos().offset(-5, -1, -5), te.getBlockPos().offset(5, 10, 5));
    }
}
'@
$tail = N "        poseStack.translate(0.5 / size, 0.5, 0.5 / size);`n    }`n}"
Rep $statueRenderer $tail (N "        poseStack.translate(0.5 / size, 0.5, 0.5 / size);`n    }`n`n" + $box) 'statue renderer culling box'
Write-Output 'fix2 done'
