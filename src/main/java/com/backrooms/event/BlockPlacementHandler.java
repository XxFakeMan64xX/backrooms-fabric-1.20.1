package com.backrooms.event;

import com.backrooms.BackroomsMod;
import com.backrooms.scanner.SpaceScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BlockPlacementHandler {
    
    public static void register() {
        // Placement is handled by mixin in ServerPlayerInteractionManagerMixin
        BackroomsMod.LOGGER.info("Block placement handler registered via mixin");
    }
    
    public static void handleBlockPlace(Level world, BlockPos pos, BlockState state, net.minecraft.server.level.ServerPlayer player) {
        BackroomsMod.LOGGER.info("Block placed at {}, scanning for enclosed space from player position...", pos);
        
        // Use player position instead of block position for scanning
        BlockPos playerPos = player.blockPosition();
        boolean found = SpaceScanner.scanAndSaveEnclosedSpace(world, playerPos);
        
        if (found) {
            BackroomsMod.LOGGER.info("Enclosed space detected and saved!");
        } else {
            BackroomsMod.LOGGER.info("No enclosed space detected.");
        }
    }
}
