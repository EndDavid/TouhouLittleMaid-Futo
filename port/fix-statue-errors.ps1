# Fix the 9 compile errors from the first statue/garage-kit pass.
# ASCII-only; exact-string replacements with count assertions.
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
function Replace-Once([string]$path, [string]$from, [string]$to, [string]$label) {
    $t = Get-Text $path
    $n = ([regex]::Matches($t, [regex]::Escape($from))).Count
    if ($n -ne 1) { throw "$label : matched $n times (expected 1)" }
    Save-Text $path $t.Replace($from, $to)
    Write-Output ("  ok  " + $label)
}
function Replace-All([string]$path, [string]$from, [string]$to, [string]$label) {
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

Write-Output '--- 1. getCloneItemStack: 1.18.2 takes (BlockGetter, BlockPos, BlockState) ---'
Replace-Once $garageBlock `
    'public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter world,' `
    'public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {' `
    'garage kit getCloneItemStack signature'
Replace-Once $garageBlock @'
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
                                       BlockPos pos, Player player) {
'@ @'
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
'@ 'garage kit getCloneItemStack body tail'

Write-Output '--- 2. LevelEvent lives in net.minecraft.world.level.block ---'
Replace-Once $statueBlock 'net.minecraft.world.level.LevelEvent.SOUND_EXTINGUISH_FIRE' `
    'net.minecraft.world.level.block.LevelEvent.SOUND_EXTINGUISH_FIRE' 'LevelEvent package'

Write-Output '--- 3. bounding box lives on the renderer in 1.18.2 ---'
Replace-Once $statueTe '    public AABB getRenderBoundingBox() {' `
    '    public AABB renderBoundingBoxCompat() {' 'statue TE method rename'
Replace-Once $statueTe ('    @Override' + "`n" + '    public AABB renderBoundingBoxCompat() {') `
    ('    // 1.18.2 culls block entities through BlockEntityRenderer#getRenderBoundingBox(T),' + "`n" +
     '    // not through the block entity itself. Kept only so the field/import stay meaningful;' + "`n" +
     '    // the real one is in TileEntityStatueRenderer.' + "`n" +
     '    public AABB renderBoundingBoxCompat() {') 'statue TE drop @Override'

Write-Output '--- 4. EntityMaid has no single-arg constructor: cache Entity, build via the type ---'
Replace-All $cache 'public static final Cache<Long, EntityMaid> STATUE_CACHE' `
    'public static final Cache<Long, Entity> STATUE_CACHE' 'cache type statue'
Replace-All $cache 'public static final Cache<ItemStack, EntityMaid> GARAGE_KIT_CACHE' `
    'public static final Cache<ItemStack, Entity> GARAGE_KIT_CACHE' 'cache type garage kit'
$helper = @'
    /**
     * Build a throwaway maid for rendering.
     *
     * <p>Upstream writes {@code new EntityMaid(world)}; this port's EntityMaid only has the
     * (EntityType, Level) constructor Mob adds, so the entity has to come from the registered
     * type. The result is never added to a level -- it exists only to be loaded with NBT and
     * handed to the EntityRenderDispatcher.</p>
     */
    public static Entity createMaidEntity(Level level) {
        Entity entity = com.github.tartaricacid.touhoulittlemaid.init.InitEntities.MAID.create(level);
        if (entity == null) {
            throw new IllegalStateException("InitEntities.MAID.create(level) returned null");
        }
        return entity;
    }

'@
$helper = $helper -replace "`r`n", "`n"
Replace-Once $cache '    /**
     * 把女仆身上的' ($helper + '    /**
     * 把女仆身上的') 'cache createMaidEntity helper'

foreach ($f in @($statueRenderer, $garageRenderer, $itemRenderer)) {
    $name = Split-Path $f -Leaf
    $t = Get-Text $f
    $t = $t.Replace('() -> new EntityMaid(world)', '() -> EntityCacheUtil.createMaidEntity(world)')
    $t = $t.Replace('() -> new EntityMaid(level)', '() -> EntityCacheUtil.createMaidEntity(level)')
    Save-Text $f $t
    Write-Output ("  ok  " + $name + " entity factory")
}

Write-Output '--- 5. statue renderer: real culling box ---'
$box = @'
    /**
     * Culling box. A 3x6x3 statue reaches far outside its own block, so without this the
     * model pops out of existence once the core block leaves the frustum.
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
$box = $box -replace "`r`n", "`n"
$tail = '        poseStack.translate(0.5 / size, 0.5, 0.5 / size);' + "`n" + '    }' + "`n" + '}'
Replace-Once $statueRenderer $tail ('        poseStack.translate(0.5 / size, 0.5, 0.5 / size);' + "`n" + '    }' + "`n`n" + $box) 'statue renderer culling box'

Write-Output 'fix script done'

