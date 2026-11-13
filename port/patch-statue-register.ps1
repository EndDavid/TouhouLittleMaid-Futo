# Register the statue / garage-kit milestone: blocks, block entities, items, the
# creative tab, EntityMaid's model-id tag + render state, and the extra client
# entrypoint.
#
# ASCII-only (PowerShell 5.1 reads a BOM-less .ps1 as ANSI). Every anchor below is
# ASCII and asserted unique before use; all edits are pure insertions so the diff
# cannot silently drop neighbouring code.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Read-Utf8([string]$p) {
    $t = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
    if ($t.Length -gt 0 -and [int]$t[0] -eq 0xFEFF) { $t = $t.Substring(1) }
    return $t
}

# normalise to \n, remember whether the file was CRLF, write back the same way
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

function Count-Of([string]$t, [string]$n) { return ([regex]::Matches($t, [regex]::Escape($n))).Count }

function Insert-After([string]$path, [string]$anchor, [string]$insert, [string]$label) {
    $t = Get-Text $path
    if ((Count-Of $t $anchor) -ne 1) { throw "$label : anchor count $((Count-Of $t $anchor)) in $path" }
    $i = $t.IndexOf($anchor) + $anchor.Length
    $t = $t.Substring(0, $i) + "`n" + $insert + $t.Substring($i)
    Save-Text $path $t
    Write-Output ("  ok  " + $label)
}

function Insert-BeforeLine([string]$path, [string]$anchor, [string]$insert, [string]$label) {
    $t = Get-Text $path
    if ((Count-Of $t $anchor) -ne 1) { throw "$label : anchor count $((Count-Of $t $anchor)) in $path" }
    $i = $t.IndexOf($anchor)
    $t = $t.Substring(0, $i) + $insert + "`n" + $t.Substring($i)
    Save-Text $path $t
    Write-Output ("  ok  " + $label)
}

$java = Join-Path $root 'src\main\java\com\github\tartaricacid\touhoulittlemaid'
$initBlocks = Join-Path $java 'init\InitBlocks.java'
$initItems = Join-Path $java 'init\InitItems.java'
$entityMaid = Join-Path $java 'entity\passive\EntityMaid.java'
$modJson = Join-Path $root 'src\main\resources\fabric.mod.json'

Write-Output '--- 1. fabric.mod.json : second client entrypoint ---'
$t = Get-Text $modJson
$anchor = '"com.github.tartaricacid.touhoulittlemaid.client.TouhouLittleMaidFutoClient"'
if ((Count-Of $t $anchor) -ne 1) { throw 'client entrypoint anchor not unique' }
$tab = [string][char]9
$replacement = '"com.github.tartaricacid.touhoulittlemaid.client.TouhouLittleMaidFutoClient",' + "`n" +
               $tab + $tab + $tab + '"com.github.tartaricacid.touhoulittlemaid.client.init.InitStatueClient"'
$t = $t.Replace($anchor, $replacement)
Save-Text $modJson $t
Write-Output '  ok  client entrypoint'

Write-Output '--- 2. InitBlocks : statue + garage kit ---'
$blocks = @'
    // ------------------------------------------------------------------ 雕像 / 手办

    /**
     * 雕像方块。一座雕像由 N 个本方块拼成（1x1x1 / 1x2x1 / 2x4x2 / 3x6x3），
     * 由 {@code ItemChisel} 凿出来，拆任意一格整座还原成粘土。
     *
     * <p><b>没有物品形态</b>：玩家拿不到「雕像方块」，只能拿凿子凿出来。
     * 手办烧/挖之后掉的是粘土，不是它。</p>
     */
    public static final Block STATUE = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("statue"), new BlockStatue());

    public static final BlockEntityType<TileEntityStatue> STATUE_TE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("statue"),
            FabricBlockEntityTypeBuilder.create(TileEntityStatue::new, STATUE).build());

    /**
     * 手办方块。单格，方块实体里存一份实体 NBT，物品形态见 {@code InitItems.GARAGE_KIT}。
     */
    public static final Block GARAGE_KIT = Registry.register(Registry.BLOCK,
            TouhouLittleMaid.rl("garage_kit"), new BlockGarageKit());

    public static final BlockEntityType<TileEntityGarageKit> GARAGE_KIT_TE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE, TouhouLittleMaid.rl("garage_kit"),
            FabricBlockEntityTypeBuilder.create(TileEntityGarageKit::new, GARAGE_KIT).build());

'@
$blocks = $blocks -replace "`r`n", "`n"
Insert-BeforeLine $initBlocks '    private InitBlocks() {' $blocks 'InitBlocks fields'

Write-Output '--- 3. InitBlocks : imports ---'
Insert-After $initBlocks 'import com.github.tartaricacid.touhoulittlemaid.block.BlockAltar;' `
    'import com.github.tartaricacid.touhoulittlemaid.block.BlockGarageKit;' 'InitBlocks import garage kit'
Insert-After $initBlocks 'import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBeacon;' `
    'import com.github.tartaricacid.touhoulittlemaid.block.BlockStatue;' 'InitBlocks import statue'
