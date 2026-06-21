package com.backrooms;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

public class ModDimensions {
    public static final ResourceLocation BACKROOMS_DIMENSION_TYPE = new ResourceLocation(BackroomsMod.MOD_ID, "backrooms_dimension_type");
    public static final ResourceLocation BACKROOMS_DIMENSION = new ResourceLocation(BackroomsMod.MOD_ID, "backrooms");

    public static void register() {
        // Dimension type and dimension are registered via JSON files in data/backrooms/dimension_type and data/backrooms/dimension
        BackroomsMod.LOGGER.info("Registered Backrooms dimension");
    }
}
