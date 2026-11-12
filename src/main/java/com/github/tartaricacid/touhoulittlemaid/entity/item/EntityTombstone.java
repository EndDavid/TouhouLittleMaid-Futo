package com.github.tartaricacid.touhoulittlemaid.entity.item;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 女仆墓碑，移植自上游 {@code entity/item/EntityTombstone}。
 *
 * <p>女仆死亡时，她身上所有东西（护甲 / 主副手 / 物品栏 / 饰品 / 背包 / 隐藏栏）连同那张
 * 胶片一起塞进这个实体，而不是散一地。墓碑只认主人（或手里拿着 NTR 物品的管理员），
 * 点一下全部收回；拿别人的墓碑点只会收到两句提示。</p>
 *
 * <h2>它为什么重写了那么多「什么都不做」的方法</h2>
 * <p>墓碑是<b>不动、不疼、不推、不撞</b>的装饰实体：{@code move} / {@code push} / {@code hurt} /
 * {@code thunderHit} / {@code refreshDimensions} 全部清空，{@code canCollideWith} 返回 false，
 * 避免被水流冲走、被活塞推、被雷劈、被碰撞箱顶起来。{@code repositionEntityAfterLoad}
 * 返回 false 是为了让读档后不要按碰撞箱重新落地 —— 否则悬在空中的墓碑会掉下去。</p>
 *
 * <h2>与上游的差异（两处，都在调用点写了原因）</h2>
 * <ol>
 *     <li>{@code NetworkHooks.getEntitySpawningPacket} 换成原版的
 *         {@link ClientboundAddEntityPacket}（Forge 那个只是包了一层同名的原版包）。</li>
 *     <li>上游的 NTR 物品是 {@code Ingredient}，可以直接 {@code getItems()} 拿到展示用物品；
 *         本移植版的 {@code EntityMaid#getNtrItem()} 返回 {@code Predicate<ItemStack>}
 *         （配置里是单个带 {@code #} 前缀的标签 / 物品 id），所以这里用
 *         {@link #getNtrItemStack()} 把同一个配置解析成物品来显示。</li>
 * </ol>
 */
public class EntityTombstone extends Entity {
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final String TOMBSTONE_ITEMS_TAG = "TombstoneItems";
    private static final String MAID_NAME_TAG = "MaidName";
    private static final EntityDataAccessor<Component> MAID_NAME =
            SynchedEntityData.defineId(EntityTombstone.class, EntityDataSerializers.COMPONENT);

    /**
     * 上游同样是 72 格：36 物品栏 + 9 饰品 + 4 护甲 + 2 手 + 1 隐藏 + 若干背包扩展。
     */
    private final ItemStackHandler items = new ItemStackHandler(72);
    private UUID ownerId = Util.NIL_UUID;

    public EntityTombstone(EntityType<?> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
    }

    public EntityTombstone(Level worldIn, UUID ownerId, Vec3 pos) {
        this(InitEntities.TOMBSTONE, worldIn);
        this.ownerId = ownerId;
        this.setPos(pos);
    }

    public void insertItem(ItemStack item) {
        if (!item.isEmpty()) {
            ItemHandlerHelper.insertItemStacked(this.items, item, false);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        // NTR 工具可以收回墓碑（服务器管理员用）
        if (player.getUUID().equals(this.ownerId) || EntityMaid.getNtrItem().test(itemInHand)) {
            for (int i = 0; i < this.items.getSlots(); i++) {
                int size = this.items.getSlotLimit(i);
                ItemStack extractItem = this.items.extractItem(i, size, false);
                ItemHandlerHelper.giveItemToPlayer(player, extractItem);
            }
            this.discard();
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!player.level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            Component displayName = getNtrItemStack().getDisplayName();
            player.sendMessage(new TranslatableComponent(
                    "message.touhou_little_maid.tombstone.not_yours.1"), Util.NIL_UUID);
            player.sendMessage(new TranslatableComponent(
                    "message.touhou_little_maid.tombstone.not_yours.2").append(displayName), Util.NIL_UUID);
        }
        return super.interact(player, hand);
    }

    /**
     * 配置里的 NTR 物品，解析成 {@link ItemStack} 只为显示那句「当前 NTR 物品：xxx」。
     *
     * <p>配置支持两种写法，与 {@code EntityMaid#getNtrItem()} 一致：
     * {@code #namespace:tag} 或直接的物品 id。标签取第一个物品；解析不出来时回退到
     * 结构空位（也就是配置默认值），这样提示永远不会是空的。</p>
     */
    private static ItemStack getNtrItemStack() {
        String config = MaidConfig.ntrItem();
        if (config == null || config.isEmpty()) {
            return new ItemStack(Items.STRUCTURE_VOID);
        }
        try {
            if (config.startsWith("#")) {
                var tag = net.minecraft.tags.TagKey.create(Registry.ITEM_REGISTRY,
                        new ResourceLocation(config.substring(1)));
                // 1.18.2 的 Named 迭代出来的是 Holder<Item>，不是 Item
                var named = Registry.ITEM.getTag(tag);
                if (named.isPresent()) {
                    for (var holder : named.get()) {
                        return new ItemStack(holder.value());
                    }
                }
                return new ItemStack(Items.STRUCTURE_VOID);
            }
            Item item = Registry.ITEM.get(new ResourceLocation(config));
            return new ItemStack(item);
        } catch (Exception e) {
            return new ItemStack(Items.STRUCTURE_VOID);
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(MAID_NAME, TextComponent.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(OWNER_ID_TAG)) {
            this.ownerId = tag.getUUID(OWNER_ID_TAG);
        }
        if (tag.contains(TOMBSTONE_ITEMS_TAG)) {
            this.items.deserializeNBT(tag.getCompound(TOMBSTONE_ITEMS_TAG));
        }
        if (tag.contains(MAID_NAME_TAG)) {
            this.setMaidName(Component.Serializer.fromJson(tag.getString(MAID_NAME_TAG)));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putUUID(OWNER_ID_TAG, this.ownerId);
        tag.put(TOMBSTONE_ITEMS_TAG, this.items.serializeNBT());
        tag.putString(MAID_NAME_TAG, Component.Serializer.toJson(this.getMaidName()));
    }

    @Override
    public void tick() {
        if (!this.level.isClientSide) {
            this.checkOutOfWorld();
        }
    }

    @Override
    public boolean skipAttackInteraction(Entity pEntity) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        return false;
    }

    @Override
    public void move(MoverType pType, Vec3 pPos) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double pX, double pY, double pZ) {
    }

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    @Override
    public void thunderHit(ServerLevel pLevel, LightningBolt pLightning) {
    }

    @Override
    public void refreshDimensions() {
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) {
            MaidWorldData maidWorldData = MaidWorldData.get(level);
            if (maidWorldData != null) {
                maidWorldData.removeTombstones(this);
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return this.isAlive();
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Component getMaidName() {
        return this.entityData.get(MAID_NAME);
    }

    public void setMaidName(@Nullable Component name) {
        if (name != null) {
            this.entityData.set(MAID_NAME, name);
        }
    }

    public ItemStackHandler getItems() {
        return items;
    }

    /**
     * 供 {@code InitEntities} 使用：上游把 {@code TYPE} 写在实体类里，本移植版统一放在
     * 注册表类，这里保留一个尺寸/类别的常量入口，避免两处数字不一致。
     */
    public static EntityType<EntityTombstone> createType() {
        return EntityType.Builder.<EntityTombstone>of(EntityTombstone::new, MobCategory.MISC)
                .sized(0.8F, 1.2F)
                .clientTrackingRange(10)
                .build(com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid.MOD_ID + ":tombstone");
    }
}
