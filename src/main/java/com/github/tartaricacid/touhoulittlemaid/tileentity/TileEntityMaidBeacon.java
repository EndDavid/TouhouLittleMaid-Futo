package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 神社庭灯的方块实体，移植自上游 {@code tileentity/TileEntityMaidBeacon}。
 */
public class TileEntityMaidBeacon extends BlockEntity {
    public static final String POTION_INDEX_TAG = "PotionIndex";
    public static final String STORAGE_POWER_TAG = "StoragePower";
    public static final String OVERFLOW_DELETE_TAG = "OverflowDelete";
    /**
     * 上游靠 Forge 的 {@code getPersistentData()} 把三个字段包在 {@code ForgeData} 下，
     * 物品形态也沿用了这个路径。这里保留常量名，只为兼容物品 NBT。
     */
    public static final String FORGE_DATA_TAG = "ForgeData";

    /**
     * 效果序号，{@code -1} 表示未选择。与上游一致：用枚举 ordinal 而不是注册名，
     * 因为 {@link BeaconEffect} 的枚举顺序就是 GUI 按钮的顺序。
     */
    private int potionIndex = -1;
    private float storagePower;
    private boolean overflowDelete = false;

    public TileEntityMaidBeacon(BlockPos blockPos, BlockState blockState) {
        super(InitBlocks.MAID_BEACON_TE, blockPos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileEntityMaidBeacon beacon) {
        if (beacon.level != null && !level.isClientSide && level.getGameTime() % 80L == 0L) {
            if (beacon.potionIndex != -1 && beacon.storagePower >= beacon.getEffectCost()) {
                beacon.storagePower = beacon.storagePower - beacon.getEffectCost();
                beacon.updateBeaconEffect(level, BeaconEffect.getEffectByIndex(beacon.potionIndex).getEffect());
                // 与上游一致：这里不调用 refresh()，否则每 4 秒都会给周围玩家推一次方块更新包。
                // 存储量的显示误差由 GUI 打开期间的其它操作 / 重新打开界面刷新。
            }
            beacon.updateAbsorbPower(level);
        }
    }

    private void updateBeaconEffect(Level world, MobEffect potion) {
        List<EntityMaid> list = world.getEntitiesOfClass(EntityMaid.class,
                new AABB(this.getBlockPos()).inflate(8, 8, 8), LivingEntity::isAlive);
        for (EntityMaid maid : list) {
            maid.addEffect(new MobEffectInstance(potion, 100, 1, true, true));
        }
    }

    private void updateAbsorbPower(Level world) {
        int range = MiscConfig.shrineLampMaxRange();
        List<EntityPowerPoint> list = world.getEntitiesOfClass(EntityPowerPoint.class,
                new AABB(this.getBlockPos()).inflate(range, range, range), Entity::isAlive);
        for (EntityPowerPoint powerPoint : list) {
            float addNum = this.getStoragePower() + powerPoint.getValue() / 100.0f;
            if (addNum <= this.getMaxStorage()) {
                this.setStoragePower(addNum);
                powerPoint.spawnExplosionParticle();
                powerPoint.discard();
            } else if (this.overflowDelete) {
                powerPoint.spawnExplosionParticle();
                powerPoint.discard();
            }
        }
    }

    // ------------------------------------------------------------------ 存档

    @Override
    public void saveAdditional(CompoundTag compound) {
        compound.putInt(POTION_INDEX_TAG, this.potionIndex);
        compound.putFloat(STORAGE_POWER_TAG, this.storagePower);
        compound.putBoolean(OVERFLOW_DELETE_TAG, this.overflowDelete);
        super.saveAdditional(compound);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.potionIndex = nbt.getInt(POTION_INDEX_TAG);
        this.storagePower = nbt.getFloat(STORAGE_POWER_TAG);
        this.overflowDelete = nbt.getBoolean(OVERFLOW_DELETE_TAG);
    }

    /**
     * 从物品 NBT 还原。传入的是 {@code StorageData.ForgeData} 那一层，
     * 字段名与 {@link #saveAdditional} 完全相同。
     */
    public void loadData(CompoundTag data) {
        this.potionIndex = data.getInt(POTION_INDEX_TAG);
        this.storagePower = data.getFloat(STORAGE_POWER_TAG);
        this.overflowDelete = data.getBoolean(OVERFLOW_DELETE_TAG);
    }

    /**
     * 生成物品形态的「数据根」，{@code ItemMaidBeacon} 会把它塞进 {@code StorageData}。
     *
     * <p>层级刻意仿照上游：{@code ForgeData} → 三个字段。这样上游存档里带 P 点的庭灯物品
     * 搬进本移植版仍然能读出存储量。</p>
     */
    public CompoundTag saveToItemTag() {
        CompoundTag forgeData = new CompoundTag();
        forgeData.putInt(POTION_INDEX_TAG, this.potionIndex);
        forgeData.putFloat(STORAGE_POWER_TAG, this.storagePower);
        forgeData.putBoolean(OVERFLOW_DELETE_TAG, this.overflowDelete);
        CompoundTag root = new CompoundTag();
        root.put(FORGE_DATA_TAG, forgeData);
        return root;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ 字段

    public int getPotionIndex() {
        return this.potionIndex;
    }

    public void setPotionIndex(int potionIndex) {
        this.potionIndex = potionIndex;
        this.refresh();
    }

    public float getStoragePower() {
        return this.storagePower;
    }

    public void setStoragePower(float storagePower) {
        this.storagePower = storagePower;
        this.refresh();
    }

    public boolean isOverflowDelete() {
        return this.overflowDelete;
    }

    public void setOverflowDelete(boolean overflowDelete) {
        this.overflowDelete = overflowDelete;
        this.refresh();
    }

    /**
     * 每次施加效果消耗的灵力。配置单位是「灵力 / 小时」，80 tick 一小时有 900 份
     * （20 tick/s × 60 s × 60 min ÷ 80 = 900），与上游一致。
     */
    public float getEffectCost() {
        return (float) (MiscConfig.shrineLampEffectCost() / 900);
    }

    public float getMaxStorage() {
        return (float) MiscConfig.shrineLampMaxStorage();
    }

    /**
     * 标记脏数据并通知客户端重读方块实体（GUI 的进度条 / 按钮状态靠它刷新）。
     */
    public void refresh() {
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.level.getBlockState(this.worldPosition);
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * GUI 上的五个按钮。顺序即 GUI 里的排列顺序，也是 {@code potionIndex} 的含义。
     */
    public enum BeaconEffect {
        SPEED(MobEffects.MOVEMENT_SPEED),
        FIRE_RESISTANCE(MobEffects.FIRE_RESISTANCE),
        STRENGTH(MobEffects.DAMAGE_BOOST),
        RESISTANCE(MobEffects.DAMAGE_RESISTANCE),
        REGENERATION(MobEffects.REGENERATION);

        private final MobEffect effect;

        BeaconEffect(MobEffect effect) {
            this.effect = effect;
        }

        /**
         * 按序号取效果，越界时夹到合法区间。
         *
         * <p>上游写的是 {@code Mth.clamp(0, index, values().length - 1)} ——
         * 参数顺序反了（{@code clamp(value, min, max)}），传入 {@code index > 4} 时会越界。
         * 这里修正为 {@code clamp(index, 0, len - 1)}，同时仍返回默认的第一个效果。</p>
         */
        public static BeaconEffect getEffectByIndex(int index) {
            return values()[Mth.clamp(index, 0, values().length - 1)];
        }

        public MobEffect getEffect() {
            return this.effect;
        }
    }
}

