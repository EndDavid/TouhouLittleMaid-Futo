package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * 手办方块，逐字移植自上游 {@code block/BlockGarageKit}。
 *
 * <p>单格方块，方块实体里存一份实体 NBT，渲染时把那个实体画出来。
 * 它有三个来源：物品放下、拿刷怪蛋点它、迷你雕像被火烧。</p>
 *
 * <h2>为什么碰撞箱只有 8×16×8</h2>
 * <p>手办视觉上是「一个人站在台座上」，但只占中间那一竖条 —— 否则旁边就摆不下第二只。
 * 所以 {@code getShape} 返回 {@link #BLOCK_AABB}，而 {@code getRenderShape} 是
 * {@code ENTITYBLOCK_ANIMATED}（方块本身不画）。</p>
 *
 * <h2>与上游的差异</h2>
 * <p>上游用 {@code initializeClient} 把挖掘粒子换成粘土色（Forge 独占），
 * 1.18.2 没有对应物，所以手办与雕像一样掉的是石头质感粒子。</p>
 */
public class BlockGarageKit extends Block implements EntityBlock {
    /**
     * 中间那一竖条。上边界是 16（顶到方块顶部），因为大尺寸手办会往上长。
     */
    public static final VoxelShape BLOCK_AABB = Block.box(4, 0, 4, 12, 16, 12);

    /** 物品 NBT 里的子标签名，{@code ItemGarageKit} 用的是同一个键。 */
    private static final String ENTITY_INFO = "EntityInfo";

    public BlockGarageKit() {
        super(BlockBehaviour.Properties.of(Material.CLAY).strength(1, 2).noOcclusion());
    }

    /**
     * 创造模式物品栏：每个已加载的女仆模型摆一只。
     *
     * <p>上游遍历 {@code CustomPackLoader.MAID_MODELS.getModelIdSet()}；
     * 本移植版的等价物是 {@link MaidModelRegistry#all()}。</p>
     *
     * <p>这段代码在<b>服务端也会跑</b>（它是 {@code Block} 的方法，不是 {@code @OnlyIn}）：
     * 服务端没有加载模型资源，{@link MaidModelRegistry#all()} 是空集合，
     * 于是循环零次，不会抛异常。创造模式物品栏本身也只有客户端会填。</p>
     */
    @Override
    public void fillItemCategory(CreativeModeTab group, NonNullList<ItemStack> items) {
        for (MaidModelInfo info : MaidModelRegistry.all()) {
            ItemStack stack = new ItemStack(InitBlocks.GARAGE_KIT);
            CompoundTag data = stack.getOrCreateTagElement(ENTITY_INFO);
            data.putString("id", Objects.requireNonNull(
                    Registry.ENTITY_TYPE.getKey(InitEntitiesMaidHolder.MAID)).toString());
            data.putString(EntityMaid.MODEL_ID_TAG, info.getModelId().toString());
            items.add(stack);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityGarageKit(pos, state);
    }

    /**
     * 方块被拆除 / 替换时把手办本体掉出来（带着女仆数据）。
     */
    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            popResource(worldIn, pos, this.getGarageKitFromWorld(worldIn, pos));
        }
        super.onRemove(state, worldIn, pos, newState, isMoving);
    }

    @Override
    public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        this.getGarageKit(worldIn, pos).ifPresent(te -> {
            Direction facing = Direction.SOUTH;
            if (placer != null) {
                facing = placer.getDirection().getOpposite();
            }
            te.setData(facing, ItemGarageKit.getMaidData(stack));
        });
    }

    /**
     * 中键选取 / Ctrl+选取拿到的是「带着这只女仆数据的手办」，不是一只空手办。
     */
    @Override
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
        return this.getGarageKitFromWorld(world, pos);
    }

    /**
     * 拿任意刷怪蛋右键 → 把手办换成那只生物。
     *
     * <p>只认刷怪蛋，其它物品一律 {@code PASS}（否则会挡住正常的右键行为）。</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player playerIn,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = playerIn.getItemInHand(hand);
        if (!(worldIn instanceof ServerLevel) || !(stack.getItem() instanceof SpawnEggItem spawnEgg)) {
            return InteractionResult.PASS;
        }
        BlockEntity tile = worldIn.getBlockEntity(pos);
        if (!(tile instanceof TileEntityGarageKit garageKit)) {
            return InteractionResult.PASS;
        }
        net.minecraft.world.entity.EntityType<?> type = spawnEgg.getType(stack.getTag());
        net.minecraft.resources.ResourceLocation typeId = Registry.ENTITY_TYPE.getKey(type);
        if (typeId == null) {
            return InteractionResult.PASS;
        }

        CompoundTag data = new CompoundTag();
        data.putString("id", typeId.toString());

        Entity entity = type.create(worldIn);
        if (entity instanceof Mob mobEntity) {
            mobEntity.finalizeSpawn((ServerLevel) worldIn, worldIn.getCurrentDifficultyAt(pos),
                    MobSpawnType.SPAWN_EGG, null, data);
            mobEntity.addAdditionalSaveData(data);
        }

        garageKit.setData(garageKit.getFacing(), data);
        return InteractionResult.SUCCESS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return BLOCK_AABB;
    }

    private ItemStack getGarageKitFromWorld(BlockGetter world, BlockPos pos) {
        ItemStack stack = new ItemStack(InitItems.GARAGE_KIT);
        this.getGarageKit(world, pos).ifPresent(te -> stack.getOrCreateTag().put(ENTITY_INFO, te.getExtraData()));
        return stack;
    }

    private Optional<TileEntityGarageKit> getGarageKit(BlockGetter world, BlockPos pos) {
        BlockEntity te = world.getBlockEntity(pos);
        if (te instanceof TileEntityGarageKit garageKit) {
            return Optional.of(garageKit);
        }
        return Optional.empty();
    }

    /**
     * 间接持有 {@code InitEntities.MAID}。
     *
     * <p>为什么要绕一层：{@code InitBlocks} 的静态初始化发生在 {@code InitEntities} 之前时，
     * 直接在字段里引用 {@code InitEntities.MAID} 会把「方块注册 → 实体注册」这条链拉成
     * 初始化环。挪进方法里，只有真正调用 {@code fillItemCategory} 时才去读它。</p>
     */
    private static final class InitEntitiesMaidHolder {
        private static final net.minecraft.world.entity.EntityType<?> MAID =
                com.github.tartaricacid.touhoulittlemaid.init.InitEntities.MAID;
    }
}

