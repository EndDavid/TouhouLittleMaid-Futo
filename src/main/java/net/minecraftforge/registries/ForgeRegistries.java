package net.minecraftforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Futo 兼容层：Forge 注册表门面。
 */
public final class ForgeRegistries {
    public static final FutoForgeRegistry<Block> BLOCKS = new FutoForgeRegistry<>(Registry.BLOCK, id -> TagKey.create(Registry.BLOCK_REGISTRY, id));
    public static final FutoForgeRegistry<Item> ITEMS = new FutoForgeRegistry<>(Registry.ITEM, id -> TagKey.create(Registry.ITEM_REGISTRY, id));
    public static final FutoForgeRegistry<EntityType<?>> ENTITIES = new FutoForgeRegistry<>(Registry.ENTITY_TYPE, id -> TagKey.create(Registry.ENTITY_TYPE_REGISTRY, id));
    public static final FutoForgeRegistry<BlockEntityType<?>> BLOCK_ENTITIES = new FutoForgeRegistry<>(Registry.BLOCK_ENTITY_TYPE, id -> TagKey.create(Registry.BLOCK_ENTITY_TYPE_REGISTRY, id));
    public static final FutoForgeRegistry<Attribute> ATTRIBUTES = new FutoForgeRegistry<>(Registry.ATTRIBUTE, id -> TagKey.create(Registry.ATTRIBUTE_REGISTRY, id));
    public static final FutoForgeRegistry<MemoryModuleType<?>> MEMORY_MODULE_TYPES = new FutoForgeRegistry<>(Registry.MEMORY_MODULE_TYPE, id -> TagKey.create(Registry.MEMORY_MODULE_TYPE_REGISTRY, id));
    public static final FutoForgeRegistry<SensorType<?>> SENSOR_TYPES = new FutoForgeRegistry<>(Registry.SENSOR_TYPE, id -> TagKey.create(Registry.SENSOR_TYPE_REGISTRY, id));
    public static final FutoForgeRegistry<Activity> ACTIVITIES = new FutoForgeRegistry<>(Registry.ACTIVITY, id -> TagKey.create(Registry.ACTIVITY_REGISTRY, id));
    public static final FutoForgeRegistry<Schedule> SCHEDULES = new FutoForgeRegistry<>(Registry.SCHEDULE, id -> TagKey.create(Registry.SCHEDULE_REGISTRY, id));
    public static final FutoForgeRegistry<net.minecraft.sounds.SoundEvent> SOUND_EVENTS = new FutoForgeRegistry<>(Registry.SOUND_EVENT, id -> TagKey.create(Registry.SOUND_EVENT_REGISTRY, id));
    public static final FutoForgeRegistry<net.minecraft.world.item.enchantment.Enchantment> ENCHANTMENTS = new FutoForgeRegistry<>(Registry.ENCHANTMENT, id -> TagKey.create(Registry.ENCHANTMENT_REGISTRY, id));
    public static final FutoForgeRegistry<RecipeSerializer<?>> RECIPE_SERIALIZERS = new FutoForgeRegistry<>(Registry.RECIPE_SERIALIZER, id -> TagKey.create(Registry.RECIPE_SERIALIZER_REGISTRY, id));
    public static final FutoForgeRegistry<net.minecraft.world.entity.ai.village.poi.PoiType> POI_TYPES = ownRegistry(Keys.POI_TYPES);

    // Forge 独有注册表：由 Futo 自己维护
    public static final FutoForgeRegistry<net.minecraft.world.inventory.MenuType<?>> CONTAINERS =
            new FutoForgeRegistry<>(net.minecraft.core.Registry.MENU, id -> TagKey.create(net.minecraft.core.Registry.MENU_REGISTRY, id));
    public static final FutoForgeRegistry<DataSerializerEntry> DATA_SERIALIZERS = ownRegistry(Keys.DATA_SERIALIZERS);

    private ForgeRegistries() {
    }

    private static <T> FutoForgeRegistry<T> ownRegistry(ResourceKey<? extends Registry<T>> key) {
        return new FutoForgeRegistry<>(null, id -> TagKey.create(key, id));
    }

    public static final class Keys {
        public static final ResourceKey<Registry<net.minecraft.world.inventory.MenuType<?>>> CONTAINERS = Registry.MENU_REGISTRY;
        public static final ResourceKey<Registry<DataSerializerEntry>> DATA_SERIALIZERS =
                ResourceKey.createRegistryKey(new ResourceLocation("futo", "data_serializers"));
        public static final ResourceKey<Registry<net.minecraft.world.entity.ai.village.poi.PoiType>> POI_TYPES =
                Registry.POINT_OF_INTEREST_TYPE_REGISTRY;

        private Keys() {
        }
    }
}
