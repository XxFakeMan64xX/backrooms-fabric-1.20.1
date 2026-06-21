package com.backrooms.scanner;

import com.backrooms.BackroomsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public class SpaceScanner {
    
    private static final int MAX_SCAN_SIZE = 1000; // Maximum blocks to scan to prevent performance issues
    private static final String SCAN_OUTPUT_DIR = "backrooms_scans";
    private static final long SCAN_COOLDOWN_MS = 1000; // Cooldown between scans to prevent duplicates
    private static long lastScanTime = 0;
    
    /**
     * Scans for enclosed spaces starting from a given position
     * @param level The world level
     * @param startPos The starting position for the scan (player position)
     * @return true if an enclosed space was found and saved, false otherwise
     */
    public static boolean scanAndSaveEnclosedSpace(Level level, BlockPos startPos) {
        // Check cooldown to prevent duplicate scans
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime < SCAN_COOLDOWN_MS) {
            BackroomsMod.LOGGER.info("Scan skipped - cooldown active ({} ms since last scan)", currentTime - lastScanTime);
            return false;
        }
        lastScanTime = currentTime;
        
        BackroomsMod.LOGGER.info("=== STARTING SPACE SCAN ===");
        BackroomsMod.LOGGER.info("Scan start position (player): {}", startPos);
        BackroomsMod.LOGGER.info("Block at start position: {}", level.getBlockState(startPos));
        
        Set<BlockPos> enclosedSpace = findEnclosedSpace(level, startPos);
        
        BackroomsMod.LOGGER.info("Flood-fill found {} blocks", enclosedSpace.size());
        
        // If we hit the max scan size, the space is too large or not truly enclosed
        if (enclosedSpace.size() >= MAX_SCAN_SIZE) {
            BackroomsMod.LOGGER.info("Scan hit maximum size limit ({} blocks) - space is too large or not enclosed", MAX_SCAN_SIZE);
            BackroomsMod.LOGGER.info("=== SCAN COMPLETE: FAILURE ===");
            return false;
        }
        
        if (!enclosedSpace.isEmpty()) {
            BackroomsMod.LOGGER.info("Checking if space is truly enclosed...");
            boolean trulyEnclosed = isTrulyEnclosed(level, enclosedSpace);
            BackroomsMod.LOGGER.info("Is truly enclosed: {}", trulyEnclosed);
            
            if (trulyEnclosed) {
                BackroomsMod.LOGGER.info("Checking if space has a door or trapdoor...");
                boolean hasDoor = hasDoorOrTrapdoor(level, enclosedSpace);
                BackroomsMod.LOGGER.info("Has door/trapdoor: {}", hasDoor);
                
                if (hasDoor) {
                    saveSpaceToFile(enclosedSpace, level, startPos);
                    BackroomsMod.LOGGER.info("Found and saved enclosed space with {} blocks", enclosedSpace.size());
                    BackroomsMod.LOGGER.info("=== SCAN COMPLETE: SUCCESS ===");
                    return true;
                } else {
                    BackroomsMod.LOGGER.info("Space is enclosed but has no door or trapdoor - skipping");
                }
            } else {
                BackroomsMod.LOGGER.info("Space found but not truly enclosed (has openings to outside)");
            }
        } else {
            BackroomsMod.LOGGER.info("No enclosed space found - flood-fill returned empty set");
        }
        
        BackroomsMod.LOGGER.info("=== SCAN COMPLETE: FAILURE ===");
        return false;
    }
    
    /**
     * Uses flood-fill algorithm to find all connected air blocks
     */
    private static Set<BlockPos> findEnclosedSpace(Level level, BlockPos startPos) {
        BackroomsMod.LOGGER.info("Starting flood-fill algorithm from position: {}", startPos);
        
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> enclosedSpace = new HashSet<>();
        Set<BlockPos> queue = new HashSet<>();
        
        queue.add(startPos);
        BackroomsMod.LOGGER.info("Initial queue size: 1");
        
        int iterations = 0;
        while (!queue.isEmpty() && enclosedSpace.size() < MAX_SCAN_SIZE) {
            iterations++;
            BlockPos current = queue.iterator().next();
            queue.remove(current);
            
            if (visited.contains(current)) {
                continue;
            }
            
            visited.add(current);
            
            // Check if this position is air or passable
            boolean passable = isAirOrPassable(level, current);
            BackroomsMod.LOGGER.debug("Checking position {}: passable={}, block={}", current, passable, level.getBlockState(current));
            
            if (passable) {
                enclosedSpace.add(current);
                
                // Add neighbors to queue
                queue.add(current.above());
                queue.add(current.below());
                queue.add(current.north());
                queue.add(current.south());
                queue.add(current.east());
                queue.add(current.west());
            } else {
                // For solid blocks, add them to the space but don't continue flood-fill through them
                enclosedSpace.add(current);
            }
        }
        
        BackroomsMod.LOGGER.info("Flood-fill completed: {} iterations, {} blocks found, {} visited", iterations, enclosedSpace.size(), visited.size());
        return enclosedSpace;
    }
    
    /**
     * Checks if a position is air or passable
     */
    private static boolean isAirOrPassable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.blocksMotion();
    }
    
    /**
     * Verifies that the space is truly enclosed by checking all surrounding blocks
     */
    private static boolean isTrulyEnclosed(Level level, Set<BlockPos> space) {
        BackroomsMod.LOGGER.info("Checking if space is truly enclosed ({} blocks to check)", space.size());
        
        int openingsChecked = 0;
        int potentialOpenings = 0;
        
        // Check if the space has any openings to the outside
        for (BlockPos pos : space) {
            // Check all 6 directions
            BlockPos[] neighbors = {
                pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()
            };
            
            for (BlockPos neighbor : neighbors) {
                // If neighbor is not in our space and is air/passable, it might be an opening
                if (!space.contains(neighbor) && isAirOrPassable(level, neighbor)) {
                    potentialOpenings++;
                    BackroomsMod.LOGGER.debug("Potential opening at {} (neighbor of {})", neighbor, pos);
                    openingsChecked++;
                    
                    // Check if this leads to outside by scanning a bit further
                    boolean leadsOutside = leadsToOutside(level, neighbor, space, 5);
                    BackroomsMod.LOGGER.debug("Position {} leads to outside: {}", neighbor, leadsOutside);
                    
                    if (leadsOutside) {
                        BackroomsMod.LOGGER.info("Found opening to outside at {}", neighbor);
                        return false;
                    }
                }
            }
        }
        
        BackroomsMod.LOGGER.info("Enclosure check complete: {} potential openings checked, none lead to outside", potentialOpenings);
        return true;
    }
    
    /**
     * Checks if the enclosed space contains a door or trapdoor
     */
    private static boolean hasDoorOrTrapdoor(Level level, Set<BlockPos> space) {
        // Check all blocks in the enclosed space
        for (BlockPos pos : space) {
            BlockState state = level.getBlockState(pos);
            if (isDoorOrTrapdoor(state)) {
                BackroomsMod.LOGGER.debug("Found door/trapdoor at {}: {}", pos, state);
                return true;
            }
        }
        
        // Check blocks adjacent to the enclosed space (doors might be solid blocks on the boundary)
        for (BlockPos pos : space) {
            BlockPos[] neighbors = {
                pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()
            };
            
            for (BlockPos neighbor : neighbors) {
                // Only check neighbors that are NOT in the space (these are the boundary blocks)
                if (!space.contains(neighbor)) {
                    BlockState state = level.getBlockState(neighbor);
                    if (isDoorOrTrapdoor(state)) {
                        BackroomsMod.LOGGER.debug("Found door/trapdoor at boundary {}: {}", neighbor, state);
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a block state is a door or trapdoor
     */
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
    
    /**
     * Recursively checks if a position leads to the outside world
     */
    private static boolean leadsToOutside(Level level, BlockPos pos, Set<BlockPos> enclosedSpace, int depth) {
        BackroomsMod.LOGGER.debug("leadsToOutside check: pos={}, depth={}, maxY={}", pos, depth, level.getMaxBuildHeight() - 1);
        
        if (depth <= 0) {
            BackroomsMod.LOGGER.debug("leadsToOutside: depth limit reached at {}", pos);
            return false;
        }
        
        // If we're at sky level, it's outside
        if (pos.getY() >= level.getMaxBuildHeight() - 1) {
            BackroomsMod.LOGGER.info("leadsToOutside: REACHED SKY LEVEL at {}", pos);
            return true;
        }
        
        // If this position is in the enclosed space, we're not going outside
        if (enclosedSpace.contains(pos)) {
            BackroomsMod.LOGGER.debug("leadsToOutside: position {} is in enclosed space", pos);
            return false;
        }
        
        // If it's a solid block, we can't go through
        if (!isAirOrPassable(level, pos)) {
            BackroomsMod.LOGGER.debug("leadsToOutside: position {} is solid block: {}", pos, level.getBlockState(pos));
            return false;
        }
        
        // Check neighbors
        BlockPos[] neighbors = {
            pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()
        };
        
        for (BlockPos neighbor : neighbors) {
            if (leadsToOutside(level, neighbor, enclosedSpace, depth - 1)) {
                BackroomsMod.LOGGER.debug("leadsToOutside: neighbor {} leads to outside", neighbor);
                return true;
            }
        }
        
        BackroomsMod.LOGGER.debug("leadsToOutside: no path to outside found from {}", pos);
        return false;
    }
    
    /**
     * Generates a hash of the room data for duplicate detection
     */
    private static String generateRoomHash(Set<BlockPos> space, Level level, BlockPos playerPos) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Find the minimum coordinates to normalize the room
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            for (BlockPos pos : space) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
            }
            
            // Sort positions to ensure consistent hash regardless of iteration order
            java.util.List<BlockPos> sortedPositions = new java.util.ArrayList<>(space);
            sortedPositions.sort((a, b) -> {
                int yCompare = Integer.compare(a.getY(), b.getY());
                if (yCompare != 0) return yCompare;
                int xCompare = Integer.compare(a.getX(), b.getX());
                if (xCompare != 0) return xCompare;
                return Integer.compare(a.getZ(), b.getZ());
            });
            
            for (BlockPos pos : sortedPositions) {
                // Calculate normalized coordinates (relative to room minimum)
                int normX = pos.getX() - minX;
                int normY = pos.getY() - minY;
                int normZ = pos.getZ() - minZ;
                
                BlockState state = level.getBlockState(pos);
                String blockId;
                
                if (state.isAir()) {
                    blockId = "minecraft:air";
                } else if (state.getBlock() instanceof TrapDoorBlock) {
                    blockId = "minecraft:dark_oak_trapdoor";
                } else if (state.getBlock() instanceof DoorBlock) {
                    blockId = "minecraft:dark_oak_door";
                } else if (isAirOrPassable(level, pos)) {
                    blockId = state.getBlock().toString();
                } else {
                    blockId = "backrooms:wallpaper";
                }
                
                // Hash the block data
                String blockData = normX + "," + normY + "," + normZ + "," + blockId;
                digest.update(blockData.getBytes());
            }
            
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            BackroomsMod.LOGGER.error("Failed to generate room hash", e);
            return "";
        }
    }
    
    /**
     * Checks if a room with the given hash already exists
     */
    private static boolean roomHashExists(String hash) {
        try {
            Path dir = Paths.get(SCAN_OUTPUT_DIR);
            if (!Files.exists(dir)) {
                return false;
            }
            
            // Check all JSON files in the directory
            for (Path file : Files.newDirectoryStream(dir, "*.json")) {
                String content = Files.readString(file);
                if (content.contains("\"hash\": \"" + hash + "\"")) {
                    BackroomsMod.LOGGER.info("Duplicate room detected, skipping save");
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            BackroomsMod.LOGGER.error("Failed to check for duplicate rooms", e);
            return false;
        }
    }
    
    /**
     * Saves the enclosed space data to a file
     */
    private static void saveSpaceToFile(Set<BlockPos> space, Level level, BlockPos playerPos) {
        try {
            // Generate hash for duplicate detection
            String roomHash = generateRoomHash(space, level, playerPos);
            if (roomHash.isEmpty()) {
                BackroomsMod.LOGGER.error("Failed to generate room hash, skipping save");
                return;
            }
            
            // Check if this room already exists
            if (roomHashExists(roomHash)) {
                BackroomsMod.LOGGER.info("Room already exists (hash: {}), skipping save", roomHash);
                return;
            }
            
            // Find the minimum coordinates to normalize the room (same as hash)
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            for (BlockPos pos : space) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
            }
            
            java.io.File dir = new java.io.File(SCAN_OUTPUT_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String filename = SCAN_OUTPUT_DIR + "/enclosed_space_" + System.currentTimeMillis() + ".json";
            FileWriter writer = new FileWriter(filename);
            
            writer.write("{\n");
            writer.write("  \"timestamp\": " + System.currentTimeMillis() + ",\n");
            writer.write("  \"hash\": \"" + roomHash + "\",\n");
            writer.write("  \"roomOrigin\": {\"x\": " + minX + ", \"y\": " + minY + ", \"z\": " + minZ + "},\n");
            writer.write("  \"blockCount\": " + space.size() + ",\n");
            writer.write("  \"blocks\": [\n");
            
            boolean first = true;
            for (BlockPos pos : space) {
                if (!first) {
                    writer.write(",\n");
                }
                
                // Calculate normalized coordinates (relative to room minimum)
                int normX = pos.getX() - minX;
                int normY = pos.getY() - minY;
                int normZ = pos.getZ() - minZ;
                
                BlockState state = level.getBlockState(pos);
                String blockId;
                
                // Map blocks to output format
                if (state.isAir()) {
                    blockId = "minecraft:air";
                } else if (state.getBlock() instanceof TrapDoorBlock) {
                    blockId = "minecraft:dark_oak_trapdoor";
                } else if (state.getBlock() instanceof DoorBlock) {
                    blockId = "minecraft:dark_oak_door";
                } else if (isAirOrPassable(level, pos)) {
                    // Passable blocks (torches, grass, etc.) keep their original block ID
                    blockId = state.getBlock().toString();
                } else {
                    // Solid blocks become wallpaper
                    blockId = "backrooms:wallpaper";
                }
                
                // Build block data string with state properties
                StringBuilder blockData = new StringBuilder();
                blockData.append("{\"relX\": ").append(normX);
                blockData.append(", \"relY\": ").append(normY);
                blockData.append(", \"relZ\": ").append(normZ);
                blockData.append(", \"block\": \"").append(blockId).append("\"");
                
                // Add block state properties for doors, trapdoors, and passable blocks
                if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || isAirOrPassable(level, pos)) {
                    for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
                        Comparable<?> value = state.getValue(property);
                        blockData.append(", \"").append(property.getName()).append("\": \"").append(value).append("\"");
                    }
                }
                
                blockData.append("}");
                writer.write("    " + blockData.toString());
                
                first = false;
            }
            
            writer.write("\n  ]\n");
            writer.write("}\n");
            
            writer.close();
            BackroomsMod.LOGGER.info("Saved enclosed space to {}", filename);
            
        } catch (IOException e) {
            BackroomsMod.LOGGER.error("Failed to save enclosed space to file", e);
        }
    }
}
