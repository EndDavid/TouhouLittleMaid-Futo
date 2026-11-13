package com.github.tartaricacid.touhoulittlemaid.util;

import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.concurrent.TimeUnit;

/**
 * 实体缓存，逐字移植自上游 {@code util/EntityCacheUtil}。
 *
 * <p>雕像 / 手办每帧都要「把一份实体 NBT 画出来」，如果每次都
 * {@code new EntityMaid(level)}，方块渲染的顶点量不变但对象分配量会爆炸
 * （一个 3×6×3 的大雕像每帧新建一次）。所以这里把实体缓存起来，
 * 每帧只需要 {@code entity.load(data)} 覆盖一次状态。</p>
 *
 * <h2>为什么女仆要单独两个缓存</h2>
 * <p>{@link #ENTITY_CACHE} 按 {@code EntityType} 缓存，一个类型只有一个实例。
 * 但女仆是 GeckoLib 模型：<b>同一个实体对象同一帧被画两次，后一次会覆盖前一次的
 * 骨骼状态</b>，表现就是一排雕像全部变成最后那只的样子。所以女仆按
 * 「方块坐标」({@link #STATUE_CACHE}) 与「物品栈」({@link #GARAGE_KIT_CACHE})
 * 分开缓存，保证每个雕像 / 每个手办物品各有一个独立实例。</p>
 *
 * <h2>与上游的差异</h2>
 * <p>上游用 {@code @SubscribeEvent EntityJoinWorldEvent} 在玩家换维度时清空
 * {@link #ENTITY_CACHE}（缓存里的实体还挂在旧 {@code Level} 上）。
 * Fabric 侧没有那个事件，改由渲染器每帧调用
 * {@link #onClientLevelChange(Level)} 做同样的检查 —— 语义一致，只是检查点从
 * 「玩家进世界」搬到了「画之前」，反而更及时。</p>
 */
public final class EntityCacheUtil {
    /**
     * 实体缓存，客户端会大量运用实体渲染，这个缓存可以减少重复创建实体带来的性能问题。
     */
    public static final Cache<EntityType<?>, Entity> ENTITY_CACHE =
            CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    /**
     * 女仆实体缓存，用于雕像。每个方块坐标一个实例，避免 GeckoLib 骨骼互相覆盖。
     */
    public static final Cache<Long, Entity> STATUE_CACHE =
            CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build();

    /**
     * 女仆实体缓存，用于物品形态的手办。每个物品栈一个实例，原因同上。
     */
    public static final Cache<ItemStack, Entity> GARAGE_KIT_CACHE =
            CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).build();

    private static ResourceKey<Level> dimAt;

    private EntityCacheUtil() {
    }

    /**
     * 把女仆身上的「瞬时状态」清干净，让她看起来像一尊静物。
     *
     * <p>不清会怎样：{@code entity.load(data)} 只还原存档字段，
     * 受伤闪红（{@code hurtTime}）、倒地计时（{@code deathTime}）、坐姿、
     * 头顶自定名、背包模型都还是上一个使用者的残留 —— 雕像会莫名其妙闪红或者
     * 显示别人的名字。</p>
     *
     * @param clearEquipmentData 是否连装备一起清空（手办要，否则手里会多出一把剑）
     */
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
    public static void clearMaidDataResidue(EntityMaid maid, boolean clearEquipmentData) {
        maid.hurtDuration = 0;
        maid.hurtTime = 0;
        maid.deathTime = 0;
        maid.setOnGround(true);
        maid.setInSittingPose(false);
        maid.setMaidBackpackType(BackpackManager.getEmptyBackpack());
        maid.setCustomName(TextComponent.EMPTY);
        if (clearEquipmentData) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                maid.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 客户端换维度时清空 {@link #ENTITY_CACHE}（上游 {@code EntityJoinWorldEvent} 的替代）。
     *
     * <p>缓存的实体身上还挂着旧的 {@code Level}，跨维度继续用会算出错误的光照与
     * 朝向。女仆的两个专用缓存不用清：它们的 key 分别是方块坐标与物品栈，
     * 换维度后 {@code load()} 会整体覆盖。</p>
     */
    public static void onClientLevelChange(Level level) {
        if (level == null) {
            return;
        }
        ResourceKey<Level> dim = level.dimension();
        if (!dim.equals(dimAt)) {
            dimAt = dim;
            ENTITY_CACHE.invalidateAll();
        }
    }
}

