package com.github.tartaricacid.touhoulittlemaid.crafting;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitRecipes;
import com.github.tartaricacid.touhoulittlemaid.inventory.AltarRecipeInventory;
import com.google.common.base.Preconditions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.RecipeMatcher;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.Objects;

import static com.github.tartaricacid.touhoulittlemaid.inventory.AltarRecipeInventory.RECIPES_SIZE;

/**
 * 祭坛合成表，移植自上游 {@code crafting/AltarRecipe}。
 *
 * <p>与普通合成表最大的区别：<b>产物是实体，不是物品</b>。所以它不实现
 * {@code getResultItem} 的常规语义（那是给 JEI / 配方书看的），真正生成产物的是
 * {@link #spawnOutputEntity}。</p>
 *
 * <h2>三个字段决定了「同一个配方能产出不同东西」</h2>
 * <ul>
 *     <li>{@code extraData}：产物的额外 NBT（例如「盒子里装着一只女仆」）；</li>
 *     <li>{@code copyInput} + {@code copyTag}：把匹配到的某个材料的一个子标签
 *         抄到产物实体上 —— 神社复活与「胶片 → 女仆」都靠这条通路，
 *         否则玩家复活出来的女仆会丢掉名字、模型、好感度等全部数据。</li>
 * </ul>
 *
 * <h2>与上游的差异</h2>
 * <p>{@code EntityType#getRegistryName} 是 Forge 加的，这里改用
 * {@code ForgeRegistries.ENTITIES.getKey(...)}（同一语义）。其余逐字一致。</p>
 */
public class AltarRecipe implements Recipe<AltarRecipeInventory> {
    private final ResourceLocation id;
    private final EntityType<?> entityType;
    @Nullable
    private final CompoundTag extraData;
    private final float powerCost;
    private final NonNullList<Ingredient> inputs;
    private final boolean isItemCraft;
    private final ItemStack resultItem;
    private final Ingredient copyInput;
    @Nullable
    private final String copyTag;

    public AltarRecipe(ResourceLocation id, EntityType<?> entityType, @Nullable CompoundTag extraData,
                       float powerCost, Ingredient copyInput, @Nullable String copyTag, Ingredient... inputs) {
        Preconditions.checkArgument(0 < inputs.length && inputs.length <= RECIPES_SIZE,
                "Ingredients count is illegal!");
        this.id = id;
        this.entityType = entityType;
        this.isItemCraft = (entityType == EntityType.ITEM);
        if (this.isItemCraft && extraData != null) {
            // 产物是掉落物时，物品本身写在 NBT 的 "Item" 子标签里（见 data/.../recipes/altar/*.json）
            this.resultItem = ItemStack.of(extraData.getCompound("Item"));
        } else {
            this.resultItem = ItemStack.EMPTY;
        }
        this.copyInput = copyInput;
        this.copyTag = copyTag;
        this.extraData = extraData;
        this.powerCost = powerCost;
        this.inputs = NonNullList.of(Ingredient.EMPTY, fillInputs(inputs));
    }

    public AltarRecipe(ResourceLocation id, EntityType<?> entityType, @Nullable CompoundTag extraData,
                       float powerCost, Ingredient... inputs) {
        this(id, entityType, extraData, powerCost, Ingredient.EMPTY, null, inputs);
    }

    @Override
    public boolean matches(AltarRecipeInventory inv, Level worldIn) {
        // RecipeMatcher 会跳过 EMPTY 补位，见 net.minecraftforge.common.util.RecipeMatcher 的注释
        return RecipeMatcher.findMatches(inv.getItems(), inputs) != null;
    }

    @Override
    public ItemStack assemble(AltarRecipeInventory inv) {
        return getResultItem().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        // 祭坛不是网格合成，不需要 2×2 / 3×3 判定
        return false;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return inputs;
    }

