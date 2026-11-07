package net.minecraftforge.registries;

import net.minecraft.network.syncher.EntityDataSerializer;

public class DataSerializerEntry {
    private final EntityDataSerializer<?> serializer;

    public DataSerializerEntry(EntityDataSerializer<?> serializer) {
        this.serializer = serializer;
    }

    public EntityDataSerializer<?> getSerializer() {
        return serializer;
    }
}