Insert-After $initBlocks 'import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;' `
    'import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;' 'InitBlocks import te garage kit'
Insert-After $initBlocks 'import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;' `
    'import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;' 'InitBlocks import te statue'

Write-Output '--- 4. InitItems : garage kit creative tab ---'
$tabSrc = @'

    /**
     * 手办专用创造模式物品栏（上游 {@code MaidGroup.GARAGE_KIT_TAB}）。
     *
     * <p>每个女仆模型一只，几十只塞进主物品栏会把别的物品挤没，所以上游单开一栏。
     * 内容由 {@code BlockGarageKit#fillItemCategory} 铺（它要读模型注册表，
     * 所以下面这一遍 fillItemCategory 就是全部内容，不需要额外追加）。</p>
     */
    public static final CreativeModeTab GARAGE_KIT_TAB = FabricItemGroupBuilder
            .create(TouhouLittleMaid.rl("garage_kit"))
            .icon(() -> new ItemStack(InitItems.GARAGE_KIT))
            .appendItems((items, tab) -> {
                NonNullList<ItemStack> defaults = NonNullList.create();
                for (Item item : Registry.ITEM) {
                    item.fillItemCategory(tab, defaults);
                }
                items.addAll(defaults);
            })
            .build();
'@
$tabSrc = $tabSrc -replace "`r`n", "`n"
# anchor: the CHAIR_TAB declaration, then append after its .build();
$t = Get-Text $initItems
$chairAnchor = 'TouhouLittleMaid.rl("chair"))'
if ((Count-Of $t $chairAnchor) -ne 1) { throw 'CHAIR_TAB anchor not unique' }
$buildIdx = $t.IndexOf('.build();', $t.IndexOf($chairAnchor))
if ($buildIdx -lt 0) { throw 'could not find CHAIR_TAB .build()' }
$cut = $buildIdx + '.build();'.Length
$t = $t.Substring(0, $cut) + "`n" + $tabSrc + $t.Substring($cut)
Save-Text $initItems $t
Write-Output '  ok  GARAGE_KIT_TAB'

Write-Output '--- 5. InitItems : chisel + garage kit items ---'
$itemsSrc = @'
    /**
     * 凿子（上游 {@code InitItems.CHISEL}）：副手拿照片、对着粘土右键凿出雕像。
     * 64 耐久，按雕像尺寸扣 1~4 点。
     */
    public static final Item CHISEL = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("chisel"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemChisel());

    /**
     * 手办（上游 {@code InitItems.GARAGE_KIT}）。NBT 里装着一整只女仆，
     * 所以是 {@code ItemGarageKit} 而不是普通 {@code BlockItem}。
     */
    public static final Item GARAGE_KIT = Registry.register(Registry.ITEM,
            TouhouLittleMaid.rl("garage_kit"),
            new com.github.tartaricacid.touhoulittlemaid.item.ItemGarageKit());

'@
$itemsSrc = $itemsSrc -replace "`r`n", "`n"
Insert-After $initItems 'new com.github.tartaricacid.touhoulittlemaid.item.ItemFavorabilityTool("full"));' `
    $itemsSrc 'InitItems chisel + garage kit'

Write-Output '--- 6. EntityMaid : MODEL_ID_TAG + renderState ---'
$maidSrc = @'

    /**
     * 女仆 NBT 里「模型 id」的键名。
     *
     * <p>同步数据用的是 {@code DATA_MODEL_ID}，但落盘 / 物品 NBT（胶片的
     * {@code MaidInfo}、手办的 {@code EntityInfo}）里用的都是这个字符串键。
     * 上游把它暴露成 {@code EntityMaid.MODEL_ID_TAG}，雕像 / 手办 / 胶片都引它，
     * 所以这里也必须是 public 常量，不能各写各的字面量。</p>
     */
    public static final String MODEL_ID_TAG = "ModelId";

    /**
     * 渲染状态，默认 {@link MaidRenderState#ENTITY}。
     *
     * <p>雕像与手办把实体 NBT 读进一个不参与 tick 的临时实体来渲染，
     * 读到的是「快照」而不是本体；置成 {@code STATUE} / {@code GARAGE_KIT}
     * 让渲染链路上需要区分的地方有据可依，同时也是给附属模组留的公开字段。</p>
     */
    public com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState renderState =
            com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState.ENTITY;
'@
$maidSrc = $maidSrc -replace "`r`n", "`n"
Insert-After $entityMaid '    public static final String DEFAULT_MODEL_ID = "touhou_little_maid:hakurei_reimu";' `
    $maidSrc 'EntityMaid MODEL_ID_TAG + renderState'

Write-Output 'patch script done'