    @Override
    public ItemStack getResultItem() {
        return resultItem;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return InitRecipes.ALTAR_RECIPE_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return InitRecipes.ALTAR_CRAFTING;
    }

    /**
     * 让它不出现在配方书里（上游同款）：祭坛配方不是工作台配方，
     * 塞进配方书会画出一堆点不动的条目。
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getToastSymbol() {
        return InitItems.HAKUREI_GOHEI.getDefaultInstance();
    }

    public EntityType<?> getEntityType() {
        return entityType;
    }

    @Nullable
    public CompoundTag getExtraData() {
        return extraData;
    }

    public float getPowerCost() {
        return powerCost;
    }

    public Ingredient getCopyInput() {
        return copyInput;
    }

    @Nullable
    public String getCopyTag() {
        return copyTag;
    }

    /**
     * 在指定坐标生成产物实体。
     *
     * <p>两条路径：</p>
     * <ol>
     *     <li>有 {@code extraData}：用 NBT 反序列化（可能是带乘客的盒子、带满身数据的女仆）；</li>
     *     <li>没有：直接 {@code spawn}（例如召唤闪电）。</li>
     * </ol>
     */
    public void spawnOutputEntity(ServerLevel world, BlockPos pos, @Nullable AltarRecipeInventory inventory) {
        if (extraData != null) {
            CompoundTag nbt = this.extraData.copy();
            nbt.putString("id", Objects.requireNonNull(ForgeRegistries.ENTITIES.getKey(entityType)).toString());
            Entity resultEntity = EntityType.loadEntityRecursive(nbt, world, (e) -> {
                e.moveTo(pos.getX(), pos.getY(), pos.getZ(), e.getYRot(), e.getXRot());
                this.finalizeSpawn(world, pos, e);
                return e;
            });
            if (resultEntity != null) {
                this.finalizeSpawn(world, pos, resultEntity);
                this.copyIngredientTag(inventory, resultEntity);
                world.tryAddFreshEntityWithPassengers(resultEntity);
            }
            return;
        }
        entityType.spawn(world, null, null, null, pos, MobSpawnType.SPAWN_EGG, true, true);
    }

    /**
     * 把材料上的一个 NBT 子标签抄到产物实体上。
     *
     * <p>神社复活的关键：胶片里的 {@code MaidInfo} 就是通过这条通路写进新女仆的。
     * 注意 {@code copyTag} 为空时抄的是整个物品 NBT，不为空时只抄那个子标签。</p>
     */
    private void copyIngredientTag(@Nullable AltarRecipeInventory inventory, Entity resultEntity) {
        if (inventory != null && this.copyInput != Ingredient.EMPTY) {
            ItemStack matchStack = inventory.getMatchIngredient(this.copyInput);
            if (!matchStack.isEmpty()) {
                CompoundTag data;
                if (StringUtils.isEmpty(this.copyTag)) {
                    data = matchStack.getTag();
                } else {
                    data = matchStack.getTagElement(this.copyTag);
                }
                if (data != null && !data.isEmpty()) {
                    if (resultEntity instanceof LivingEntity livingEntity) {
                        livingEntity.readAdditionalSaveData(data);
                    }
                    if (resultEntity instanceof ItemEntity itemEntity) {
                        itemEntity.readAdditionalSaveData(data);
                    }
                }
            }
        }
    }

    public boolean isItemCraft() {
        return isItemCraft;
    }

    private void finalizeSpawn(ServerLevel world, BlockPos pos, @Nullable Entity entity) {
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), MobSpawnType.SPAWN_EGG, null, null);
        }
    }

    private Ingredient[] fillInputs(Ingredient[] inputs) {
        Ingredient[] newInputs = new Ingredient[RECIPES_SIZE];
        for (int i = 0; i < RECIPES_SIZE; i++) {
            if (i < inputs.length) {
                newInputs[i] = inputs[i];
            } else {
                newInputs[i] = Ingredient.EMPTY;
            }
        }
        return newInputs;
    }
}

