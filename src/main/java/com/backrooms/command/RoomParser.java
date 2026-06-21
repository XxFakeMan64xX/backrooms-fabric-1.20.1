package com.backrooms.command;

import com.backrooms.BackroomsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class RoomParser {
    
    public static RoomData parseRoomFromJson(String json) {
        BackroomsMod.LOGGER.info("=== ROOM PARSER START ===");
        RoomData roomData = new RoomData();
        
        try {
            // Extract blocks array from JSON
            int blocksStart = json.indexOf("\"blocks\": [");
            if (blocksStart == -1) {
                BackroomsMod.LOGGER.error("Invalid room JSON format - no blocks array found");
                return roomData;
            }
            
            blocksStart = json.indexOf("[", blocksStart) + 1;
            int blocksEnd = json.indexOf("]", blocksStart);
            String blocksJson = json.substring(blocksStart, blocksEnd);
            BackroomsMod.LOGGER.info("Extracted blocks JSON string, length: {} chars", blocksJson.length());
            
            // Parse individual block entries
            String[] blockEntries = blocksJson.split("\\},\\s*\\{");
            BackroomsMod.LOGGER.info("Found {} block entries in JSON", blockEntries.length);
            
            int doorCount = 0;
            int blockCount = 0;
            
            for (String entry : blockEntries) {
                entry = entry.replace("{", "").replace("}", "").trim();
                if (entry.isEmpty()) continue;
                
                int relX = extractInt(entry, "relX");
                int relY = extractInt(entry, "relY");
                int relZ = extractInt(entry, "relZ");
                String blockId = extractString(entry, "block");
                String facing = extractString(entry, "facing");
                String half = extractString(entry, "half");
                String open = extractString(entry, "open");
                String powered = extractString(entry, "powered");
                
                RoomData.BlockEntry blockEntry = new RoomData.BlockEntry(
                    relX, relY, relZ, blockId, facing, half, open, powered
                );
                roomData.addBlock(blockEntry);
                blockCount++;
                
                // Check if this is a door
                if (blockId.equals("minecraft:dark_oak_door")) {
                    Direction doorFacing = parseDirection(facing);
                    boolean isUpper = "upper".equalsIgnoreCase(half);
                    
                    BackroomsMod.LOGGER.debug("Found door block at relPos=({}, {}, {}), facing={}, half={}", 
                        relX, relY, relZ, facing, half);
                    
                    // Only add the lower half of doors to avoid duplicates
                    if (!isUpper) {
                        BlockPos doorPos = new BlockPos(relX, relY, relZ);
                        roomData.addDoor(new RoomData.DoorEntry(doorPos, doorFacing, false));
                        doorCount++;
                        BackroomsMod.LOGGER.info("Added door #{} at relPos={}, facing={}", 
                            doorCount, doorPos, doorFacing);
                    } else {
                        BackroomsMod.LOGGER.debug("Skipping upper half of door at relPos=({}, {}, {})", 
                            relX, relY, relZ);
                    }
                }
            }
            
            BackroomsMod.LOGGER.info("=== ROOM PARSER END ===");
            BackroomsMod.LOGGER.info("Parsed room with {} blocks and {} doors", blockCount, doorCount);
                
        } catch (Exception e) {
            BackroomsMod.LOGGER.error("Failed to parse room JSON", e);
        }
        
        return roomData;
    }
    
    private static int extractInt(String json, String key) {
        String searchKey = "\"" + key + "\": ";
        int start = json.indexOf(searchKey);
        if (start == -1) return 0;
        
        start += searchKey.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.length();
        
        try {
            return Integer.parseInt(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private static String extractString(String json, String key) {
        String searchKey = "\"" + key + "\": \"";
        int start = json.indexOf(searchKey);
        if (start == -1) return "";
        
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        
        return json.substring(start, end);
    }
    
    private static Direction parseDirection(String facing) {
        if (facing == null || facing.isEmpty()) {
            BackroomsMod.LOGGER.debug("Facing is null/empty, defaulting to NORTH");
            return Direction.NORTH;
        }
        Direction result = switch (facing.toLowerCase()) {
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> Direction.NORTH;
        };
        BackroomsMod.LOGGER.debug("Parsed direction '{}' as {}", facing, result);
        return result;
    }
}
