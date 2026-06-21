package com.backrooms.event;

import com.backrooms.BackroomsMod;
import com.backrooms.scanner.SpaceScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

public class DoorInteractionHandler {
    
    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            
            // Check if the interacted block is a door or trapdoor
            if (isDoorOrTrapdoor(state)) {
                BackroomsMod.LOGGER.info("Door/trapdoor interacted at {}, scanning for enclosed space from player position...", pos);
                
                // Use player position instead of door position for scanning
                BlockPos playerPos = player.blockPosition();
                boolean found = SpaceScanner.scanAndSaveEnclosedSpace(world, playerPos);
                
                if (found) {
                    BackroomsMod.LOGGER.info("Enclosed space detected and saved!");
                } else {
                    BackroomsMod.LOGGER.info("No enclosed space detected.");
                }
            }
            
            // Return PASS to allow normal interaction to continue
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
    
    private static boolean isDoorOrTrapdoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock || 
               state.getBlock() instanceof TrapDoorBlock ||
               state.is(Blocks.OAK_DOOR) ||
               state.is(Blocks.SPRUCE_DOOR) ||
               state.is(Blocks.BIRCH_DOOR) ||
               state.is(Blocks.JUNGLE_DOOR) ||
               state.is(Blocks.ACACIA_DOOR) ||
               state.is(Blocks.DARK_OAK_DOOR) ||
               state.is(Blocks.CRIMSON_DOOR) ||
               state.is(Blocks.WARPED_DOOR) ||
               state.is(Blocks.IRON_DOOR) ||
               state.is(Blocks.OAK_TRAPDOOR) ||
               state.is(Blocks.SPRUCE_TRAPDOOR) ||
               state.is(Blocks.BIRCH_TRAPDOOR) ||
               state.is(Blocks.JUNGLE_TRAPDOOR) ||
               state.is(Blocks.ACACIA_TRAPDOOR) ||
               state.is(Blocks.DARK_OAK_TRAPDOOR) ||
               state.is(Blocks.CRIMSON_TRAPDOOR) ||
               state.is(Blocks.WARPED_TRAPDOOR) ||
               state.is(Blocks.IRON_TRAPDOOR);
    }
}
