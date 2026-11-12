package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 胶片，移植自上游 {@code item/ItemFilm}。
 *
 * <h2>它是「女仆的尸体」</h2>
 * <p>女仆死亡时会掉落一张胶片，NBT 里的 {@code MaidInfo} 子标签存着
 * 一份 {@code EntityMaid#saveWithoutId} 快照（名字 / 模型 / 好感度 / 经验……）。
 * 把胶片放进神社，再空手右击神社，就能把那只女仆原样复活
 * （代价是玩家血量降到 1/4）。</p>
 *
 * <p>也可以放进祭坛用 {@code reborn_maid} 配方复活 —— 两条路用的是同一份数据。</p>
 *
 * <h2>存档里刻意抹掉的字段</h2>
 * <p>背包 / 饰品 / 装备 / 经验 / 血量这些都不该跟着胶片走（上游
 * {@code removeMaidSomeData} 同样处理）：那些东西在女仆死时已经作为掉落物散出去了，
 * 再复制一份进胶片等于凭空刷物品。</p>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li><b>掉落点不同。</b>上游把胶片塞进墓碑实体（{@code EntityTombstone}）里，
 *         而墓碑属于「死亡与收纳」子系统，本移植版还没移植。所以这里退一步：
 *         女仆死亡时把胶片直接掉在死亡位置（见 {@code EntityMaid#die}）。
 *         玩家体验一致（东西都捡得到），只是没有那块墓碑方块。</li>
 *     <li><b>粒子走 {@code EntityMaid#spawnExplosionParticle}。</b>上游发
 *         {@code SpawnParticleMessage} 让附近客户端播爆炸粒子；这个方法本来就是客户端专用、
 *         且已经在女仆身上，直接调用即可（与 {@code ItemPhoto} 同款处理）。</li>
 * </ol>
 */
public class ItemFilm extends AbstractStoreMaidItem {
    /**
     * 快照里记录的实体类型 id。上游同名常量。
     */
    private static final String ID_TAG = "id";

    /*
     * 需要从快照里抹掉的键。
     *
     * 注意前四个是【本移植版 EntityMaid 的存档键】（上游用常量引用，
     * 值相同但在这里不可见），改名会导致「胶片复制背包」。
     */
    private static final String BACKPACK_TYPE_TAG = "MaidBackpackType";
    private static final String INVENTORY_TAG = "MaidInventory";
    private static final String BAUBLE_INVENTORY_TAG = "MaidBaubleInventory";
    private static final String EXPERIENCE_TAG = "MaidExperience";

    public ItemFilm() {
        super(new FabricItemSettings()
                .stacksTo(1)
                .tab(InitItems.MAIN_TAB)
                // 上游靠 onEntityItemUpdate 让掉落物免疫火焰 / 岩浆；1.18.2 没有那个钩子，
                // 改用物品自带的防火属性（与 ItemPhoto 同款处理），语言文件承诺的行为不变。
                .fireResistant());
    }

    /**
     * 把一只（即将）死亡的女仆压成一张胶片。
     */
    public static ItemStack maidToFilm(EntityMaid maid) {
        ItemStack film = new ItemStack(InitItems.FILM);
        CompoundTag filmTag = new CompoundTag();
        CompoundTag maidTag = new CompoundTag();
        // 居家模式是「阵地」，不该跟着胶片穿越维度
        maid.setHomeModeEnable(false);
        maid.saveWithoutId(maidTag);
        removeMaidSomeData(maidTag);
        maidTag.putString(ID_TAG, Registry.ENTITY_TYPE.getKey(InitEntities.MAID).toString());
        filmTag.put(MAID_INFO, maidTag);
        film.setTag(filmTag);
        return film;
    }

    /**
     * 把胶片里的女仆放出来。
     *
     * <p>胶片会被消耗掉（{@code shrink(1)}）—— 神社那一格因此自动变空，
     * 调用方不需要再额外清格。</p>
     */
    public static void filmToMaid(ItemStack film, Level worldIn, BlockPos pos, Player player) {
        CompoundTag data = getMaidData(film);
        if (data.isEmpty()) {
            if (!worldIn.isClientSide) {
                player.sendMessage(new TranslatableComponent(
                        "tooltips.touhou_little_maid.film.no_data.desc"), Util.NIL_UUID);
            }
            return;
        }
        ResourceLocation entityId = new ResourceLocation(data.getString(ID_TAG));
        ResourceLocation maidId = Registry.ENTITY_TYPE.getKey(InitEntities.MAID);

        if (entityId.equals(maidId)) {
            EntityMaid maid = new EntityMaid(InitEntities.MAID, worldIn);
            maid.readAdditionalSaveData(data);
            maid.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            // 实体生成必须在服务端应用
            if (!worldIn.isClientSide) {
                worldIn.addFreshEntity(maid);
                worldIn.playSound(null, pos, InitSounds.ALTAR_CRAFT, SoundSource.VOICE, 1.0f, 1.0f);
            }
            // 粒子两端都放：客户端立刻有反馈，不用等实体同步包
            maid.spawnExplosionParticle();
            film.shrink(1);
            return;
        }

        if (!worldIn.isClientSide) {
            player.sendMessage(new TranslatableComponent(
                    "tooltips.touhou_little_maid.film.no_data.desc"), Util.NIL_UUID);
        }
    }

    /**
     * 抹掉「已经作为掉落物散出去」的那些字段，避免复活时凭空复制。
     */
    private static void removeMaidSomeData(CompoundTag nbt) {
        nbt.remove(BACKPACK_TYPE_TAG);
        nbt.remove(INVENTORY_TAG);
        nbt.remove(BAUBLE_INVENTORY_TAG);
        nbt.remove(EXPERIENCE_TAG);
        nbt.remove("ArmorItems");
        nbt.remove("HandItems");
        nbt.remove("Leash");
        nbt.remove("Health");
        nbt.remove("HurtTime");
        nbt.remove("DeathTime");
        nbt.remove("HurtByTimestamp");
        nbt.remove("Pos");
        nbt.remove("Motion");
        nbt.remove("FallDistance");
        nbt.remove("Fire");
        nbt.remove("Air");
        nbt.remove("TicksFrozen");
        nbt.remove("HasVisualFire");
        nbt.remove("Passengers");
        nbt.remove("ActiveEffects");
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!hasMaidData(stack)) {
            tooltip.add(new TranslatableComponent(
                    "tooltips.touhou_little_maid.film.no_data.desc").withStyle(ChatFormatting.DARK_RED));
        }
    }
}

