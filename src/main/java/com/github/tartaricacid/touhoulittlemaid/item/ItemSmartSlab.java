package com.github.tartaricacid.touhoulittlemaid.item;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.util.PlaceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;
/**
 * 魂符，移植自上游 1.20.1 的 {@code item/ItemSmartSlab}。
 *
 * <p>一件物品承载三种状态（上游用同一个类的 {@code Type} 区分）：</p>
 * <ul>
 *     <li>{@link Type#INIT} —— 首次进服送的「初始魂符」，右击地面会现场生成一只全新女仆并自动认主；</li>
 *     <li>{@link Type#EMPTY} —— 空魂符，右击自己的女仆把她收进去（她会被 {@code discard}）；</li>
 *     <li>{@link Type#HAS_MAID} —— 已经装着女仆的魂符，右击地面把那只女仆放出来。</li>
 * </ul>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li><b>没有 {@code MaidAndItemTransformEvent}。</b>那是给附属模组改数据的 Forge 事件；
 *         本移植版没有扩展注册表，写了也是空壳。</li>
 *     <li><b>女仆数量上限改用按需统计。</b>上游读玩家 capability，
 *         本移植版与驯服路径共用 {@code EntityMaid#countOwnedMaid}（见 {@link com.github.tartaricacid.touhoulittlemaid.capability.MaidNumCapability}）。</li>
 *     <li><b>「收女仆」不挂在 {@code InteractMaidEvent} 上，而是 Item 自己的
 *         {@code interactLivingEntity}。</b>Fabric 1.18.2 里这个钩子本来就有，
 *         且相机 {@code ItemCamera} 也是这么做的，语义一致、少一层事件。</li>
 *     <li><b>提示文本不用 {@code I18n}。</b>那是客户端 API，放在 common 包会连服务端一起加载；
 *         这里改成只在客户端才去查名字（上游同样标了 {@code @OnlyIn(Dist.CLIENT)}）。</li>
 * </ol>
 */
public class ItemSmartSlab extends AbstractStoreMaidItem {
    private static final String MAID_OWNER = "Owner";
    /**
     * 初始主人锁定标记：有它时 {@link Type#INIT} 只允许该 UUID 使用。
     *
     * <p>默认没有这个键（等价的 {@code Util.NIL_UUID} 表示不限制）。
     * 发放路径由战利品函数 {@code set_init_maid_owner} 写入。</p>
     */
    private static final String INIT_MAID_OWNER = "InitMaidOwner";
    private final Type type;
    public ItemSmartSlab(Type type) {
        super(settings(type));
        this.type = type;
    }

