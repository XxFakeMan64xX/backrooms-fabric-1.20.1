package com.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class WallpaperBlock extends Block {
    public WallpaperBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(1.0f, 1.0f)
            .sound(SoundType.WOOL));
    }
}
