package com.backrooms.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

public class RoomData {
    private final List<BlockEntry> blocks;
    private final List<DoorEntry> doors;
    
    public RoomData() {
        this.blocks = new ArrayList<>();
        this.doors = new ArrayList<>();
    }
    
    public void addBlock(BlockEntry block) {
        blocks.add(block);
    }
    
    public void addDoor(DoorEntry door) {
        doors.add(door);
    }
    
    public List<BlockEntry> getBlocks() {
        return blocks;
    }
    
    public List<DoorEntry> getDoors() {
        return doors;
    }
    
    public static class BlockEntry {
        public final int relX;
        public final int relY;
        public final int relZ;
        public final String blockId;
        public final String facing;
        public final String half;
        public final String open;
        public final String powered;
        
        public BlockEntry(int relX, int relY, int relZ, String blockId, String facing, String half, String open, String powered) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.blockId = blockId;
            this.facing = facing;
            this.half = half;
            this.open = open;
            this.powered = powered;
        }
    }
    
    public static class DoorEntry {
        public final BlockPos position;
        public final Direction facing;
        public final boolean isUpper;
        
        public DoorEntry(BlockPos position, Direction facing, boolean isUpper) {
            this.position = position;
            this.facing = facing;
            this.isUpper = isUpper;
        }
    }
}
