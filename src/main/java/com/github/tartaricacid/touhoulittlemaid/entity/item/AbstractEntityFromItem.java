package com.github.tartaricacid.touhoulittlemaid.entity.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * 「由物品摆放出来的实体」的公共父类，移植自上游 {@code entity/item/AbstractEntityFromItem}。
 *
 * <p>坐垫（{@link EntityChair}）继承它。它把 {@code LivingEntity} 当作一个<b>可摆放、可回收</b>
 * 的方盒子来用，因此几乎关掉了所有生物特性（不会被推、不会被击退、不吃药水、不掉血条）。
 *
 * <p><b>与上游的差异（三处，都是 1.18.2 API 的差异）：</b>
 * <ol>
 *     <li>{@code isPickable()}：上游写 {@code !EffectiveSide.get().isServer()}，即「只有客户端可拾取」。
 *         Fabric 没有 {@code EffectiveSide}，等价写法就是 {@code level.isClientSide()}。
 *         这样服务端的钓鱼钩不会把它当目标，而客户端仍然能右键点到它（服务端交互走的是
 *         数据包里的实体 id，不查 {@code isPickable}）。</li>
 *     <li>上游那个 {@code attackable()} 返回 {@code false} 的方法<b>没有移植</b>：它在 1.18.2 的
 *         映射里并不叫这个名字（原版是 {@code isAttackable()}），因此上游那一行其实是个
 *         <b>没有 @Override 的普通方法，从来没生效过</b>。如果照抄成 {@code isAttackable()}
 *         覆盖，玩家左键就打不到坐垫，蹲下左键拆除的玩法会直接失效。</li>
 *     <li>拾取物品（创造模式中键取块）的方法名不同：上游写 {@code getPickedResult(HitResult)}，
 *         而 1.18.2 是 {@link net.minecraft.world.entity.Entity#getPickResult()}，<b>没有参数</b>
 *         （{@code HitResult} 参数是 1.19 才加的）。照抄上游会编译不过。</li>
 * </ol>
 */
public abstract class AbstractEntityFromItem extends LivingEntity {
    protected AbstractEntityFromItem(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    /**
     * 判定此玩家能否击落实体。
     */
    protected abstract boolean canKillEntity(Player player);

    /**
     * 击打时的音效。
     */
    protected abstract SoundEvent getHitSound();

    /**
     * 该实体对应的物品。
     */
    protected abstract Item getWithItem();

    /**
     * 该实体被击落时掉落的物品堆。
     */
    protected abstract ItemStack getKilledStack();

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (!this.level.isClientSide && !this.isRemoved() && this.isAlive()) {
            if (this.isInvulnerableTo(source)) {
                return false;
            }
            if (source.getDirectEntity() instanceof Player player) {
                return applyHitEntityLogic(player);
            }
        }
        return false;
    }

    private boolean applyHitEntityLogic(Player player) {
        if (player.isShiftKeyDown()) {
            this.ejectPassengers();
            this.playSound(getHitSound(), 1.0F, 1.0F);
            if (player.isCreative() || canKillEntity(player)) {
                killEntity();
            }
        }
        return true;
    }

    private void killEntity() {
        this.discard();
        if (this.level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            ItemStack stack = getKilledStack();
            if (this.hasCustomName()) {
                stack.setHoverName(this.getCustomName());
            }
            this.spawnAtLocation(stack, 0.0F);
            this.dropExtraItems();
        }
    }

    protected void dropExtraItems() {
    }

    /**
     * 不允许被挤走，所以此处留空。
     */
    @Override
    public void push(@NotNull Entity entity) {
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void kill() {
        this.remove(RemovalReason.KILLED);
    }

    @Override
    public boolean skipAttackInteraction(@NotNull Entity entity) {
        return entity instanceof Player player && !this.level.mayInteract(player, this.blockPosition());
    }

    @Override
    public void thunderHit(@NotNull ServerLevel level, @NotNull LightningBolt lightning) {
    }

    @Override
    public boolean showVehicleHealth() {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public void knockback(double strength, double ratioX, double ratioZ) {
        // 不允许被击退效果影响
    }

    @NotNull
    @Override
    public ItemStack getPickResult() {
        return getKilledStack();
    }

    // ------------ LivingEntity 要求实现的抽象方法，全用不上，返回默认值 ------------ //

    @NotNull
    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public ItemStack getItemBySlot(@NotNull EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(@NotNull EquipmentSlot slot, @NotNull ItemStack stack) {
    }

    @NotNull
    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.LEFT;
    }
}
