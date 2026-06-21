package com.backrooms.command;

import com.backrooms.BackroomsMod;
import net.minecraft.core.Direction;

public class RoomRotator {
    
    /**
     * Rotates a block position around the Y-axis by a given number of 90-degree rotations
     */
    public static void rotatePosition(int[] pos, int rotations) {
        BackroomsMod.LOGGER.info("=== ROTATE POSITION START ===");
        BackroomsMod.LOGGER.info("Input position: x={}, y={}, z={}", pos[0], pos[1], pos[2]);
        BackroomsMod.LOGGER.info("Rotations to apply: {}", rotations);
        
        int x = pos[0];
        int z = pos[2];
        
        for (int i = 0; i < rotations; i++) {
            // 90-degree rotation around Y-axis: (x, z) -> (-z, x)
            int newX = -z;
            int newZ = x;
            BackroomsMod.LOGGER.debug("Rotation {}: ({}, {}) -> ({}, {})", i, x, z, newX, newZ);
            x = newX;
            z = newZ;
        }
        
        pos[0] = x;
        pos[2] = z;
        
        BackroomsMod.LOGGER.info("Output position: x={}, y={}, z={}", pos[0], pos[1], pos[2]);
        BackroomsMod.LOGGER.info("=== ROTATE POSITION END ===");
    }
    
    /**
     * Rotates a direction by a given number of 90-degree rotations
     */
    public static Direction rotateDirection(Direction direction, int rotations) {
        BackroomsMod.LOGGER.info("=== ROTATE DIRECTION START ===");
        BackroomsMod.LOGGER.info("Input direction: {}", direction);
        BackroomsMod.LOGGER.info("Rotations to apply: {}", rotations);
        
        if (direction == null) {
            BackroomsMod.LOGGER.info("Direction is null, returning NORTH");
            return Direction.NORTH;
        }
        
        // Only rotate horizontal directions
        if (direction.getAxis() == Direction.Axis.Y) {
            BackroomsMod.LOGGER.info("Direction is vertical ({}), not rotating", direction);
            return direction;
        }
        
        Direction newDir = direction;
        for (int i = 0; i < rotations; i++) {
            newDir = newDir.getClockWise();
            BackroomsMod.LOGGER.debug("Rotation {}: {} -> {}", i, direction, newDir);
        }
        
        BackroomsMod.LOGGER.info("Output direction: {}", newDir);
        BackroomsMod.LOGGER.info("=== ROTATE DIRECTION END ===");
        return newDir;
    }
    
    /**
     * Converts a direction to its string representation
     */
    public static String directionToString(Direction direction) {
        if (direction == null) {
            return "north";
        }
        return direction.getName();
    }
    
    /**
     * Calculates the number of 90-degree rotations needed to align two directions
     * Returns the rotations needed for direction2 to face the opposite of direction1
     */
    public static int calculateRotationsToAlign(Direction dir1, Direction dir2) {
        BackroomsMod.LOGGER.info("=== CALCULATE ROTATIONS TO ALIGN START ===");
        BackroomsMod.LOGGER.info("Direction 1 (first door facing): {}", dir1);
        BackroomsMod.LOGGER.info("Direction 2 (second door facing): {}", dir2);
        
        // We want dir2 to face opposite of dir1
        Direction targetDir = dir1.getOpposite();
        BackroomsMod.LOGGER.info("Target direction (opposite of dir1): {}", targetDir);
        
        Direction currentDir = dir2;
        int rotations = 0;
        
        while (currentDir != targetDir && rotations < 4) {
            currentDir = currentDir.getClockWise();
            rotations++;
            BackroomsMod.LOGGER.debug("Rotation {}: currentDir = {}", rotations, currentDir);
        }
        
        BackroomsMod.LOGGER.info("Calculated rotations needed: {}", rotations);
        BackroomsMod.LOGGER.info("=== CALCULATE ROTATIONS TO ALIGN END ===");
        return rotations;
    }
}