    /**
     * 只有「初始魂符」进创造模式物品栏 —— 与上游一致
     * （{@code InitCreativeTabs} 里只 {@code accept(SMART_SLAB_INIT)}）。
     * 另外两个是初始魂符用完之后的中间态，玩家不需要从物品栏里直接拿。
     */
    private static net.fabricmc.fabric.api.item.v1.FabricItemSettings settings(Type type) {
        net.fabricmc.fabric.api.item.v1.FabricItemSettings settings =
                new net.fabricmc.fabric.api.item.v1.FabricItemSettings();
        settings.stacksTo(1).rarity(Rarity.RARE);
        // 上游靠 Forge 的 onEntityItemUpdate 让掉落物发光 + 无敌；见 AbstractStoreMaidItem
        settings.fireResistant();
        if (type == Type.INIT) {
            settings.tab(InitItems.MAIN_TAB);
        }
        return settings;
    }
    public Type getType() {
        return this.type;
    }
    /**
     * 把一只女仆的完整数据存进魂符。
     */
    public static void storeMaidData(ItemStack stack, EntityMaid maid) {
        CompoundTag data = stack.getOrCreateTagElement(MAID_INFO);
        maid.saveWithoutId(data);
    }
    public static UUID getInitMaidOwner(ItemStack stack) {
        CompoundTag data = stack.getOrCreateTag();
        if (data.contains(INIT_MAID_OWNER, Tag.TAG_INT_ARRAY)) {
            return data.getUUID(INIT_MAID_OWNER);
        }
        return Util.NIL_UUID;
    }
    /**
     * 只有 {@link Type#INIT} 才允许锁主人，其它类型直接返回 {@code false}。
     */
    public static boolean setInitMaidOwner(ItemStack stack, UUID ownerUid) {
        if (stack.getItem() instanceof ItemSmartSlab smartSlab && smartSlab.type == Type.INIT) {
            CompoundTag data = stack.getOrCreateTag();
            data.putUUID(INIT_MAID_OWNER, ownerUid);
            return true;
        }
        return false;
    }
    @Override
    public String getDescriptionId() {
        return "item.touhou_little_maid.smart_slab";
    }
    // ------------------------------------------------------------------ 右击方块：放置
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction clickedFace = context.getClickedFace();
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        if (player == null) {
            return super.useOn(context);
        }
        if (clickedFace != Direction.UP || PlaceHelper.notSuitableForPlaceMaid(level, clickedPos)) {
            if (level.isClientSide) {
                player.displayClientMessage(new TranslatableComponent(
                        "message.touhou_little_maid.photo.not_suitable_for_place_maid"), true);
            }
            return super.useOn(context);
        }
        if (this.type == Type.INIT) {
            // 有锁定则只允许被锁定的那位玩家使用
            UUID initOwnerUid = getInitMaidOwner(context.getItemInHand());
            if (!initOwnerUid.equals(Util.NIL_UUID) && !player.getUUID().equals(initOwnerUid)) {
                if (!level.isClientSide) {
                    player.displayClientMessage(new TranslatableComponent(
                            "tooltips.touhou_little_maid.smart_slab.not_your_maid")
                            .withStyle(ChatFormatting.DARK_RED), true);
                }
                return InteractionResult.FAIL;
            }
            return this.spawnNewMaid(context, player, level);
        }
        if (this.type == Type.HAS_MAID) {
            return this.spawnFromStore(context, player, level);
        }
        return super.useOn(context);
    }
    /**
     * 从魂符里放出一只已经存好的女仆。
     */
    private InteractionResult spawnFromStore(UseOnContext context, Player player, Level level) {
        ItemStack stack = context.getItemInHand();
        if (!hasMaidData(stack)) {
            return super.useOn(context);
        }
        CompoundTag maidData = getMaidData(stack);
        if (!player.getUUID().equals(readOwner(maidData))) {
            if (!level.isClientSide) {
                player.displayClientMessage(new TranslatableComponent(
                        "tooltips.touhou_little_maid.smart_slab.not_your_maid")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            // 客户端只负责让手臂摆一下，实体创建全在服务端
            return InteractionResult.sidedSuccess(true);
        }
        EntityMaid maid = InitEntities.MAID.create(serverLevel);
        if (maid == null) {
            return super.useOn(context);
        }
        maid.load(maidData);
        maid.moveTo(context.getClickedPos().above(), 0, 0);
        serverLevel.addFreshEntity(maid);
        maid.spawnExplosionParticle();
        maid.playSound(SoundEvents.PLAYER_SPLASH, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
        this.consumeToEmpty(context, player);
        return InteractionResult.sidedSuccess(false);
    }
    /**
     * 读出魂符里那只女仆的主人 UUID。
     *
     * <p>主键是原版 {@code TamableAnimal} 写的 {@code Owner}；万一那份数据来自更旧的存档
     * （或玩家在别处编辑过 NBT），回退到本移植版自己的 {@code FutoOwner}。
     * 两处都没有时返回 {@link Util#NIL_UUID}，于是「不是你的女仆」这条判定照常成立 ——
     * 宁可拒绝，也不要把别人的女仆放出来。</p>
     */
    private static UUID readOwner(CompoundTag maidData) {
        if (maidData.hasUUID("Owner")) {
            return maidData.getUUID("Owner");
        }
        if (maidData.hasUUID("FutoOwner")) {
            return maidData.getUUID("FutoOwner");
        }
        return Util.NIL_UUID;
    }

    /**
     * 现场生成一只全新的、已认主的女仆（{@link Type#INIT} 的用法）。
     */
    private InteractionResult spawnNewMaid(UseOnContext context, Player player, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!player.isCreative() && !this.canAddMaid(player, serverLevel)) {
            player.displayClientMessage(new TranslatableComponent(
                    "message.touhou_little_maid.owner_maid_num.can_not_add",
                    countOwnedMaid(player, serverLevel),
                    com.github.tartaricacid.touhoulittlemaid.capability.MaidNumCapability.DEFAULT_MAX_NUM), true);
            return super.useOn(context);
        }
        EntityMaid maid = InitEntities.MAID.create(serverLevel);
        if (maid == null) {
            return super.useOn(context);
        }
        maid.tame(player);
        maid.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(context.getClickedPos()),
                MobSpawnType.SPAWN_EGG, null, null);
        maid.moveTo(context.getClickedPos().above(), 0, 0);
        serverLevel.addFreshEntity(maid);
        maid.spawnExplosionParticle();
        maid.playSound(SoundEvents.PLAYER_SPLASH, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
        this.consumeToEmpty(context, player);
        return InteractionResult.sidedSuccess(false);
    }
    /**
     * 用完之后换成一个空魂符，并给 1 秒冷却。
     *
     * <p>冷却挡的是「一次右击被两端各处理一遍」时凭空多出一个工具：
     * 服务端已经换成 {@code EMPTY}，客户端那份旧栈再走到这里时会因为物品类型不符而被跳过。</p>
     */
    private void consumeToEmpty(UseOnContext context, Player player) {
        ItemStack empty = new ItemStack(InitItems.SMART_SLAB_EMPTY);
        player.setItemInHand(context.getHand(), empty);
        player.getCooldowns().addCooldown(InitItems.SMART_SLAB_EMPTY, 20);
    }
    private boolean canAddMaid(Player player, Level level) {
        return new com.github.tartaricacid.touhoulittlemaid.capability.MaidNumCapability()
                .canAdd(countOwnedMaid(player, level));
    }
    private static int countOwnedMaid(Player player, Level level) {
        return level.getEntitiesOfClass(EntityMaid.class,
                new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(256),
                maid -> maid.isTame() && maid.isOwnedBy(player)).size();
    }
    // ------------------------------------------------------------------ 右击女仆：收起
    /**
     * 空魂符右击自己的女仆 → 把她收进魂符（上游 {@code SlabClickEvent}）。
     *
     * <p>收进去的女仆会被 {@code discard} —— 数据已经完整存在魂符里，
     * 留着实体只会变成两只。这也是上游「搬运女仆」的正规途径。</p>
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (this.type != Type.EMPTY || !(target instanceof EntityMaid maid)) {
            return super.interactLivingEntity(stack, player, target, hand);
        }
        if (player.level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!maid.isOwnedByPlayer(player)) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(InitItems.SMART_SLAB_EMPTY)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack output = new ItemStack(InitItems.SMART_SLAB_HAS_MAID);
        maid.setHomeModeEnable(false);
        storeMaidData(output, maid);
        maid.spawnExplosionParticle();
        maid.discard();
        maid.playSound(SoundEvents.PLAYER_SPLASH, 1.0F, player.level.random.nextFloat() * 0.1F + 0.9F);
        player.setItemInHand(hand, output);
        player.getCooldowns().addCooldown(InitItems.SMART_SLAB_HAS_MAID, 20);
        return InteractionResult.SUCCESS;
    }
    // ------------------------------------------------------------------ 杂项
    /**
     * 只有空魂符没有附魔光效；带着女仆或初始魂符都会闪光。
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return this.type != Type.EMPTY;
    }
    @Override
    public boolean canFitInsideContainerItems() {
        return this.type != Type.HAS_MAID;
    }
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (this.type == Type.INIT) {
            // 「未知」本身也是一条本地化键，直接当参数传进去：
            // TranslatableComponent 的参数允许是另一个 Component，渲染时各自取本地化文本。
            // 上游这里用客户端专属的 I18n.get(...)，本移植版放在 common 包，不能引用它。
            MutableComponent text = new TranslatableComponent(
                    "tooltips.touhou_little_maid.smart_slab.maid_name",
                    new TranslatableComponent("tooltips.touhou_little_maid.smart_slab.maid_name.unknown"));
            tooltip.add(text.withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.smart_slab.desc")
                .withStyle(ChatFormatting.GRAY));
    }
    /**
     * 只有「装着女仆」的魂符才画三维预览（数据来自 {@link AbstractStoreMaidItem#getTooltipImage}）。
     */
    @Override
    public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
        if (this.type == Type.HAS_MAID) {
            return super.getTooltipImage(stack);
        }
        return java.util.Optional.empty();
    }
    public enum Type {
        /**
         * 首次进服送的初始魂符：现场生成一只新女仆。
         */
        INIT,
        /**
         * 空魂符：右击女仆把她收进去。
         */
        EMPTY,
        /**
         * 存着女仆的魂符：右击地面放出她。
         */
        HAS_MAID
    }
}
