package com.backrooms;

import com.backrooms.block.WallpaperBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;

public class ModBlocks {
    public static final Block WALLPAPER = new WallpaperBlock();

    public static void register() {
        // Register block
        Block block = WALLPAPER;
        ResourceLocation blockId = new ResourceLocation(BackroomsMod.MOD_ID, "wallpaper");
        BlockItem blockItem = new BlockItem(block, new Item.Properties());
        ResourceLocation itemId = new ResourceLocation(BackroomsMod.MOD_ID, "wallpaper");
        
        Registry.register(BuiltInRegistries.BLOCK, blockId, block);
        Registry.register(BuiltInRegistries.ITEM, itemId, blockItem);
    }
}
