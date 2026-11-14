package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * 女仆的「对局记录」，移植自上游 {@code entity/passive/MaidGameRecordManager}。
 *
 * <p>存两样东西：五子棋胜场（客户端要用来算段位）和「这一局谁赢了」的临时状态
 * （客户端动画要用 —— 赢棋笑、输棋丧气）。两者都走同步数据，因为都是<b>客户端要看的</b>；
 * 但存档只写胜场，临时状态不落盘（上游同款：退出重进不该还挂着上一次的胜负表情）。</p>
 *
 * <h2>与上游的差异</h2>
 * <p>上游把两个 {@code EntityDataAccessor} 声明在 {@code EntityMaid} 里，这里放在本类内 ——
 * {@code SynchedEntityData.defineId} 是静态方法，声明在哪里都一样，但放在一起能少改一次
 * {@code EntityMaid}（那个文件太大，历史上被误截断过两次）。同步数据的 {@code define}
 * 仍然必须由 {@code EntityMaid#defineSynchedData} 调 {@link #defineSynchedData(SynchedEntityData)} 完成。</p>
 */
public class MaidGameRecordManager {
    private static final String GAME_SKILL_TAG = "MaidGameSkillData";
    private static final String GOMOKU = "Gomoku";
    private static final byte NONE = 0, WIN = 1, LOSE = 2;

    private static final EntityDataAccessor<CompoundTag> GAME_SKILL =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Byte> GAME_STATUE =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.BYTE);

    private final EntityMaid maid;

    public MaidGameRecordManager(EntityMaid maid) {
        this.maid = maid;
    }

    void defineSynchedData(SynchedEntityData data) {
        data.define(GAME_SKILL, new CompoundTag());
        data.define(GAME_STATUE, NONE);
    }

    void addAdditionalSaveData(CompoundTag compound) {
        compound.put(GAME_SKILL_TAG, getGameSkill());
    }

    void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains(GAME_SKILL_TAG, Tag.TAG_COMPOUND)) {
            setGameSkill(compound.getCompound(GAME_SKILL_TAG));
        }
    }

    /**
     * 离开座位就把胜负表情清掉，否则下次坐上别的棋盘时女仆还挂着上一局的表情。
     */
    void tick() {
        if (!(this.maid.getVehicle() instanceof EntitySit) && getGameStatue() != NONE) {
            resetStatue();
        }
    }

    private CompoundTag getGameSkill() {
        // 必须返回副本：set() 按「内容」判脏，取回同一实例改完再塞回去会被判成没变化
        return this.maid.getEntityData().get(GAME_SKILL).copy();
    }

    private void setGameSkill(CompoundTag gameSkill) {
        this.maid.forceSyncData(GAME_SKILL, gameSkill, true);
    }

    private byte getGameStatue() {
        return this.maid.getEntityData().get(GAME_STATUE);
    }

    private void setGameStatue(byte gameStatue) {
        this.maid.getEntityData().set(GAME_STATUE, gameStatue);
    }

    public int getGomokuWinCount() {
        CompoundTag gameSkill = this.getGameSkill();
        if (gameSkill.contains(GOMOKU, Tag.TAG_INT)) {
            return gameSkill.getInt(GOMOKU);
        }
        return 0;
    }

    public void increaseGomokuWinCount() {
        CompoundTag gameSkill = this.getGameSkill();
        if (gameSkill.contains(GOMOKU, Tag.TAG_INT)) {
            gameSkill.putInt(GOMOKU, gameSkill.getInt(GOMOKU) + 1);
        } else {
            gameSkill.putInt(GOMOKU, 1);
        }
        this.setGameSkill(gameSkill);
    }

    public boolean isWin() {
        return this.getGameStatue() == WIN;
    }

    public boolean isLost() {
        return this.getGameStatue() == LOSE;
    }

    /**
     * 标记这一局的胜负，并放对应的语音。
     */
    public void markStatue(boolean isWin) {
        this.setGameStatue(isWin ? WIN : LOSE);
        if (isWin) {
            maid.playSound(InitSounds.GAME_WIN, 1, 1);
        } else {
            maid.playSound(InitSounds.GAME_LOST, 1, 1);
        }
    }

    public void resetStatue() {
        this.setGameStatue(NONE);
    }
}
