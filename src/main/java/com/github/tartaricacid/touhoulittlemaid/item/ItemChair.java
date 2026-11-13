package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.client.resource.ChairModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.ChairModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 坐垫物品，移植自上游 {@code item/ItemChair}。
 *
 * <p>它本身只是一张「模型 id + 坐高 + 可骑 + 无重力」的凭证：右键地面生成一个
 * {@link EntityChair}，蹲下左键拆除后又会变回同样数据的物品。
 *
 * <p><b>与上游的差异：</b>
 * <ul>
 *     <li>上游用 Forge 的 {@code IItemRenderProperties#getItemStackRenderer()} 提供
 *         {@code TileEntityItemStackChairRenderer}；本移植版改用 Fabric 的
 *         {@code BuiltinItemRendererRegistry}，见
 *         {@code client/renderer/item/ChairItemRenderer}。</li>
 *     <li>{@code @OnlyIn(Dist.CLIENT)} 改成 {@code FabricLoader} 的环境判断 —— Fabric 上
 *         {@code @OnlyIn} 只是文档性注解，不会真的剔除方法。</li>
 * </ul>
 */
public class ItemChair extends Item {
    private static final String DEFAULT_MODEL_ID = "touhou_little_maid:cushion";

    public ItemChair() {
        super(new FabricItemSettings().tab(InitItems.CHAIR_TAB).stacksTo(1));
    }

    @NotNull
    public static Data getData(@NotNull ItemStack stack) {
        if (stack.getItem() == InitItems.CHAIR) {
            return Data.deserialization(stack.getOrCreateTag());
        }
        return new Data(DEFAULT_MODEL_ID, 0.0F, true, false);
    }

    @NotNull
    public static ItemStack setData(@NotNull ItemStack stack, @NotNull Data data) {
        if (stack.getItem() == InitItems.CHAIR) {
            Data.serialization(stack.getOrCreateTag(), data);
        }
        return stack;
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        if (context.getClickedFace() == Direction.DOWN) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos clickedPos = new BlockPlaceContext(context).getClickedPos();
        AABB boundingBox = InitEntities.CHAIR.getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(clickedPos));
        if (!level.noCollision(boundingBox) || !level.getEntities(null, boundingBox).isEmpty()) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel serverLevel) {
            EntityChair chair = getSpawnChair(serverLevel, context.getPlayer(), stack, context.getClickedPos(),
                    context.getRotation());
            if (chair == null) {
                return InteractionResult.FAIL;
            }
            level.addFreshEntity(chair);
            level.playSound(null, chair.getX(), chair.getY(), chair.getZ(),
                    SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    public static EntityChair getSpawnChair(@NotNull ServerLevel serverLevel, @Nullable Player player,
                                            @NotNull ItemStack stack, @NotNull BlockPos pos, float rotation) {
        Component customName = null;
        if (stack.hasCustomHoverName()) {
            customName = stack.getDisplayName();
        }
        EntityChair chair = InitEntities.CHAIR.create(serverLevel, stack.getTag(), customName, player, pos,
                MobSpawnType.SPAWN_EGG, true, true);
        if (chair != null) {
            addExtraData(player, stack, chair, rotation);
        }
        return chair;
    }

    private static void addExtraData(@Nullable Player player, @NotNull ItemStack stack,
                                     @NotNull EntityChair chair, float rotation) {
        Data data = Data.deserialization(stack.getOrCreateTag());
        chair.setModelId(data.getModelId());
        chair.setMountedHeight(data.getHeight());
        chair.setTameableCanRide(data.isCanRide());
        chair.setNoGravity(data.isNoGravity());
        chair.setOwner(player);
        // 与上游一致：吸附到 45° 的整数倍，避免玩家摆出一地奇怪的角度
        float yaw = (float) Mth.floor((Mth.wrapDegrees(rotation - 180) + 22.5F) / 45.0F) * 45.0F;
        chair.moveTo(chair.getX(), chair.getY(), chair.getZ(), yaw, 0.0F);
        chair.setYBodyRot(yaw);
        chair.setYHeadRot(yaw);
    }

    /**
     * 创造模式物品栏里把<b>每一种坐垫模型</b>都列一遍，这与上游一致。
     */
    @Override
    public void fillItemCategory(@NotNull CreativeModeTab group, @NotNull NonNullList<ItemStack> items) {
        if (!this.allowdedIn(group)) {
            return;
        }
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        ChairModelRegistry.appendCreativeItems(items, this);
    }

    @NotNull
    @Override
    public Component getName(@NotNull ItemStack stack) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            // 添加坐垫前缀，方便搜索
            MutableComponent prefix = new TranslatableComponent("item.touhou_little_maid.chair.prefix");
            Data data = getData(stack);
            ChairModelInfo info = ChairModelRegistry.getInfo(data.getModelId()).orElse(null);
            if (info != null) {
                return prefix.append(ParseI18n.parse(info.getName()));
            }
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.chair.place.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.chair.destroy.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.chair.gui.desc")
                .withStyle(ChatFormatting.GRAY));
        // 调试模式，不加国际化
        if (flag.isAdvanced() && ShiftKey.isShiftDown()) {
            Data data = Data.deserialization(stack.getOrCreateTag());
            tooltip.add(new TextComponent("Model Id: " + data.getModelId()).withStyle(ChatFormatting.GRAY));
            tooltip.add(new TextComponent("Mounted Height: " + data.getHeight()).withStyle(ChatFormatting.GRAY));
            tooltip.add(new TextComponent("Tameable Can Ride: " + data.isCanRide()).withStyle(ChatFormatting.GRAY));
            tooltip.add(new TextComponent("Is No Gravity: " + data.isNoGravity()).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * {@code Screen.hasShiftDown()} 是客户端类。
     *
     * <p>把调用放进一个只在客户端才会被初始化的嵌套类里，服务端就永远碰不到
     * {@code net.minecraft.client.gui.screens.Screen} 这个类引用。</p>
     */
    private static final class ShiftKey {
        private static final ShiftKey INSTANCE = new ShiftKey();

        private static boolean isShiftDown() {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT && INSTANCE.shift();
        }

        private boolean shift() {
            return Screen.hasShiftDown();
        }
    }

    /**
     * 坐垫的物品数据：模型 id、坐高、是否允许生物自动坐上来、是否无重力。
     */
    public static class Data {
        private static final String MODEL_ID_TAG = "ModelId";
        private static final String MOUNTED_HEIGHT_TAG = "MountedHeight";
        private static final String TAMEABLE_CAN_RIDE_TAG = "TameableCanRide";
        private static final String IS_NO_GRAVITY_TAG = "IsNoGravity";

        private final String modelId;
        private final float height;
        private final boolean canRide;
        private final boolean isNoGravity;

        public Data(String modelId, float height, boolean canRide, boolean isNoGravity) {
            this.modelId = modelId;
            this.height = height;
            this.canRide = canRide;
            this.isNoGravity = isNoGravity;
        }

        public static void serialization(@NotNull CompoundTag nbt, @NotNull Data data) {
            nbt.putString(MODEL_ID_TAG, data.getModelId());
            nbt.putFloat(MOUNTED_HEIGHT_TAG, data.getHeight());
            nbt.putBoolean(TAMEABLE_CAN_RIDE_TAG, data.isCanRide());
            nbt.putBoolean(IS_NO_GRAVITY_TAG, data.isNoGravity());
        }

        @NotNull
        public static Data deserialization(@NotNull CompoundTag nbt) {
            String modelId = DEFAULT_MODEL_ID;
            float height = 0.0F;
            boolean canRide = true;
            boolean noGravity = false;

            if (nbt.contains(MODEL_ID_TAG, Tag.TAG_STRING)) {
                modelId = nbt.getString(MODEL_ID_TAG);
            }
            if (nbt.contains(MOUNTED_HEIGHT_TAG, Tag.TAG_FLOAT)) {
                height = nbt.getFloat(MOUNTED_HEIGHT_TAG);
            }
            if (nbt.contains(TAMEABLE_CAN_RIDE_TAG, Tag.TAG_BYTE)) {
                canRide = nbt.getBoolean(TAMEABLE_CAN_RIDE_TAG);
            }
            if (nbt.contains(IS_NO_GRAVITY_TAG, Tag.TAG_BYTE)) {
                noGravity = nbt.getBoolean(IS_NO_GRAVITY_TAG);
            }
            return new Data(modelId, height, canRide, noGravity);
        }

        public String getModelId() {
            return this.modelId;
        }

        public float getHeight() {
            return this.height;
        }

        public boolean isCanRide() {
            return this.canRide;
        }

        public boolean isNoGravity() {
            return this.isNoGravity;
        }
    }
}
