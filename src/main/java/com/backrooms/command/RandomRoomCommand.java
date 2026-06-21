package com.backrooms.command;

import com.backrooms.BackroomsMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomRoomCommand {
    private static final String SCAN_OUTPUT_DIR = "backrooms_scans";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("randomroom")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2
                .then(Commands.literal("list")
                    .executes(RandomRoomCommand::listRooms)
                )
                .then(Commands.literal("generate")
                    .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .executes(RandomRoomCommand::generateSpecificRoom)
                    )
                )
                .then(Commands.literal("rename")
                    .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .then(Commands.argument("newname", StringArgumentType.string())
                            .executes(RandomRoomCommand::renameRoom)
                        )
                    )
                )
                .executes(RandomRoomCommand::generateRandomRoom)
        );
    }

    private static int listRooms(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();
            List<Path> roomFiles = getRoomFiles();
            
            if (roomFiles.isEmpty()) {
                source.sendFailure(net.minecraft.network.chat.Component.literal("No scanned rooms found in " + SCAN_OUTPUT_DIR));
                return 0;
            }

            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Available rooms (" + roomFiles.size() + "):"), true);
            
            for (int i = 0; i < roomFiles.size(); i++) {
                Path file = roomFiles.get(i);
                String filename = file.getFileName().toString();
                final int roomIndex = i + 1;
                final String roomFilename = filename;
                source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(roomIndex + ". " + roomFilename), false);
            }
            
            return 1;
        } catch (Exception e) {
            BackroomsMod.LOGGER.error("Failed to list rooms", e);
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Failed to list rooms: " + e.getMessage()));
            return 0;
        }
    }

    private static int generateSpecificRoom(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();
            ServerPlayer player = source.getPlayer();
            
            if (player == null) {
                source.sendFailure(net.minecraft.network.chat.Component.literal("This command can only be run by a player"));
                return 0;
            }

            int index = IntegerArgumentType.getInteger(context, "index");
            List<Path> roomFiles = getRoomFiles();
            
            if (roomFiles.isEmpty()) {
                source.sendFailure(net.minecraft.network.chat.Component.literal("No scanned rooms found in " + SCAN_OUTPUT_DIR));
                return 0;
            }
            
            if (index < 1 || index > roomFiles.size()) {
                source.sendFailure(net.minecraft.network.chat.Component.literal("Invalid room index. Use /randomroom list to see available rooms."));
                return 0;
            }

            ServerLevel level = player.serverLevel();
            BlockPos playerPos = player.blockPosition();
            Path selectedFile = roomFiles.get(index - 1);
            
            BackroomsMod.LOGGER.info("Selected room file by index {}: {}", index, selectedFile);
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Generating room from: " + selectedFile.getFileName()), true);

            // Load and place the room
            String content = Files.readString(selectedFile);
            placeRoomFromJson(content, level, playerPos);

            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Room generated successfully!"), true);
            return 1;
        } catch (Exception e) {
            BackroomsMod.LOGGER.error("Failed to generate specific room", e);
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Failed to generate room: " + e.getMessage()));
            return 0;
        }
    }

    private static int renameRoom(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();
            
            int index = IntegerArgumentType.getInteger(context, "index");
            String newName = StringArgumentType.getString(context, "newname");
            
            List<Path> roomFiles = getRoomFiles();
            
            if (roomFiles.isEmpty()) {
                source.sendFailure(net.minecraft.network.chat.Component.literal("No scanned rooms found in " + SCAN_OUTPUT_DIR));
                return 0;
            }
            
            if (index < 1 || index > roomFiles.size()) {
                source.sendFailure(net.minecraft.network.chat.Component.literal("Invalid room index. Use /randomroom list to see available rooms."));
                return 0;
            }

            Path oldFile = roomFiles.get(index - 1);
            String oldName = oldFile.getFileName().toString();
            
            // Ensure new name ends with .json
            if (!newName.endsWith(".json")) {
                newName = newName + ".json";
            }
            
            Path newFile = oldFile.resolveSibling(newName);
            
            // Rename the file
            Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
            
            final String finalOldName = oldName;
            final String finalNewName = newName;
            BackroomsMod.LOGGER.info("Renamed room from {} to {}", finalOldName, finalNewName);
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Renamed room from " + finalOldName + " to " + finalNewName), true);
            
            return 1;
        } catch (Exception e) {
            BackroomsMod.LOGGER.error("Failed to rename room", e);
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Failed to rename room: " + e.getMessage()));
            return 0;
        }
    }

    private static int generateRandomRoom(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();
            ServerPlayer player = source.getPlayer();
            
            if (player == null) {
                source.sendFailure(net.minecraft.network.chat.Component.literal("This command can only be run by a player"));
                return 0;
            }

            ServerLevel level = player.serverLevel();
            BlockPos playerPos = player.blockPosition();

            // Get all room files
            List<Path> roomFiles = getRoomFiles();
            if (roomFiles.isEmpty()) {
                source.sendFailure(net.minecraft.network.chat.Component.literal("No scanned rooms found in " + SCAN_OUTPUT_DIR));
                return 0;
            }

            // Select a random room
            Random random = new Random();
            Path selectedFile = roomFiles.get(random.nextInt(roomFiles.size()));
            
            BackroomsMod.LOGGER.info("Selected random room file: {}", selectedFile);
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Generating room from: " + selectedFile.getFileName()), true);

            // Load and place the room
            String content = Files.readString(selectedFile);
            placeRoomFromJson(content, level, playerPos);

            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Room generated successfully!"), true);
            return 1;
        } catch (Exception e) {
            BackroomsMod.LOGGER.error("Failed to generate random room", e);
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Failed to generate room: " + e.getMessage()));
            return 0;
        }
    }

    private static List<Path> getRoomFiles() throws IOException {
        List<Path> roomFiles = new ArrayList<>();
        Path dir = Paths.get(SCAN_OUTPUT_DIR);
        
        if (!Files.exists(dir)) {
            BackroomsMod.LOGGER.info("Scan directory does not exist: {}", SCAN_OUTPUT_DIR);
            return roomFiles;
        }

        for (Path file : Files.newDirectoryStream(dir, "*.json")) {
            roomFiles.add(file);
        }

        BackroomsMod.LOGGER.info("Found {} room files", roomFiles.size());
        return roomFiles;
    }

    private static void placeRoomFromJson(String json, ServerLevel level, BlockPos origin) {
        try {
            // Parse the JSON manually (simple parsing for the specific format)
            // Extract blocks array from JSON
            int blocksStart = json.indexOf("\"blocks\": [");
            if (blocksStart == -1) {
                BackroomsMod.LOGGER.error("Invalid room JSON format - no blocks array found");
                return;
            }

            blocksStart = json.indexOf("[", blocksStart) + 1;
            int blocksEnd = json.indexOf("]", blocksStart);
            String blocksJson = json.substring(blocksStart, blocksEnd);

            // Parse individual block entries
            String[] blockEntries = blocksJson.split("\\},\\s*\\{");
            int blocksPlaced = 0;

            for (String entry : blockEntries) {
                // Clean up the entry
                entry = entry.replace("{", "").replace("}", "").trim();
                
                if (entry.isEmpty()) continue;

                // Parse the block data
                int relX = extractInt(entry, "relX");
                int relY = extractInt(entry, "relY");
                int relZ = extractInt(entry, "relZ");
                String blockId = extractString(entry, "block");

                BlockPos pos = new BlockPos(
                    origin.getX() + relX,
                    origin.getY() + relY,
                    origin.getZ() + relZ
                );

                BlockState state = parseBlockState(blockId, entry);
                level.setBlock(pos, state, 3);
                blocksPlaced++;
            }

            BackroomsMod.LOGGER.info("Placed {} blocks from room", blocksPlaced);
        } catch (Exception e) {
            BackroomsMod.LOGGER.error("Failed to parse room JSON", e);
        }
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

    private static BlockState parseBlockState(String blockId, String entry) {
        // Parse the block ID and create the appropriate block state
        if (blockId.equals("minecraft:air")) {
            return Blocks.AIR.defaultBlockState();
        } else if (blockId.equals("backrooms:wallpaper")) {
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse("backrooms:wallpaper");
            net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(loc);
            return block != null ? block.defaultBlockState() : Blocks.AIR.defaultBlockState();
        } else {
            // Try to parse as a regular block ID
            try {
                net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(blockId);
                if (loc != null) {
                    net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(loc);
                    if (block != null) {
                        BlockState state = block.defaultBlockState();
                        
                        // Apply block state properties from the JSON entry
                        state = applyBlockProperties(state, block, entry);
                        
                        return state;
                    }
                }
            } catch (Exception e) {
                BackroomsMod.LOGGER.error("Failed to parse block ID: {}", blockId);
            }
        }
        
        // Fallback to air if we can't parse the block
        return Blocks.AIR.defaultBlockState();
    }

    private static BlockState applyBlockProperties(BlockState state, net.minecraft.world.level.block.Block block, String entry) {
        // Extract common block properties from the JSON entry
        String facing = extractString(entry, "facing");
        String half = extractString(entry, "half");
        String open = extractString(entry, "open");
        String powered = extractString(entry, "powered");
        
        try {
            // Apply facing property (for doors, trapdoors, etc.)
            if (!facing.isEmpty()) {
                net.minecraft.world.level.block.state.properties.DirectionProperty facingProp = 
                    (net.minecraft.world.level.block.state.properties.DirectionProperty) block.getStateDefinition().getProperty("facing");
                if (facingProp != null) {
                    net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(facing.toLowerCase());
                    if (dir != null) {
                        state = state.setValue(facingProp, dir);
                    }
                }
            }
            
            // Apply half property (for doors)
            if (!half.isEmpty()) {
                net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.state.properties.DoubleBlockHalf> halfProp = 
                    (net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.state.properties.DoubleBlockHalf>) block.getStateDefinition().getProperty("half");
                if (halfProp != null) {
                    if (half.equalsIgnoreCase("upper")) {
                        state = state.setValue(halfProp, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
                    } else if (half.equalsIgnoreCase("lower")) {
                        state = state.setValue(halfProp, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
                    }
                }
            }
            
            // Apply open property (for trapdoors)
            if (!open.isEmpty()) {
                net.minecraft.world.level.block.state.properties.BooleanProperty openProp = 
                    (net.minecraft.world.level.block.state.properties.BooleanProperty) block.getStateDefinition().getProperty("open");
                if (openProp != null) {
                    state = state.setValue(openProp, Boolean.parseBoolean(open));
                }
            }
            
            // Apply powered property (for doors)
            if (!powered.isEmpty()) {
                net.minecraft.world.level.block.state.properties.BooleanProperty poweredProp = 
                    (net.minecraft.world.level.block.state.properties.BooleanProperty) block.getStateDefinition().getProperty("powered");
                if (poweredProp != null) {
                    state = state.setValue(poweredProp, Boolean.parseBoolean(powered));
                }
            }
        } catch (Exception e) {
            BackroomsMod.LOGGER.error("Failed to apply block properties", e);
        }
        
        return state;
    }
}
