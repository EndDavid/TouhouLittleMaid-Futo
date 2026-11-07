package com.github.tartaricacid.touhoulittlemaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.capability.IPower;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 灵力的存档读写。
 *
 * <p>上游靠 Forge capability 的 {@code ICapabilitySerializable} 自动落盘；
 * 本移植版把字段长在 {@code Player} 上（见 {@code PlayerPowerMixin}），
 * 因此必须手工接到玩家的 NBT 里，否则<b>每次退出重进灵力都会归零</b>。</p>
 *
 * <p>注入点是 {@code ServerPlayer} 自己的那两个方法（它们 override 了
 * {@code Player} 的同名方法，而玩家存档写的正是 {@code ServerPlayer} 这一层）。</p>
 *
 * <p>键名 {@code FutoPower} 刻意加了前缀：上游能力层的 NBT 键随 Forge 序列化器变化，
 * 本移植版不打算与 Forge 存档互通，取一个不会与任何 vanilla 键冲突的名字更安全。</p>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPowerMixin {
    private static final String POWER_TAG = "FutoPower";

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void tlm$savePower(CompoundTag tag, CallbackInfo ci) {
        if ((Object) this instanceof IPower holder) {
            tag.putFloat(POWER_TAG, holder.getPower().get());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void tlm$loadPower(CompoundTag tag, CallbackInfo ci) {
        if ((Object) this instanceof IPower holder && tag.contains(POWER_TAG, Tag.TAG_FLOAT)) {
            holder.getPower().set(tag.getFloat(POWER_TAG));
        }
    }
}

