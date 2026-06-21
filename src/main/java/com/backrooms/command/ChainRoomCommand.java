package com.backrooms.command;

import com.backrooms.BackroomsMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChainRoomCommand {
    private static final String SCAN_OUTPUT_DIR = "backrooms_scans";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("chainroom")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("depth", IntegerArgumentType.integer(1, 50))
                    .executes(context -> chainRooms(context, IntegerArgumentType.getInteger(context, "depth")))
                )
        );
    }

    private static int chainRooms(CommandContext<CommandSourceStack> context, int maxDepth) {
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
                source.sendFailure(net.minecraft.network.chat.Component.literal("Need at least 1 room file in " + SCAN_OUTPUT_DIR));
                return 0;
            }

            Random random = new Random();

            // Select first random room
            Path firstRoomFile = roomFiles.get(random.nextInt(roomFiles.size()));
            BackroomsMod.LOGGER.info("=== RECURSIVE ROOM CHAINING START ===");
            BackroomsMod.LOGGER.info("Selected first room: {}", firstRoomFile.getFileName());
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Starting recursive room chain with: " + firstRoomFile.getFileName()), true);

            // Load and parse first room
            String firstRoomJson = Files.readString(firstRoomFile);
            RoomData firstRoomData = RoomParser.parseRoomFromJson(firstRoomJson);

            if (firstRoomData.getDoors().isEmpty()) {
                source.sendFailure(net.minecraft.network.chat.Component.literal("First room has no doors"));
                return 0;
            }

            // Place first room at player position
            BackroomsMod.LOGGER.info("Placing first room at {}", playerPos);
            placeRoom(firstRoomData, level, playerPos, 0);

            final int[] totalRoomsPlaced = {1};

            // Recursively place rooms for each door
            for (RoomData.DoorEntry door : firstRoomData.getDoors()) {
                BlockPos doorWorldPos = new BlockPos(
                    playerPos.getX() + door.position.getX(),
                    playerPos.getY() + door.position.getY(),
                    playerPos.getZ() + door.position.getZ()
                );
                
                totalRoomsPlaced[0] += placeRoomRecursively(
                    level, 
                    doorWorldPos, 
                    door.facing, 
                    roomFiles, 
                    1, 
                    maxDepth, 
                    random
                );
            }

            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Recursive chain complete! Total rooms placed: " + totalRoomsPlaced[0]), true);
            BackroomsMod.LOGGER.info("=== RECURSIVE ROOM CHAINING COMPLETE ===");
            BackroomsMod.LOGGER.info("Total rooms placed: {}", totalRoomsPlaced[0]);
            return 1;
        } catch (Exception e) {
            BackroomsMod.LOGGER.error("Failed to chain rooms", e);
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Failed to chain rooms: " + e.getMessage()));
            return 0;
        }
    }

    private static int placeRoomRecursively(ServerLevel level, BlockPos doorWorldPos, Direction doorFacing, 
                                            List<Path> roomFiles, int depth, int maxDepth, Random random) throws IOException {
        if (depth >= maxDepth) {
            BackroomsMod.LOGGER.info("Reached max recursion depth at {}", depth);
            return 0;
        }

        BackroomsMod.LOGGER.info("=== RECURSIVE PLACEMENT DEPTH {} ===", depth);
        BackroomsMod.LOGGER.info("Door world position: {}", doorWorldPos);
        BackroomsMod.LOGGER.info("Door facing: {}", doorFacing);

        // Select a random room
        Path roomFile = roomFiles.get(random.nextInt(roomFiles.size()));
        BackroomsMod.LOGGER.info("Selected room: {}", roomFile.getFileName());

        // Load and parse room
        String roomJson = Files.readString(roomFile);
        RoomData roomData = RoomParser.parseRoomFromJson(roomJson);

        if (roomData.getDoors().isEmpty()) {
            BackroomsMod.LOGGER.info("Room has no doors, stopping recursion");
            return 0;
        }

        // Select a random door from this room
        RoomData.DoorEntry selectedDoor = roomData.getDoors().get(random.nextInt(roomData.getDoors().size()));
        BackroomsMod.LOGGER.info("Selected door at relPos={}, facing={}", selectedDoor.position, selectedDoor.facing);

        // Calculate rotation needed to align doors
        int rotations = RoomRotator.calculateRotationsToAlign(doorFacing, selectedDoor.facing);
        BackroomsMod.LOGGER.info("Calculated rotations: {}", rotations);

        // Calculate room origin
        BlockPos roomOrigin = calculateSecondRoomPosition(
            doorWorldPos, 
            doorFacing, 
            selectedDoor.position, 
            rotations
        );
        BackroomsMod.LOGGER.info("Room origin: {}", roomOrigin);

        // Place the room
        placeRoom(roomData, level, roomOrigin, rotations);

        int roomsPlaced = 1;

        // Recursively place rooms for each door in the new room
        for (RoomData.DoorEntry door : roomData.getDoors()) {
            // Calculate the world position of this door after rotation
            int[] rotatedPos = {door.position.getX(), door.position.getY(), door.position.getZ()};
            RoomRotator.rotatePosition(rotatedPos, rotations);
            
            BlockPos newDoorWorldPos = new BlockPos(
                roomOrigin.getX() + rotatedPos[0],
                roomOrigin.getY() + rotatedPos[1],
                roomOrigin.getZ() + rotatedPos[2]
            );

            // Rotate the door's facing direction
            Direction newDoorFacing = selectedDoor.facing;
            if (door.facing != null) {
                Direction originalDir = door.facing;
                if (originalDir.getAxis() != Direction.Axis.Y) {
                    newDoorFacing = RoomRotator.rotateDirection(originalDir, rotations);
                }
            }

            roomsPlaced += placeRoomRecursively(
                level, 
                newDoorWorldPos, 
                newDoorFacing, 
                roomFiles, 
                depth + 1, 
                maxDepth, 
                random
            );
        }

        return roomsPlaced;
    }

    private static BlockPos calculateSecondRoomPosition(BlockPos firstDoorWorldPos, Direction firstDoorFacing, 
                                                       BlockPos secondDoorRelPos, int rotations) {
        BackroomsMod.LOGGER.info("=== CALCULATE SECOND ROOM POSITION START ===");
        
        // The second room's door should be placed at the same position as the first room's door
        BlockPos targetDoorPos = firstDoorWorldPos;
        BackroomsMod.LOGGER.info("Target door position (same as first door): {}", targetDoorPos);
        BackroomsMod.LOGGER.info("First door facing: {}", firstDoorFacing);
        BackroomsMod.LOGGER.info("Offset from first door: {}", firstDoorFacing.getNormal());

        // We need to find where the second room's origin should be so that its door
        // ends up at targetDoorPos after rotation
        
        // First, apply the forward rotation to the second door's relative position
        // to see where it will end up after rotation
        int[] rotatedDoorPos = {secondDoorRelPos.getX(), secondDoorRelPos.getY(), secondDoorRelPos.getZ()};
        BackroomsMod.LOGGER.info("Second door relative position (before forward rotation): x={}, y={}, z={}", 
            rotatedDoorPos[0], rotatedDoorPos[1], rotatedDoorPos[2]);
        
        // Apply forward rotation
        RoomRotator.rotatePosition(rotatedDoorPos, rotations);
        BackroomsMod.LOGGER.info("Second door relative position (after forward rotation): x={}, y={}, z={}", 
            rotatedDoorPos[0], rotatedDoorPos[1], rotatedDoorPos[2]);

        // The second room's origin should be at targetDoorPos minus the rotated door position
        int originX = targetDoorPos.getX() - rotatedDoorPos[0];
        int originY = targetDoorPos.getY() - rotatedDoorPos[1];
        int originZ = targetDoorPos.getZ() - rotatedDoorPos[2];
        
        BackroomsMod.LOGGER.info("Second room origin calculation:");
        BackroomsMod.LOGGER.info("  targetDoorPos.x - rotatedDoorPos[0] = {} - {} = {}", targetDoorPos.getX(), rotatedDoorPos[0], originX);
        BackroomsMod.LOGGER.info("  targetDoorPos.y - rotatedDoorPos[1] = {} - {} = {}", targetDoorPos.getY(), rotatedDoorPos[1], originY);
        BackroomsMod.LOGGER.info("  targetDoorPos.z - rotatedDoorPos[2] = {} - {} = {}", targetDoorPos.getZ(), rotatedDoorPos[2], originZ);
        
        BlockPos result = new BlockPos(originX, originY, originZ);
        BackroomsMod.LOGGER.info("=== CALCULATE SECOND ROOM POSITION END ===");
        BackroomsMod.LOGGER.info("Final second room origin: {}", result);
        
        return result;
    }

    private static void placeRoom(RoomData roomData, ServerLevel level, BlockPos origin, int rotations) {
        BackroomsMod.LOGGER.info("=== PLACE ROOM START ===");
        BackroomsMod.LOGGER.info("Room origin: {}", origin);
        BackroomsMod.LOGGER.info("Rotations to apply: {}", rotations);
        BackroomsMod.LOGGER.info("Total blocks to place: {}", roomData.getBlocks().size());
        
        int blocksPlaced = 0;
        int doorBlocksPlaced = 0;

        for (RoomData.BlockEntry block : roomData.getBlocks()) {
            // Skip air blocks - don't place them
            if (block.blockId.equals("minecraft:air")) {
                continue;
            }

            // Rotate the block position
            int[] pos = {block.relX, block.relY, block.relZ};
            BackroomsMod.LOGGER.debug("Block {} original relPos: x={}, y={}, z={}", blocksPlaced, pos[0], pos[1], pos[2]);
            RoomRotator.rotatePosition(pos, rotations);
            BackroomsMod.LOGGER.debug("Block {} rotated relPos: x={}, y={}, z={}", blocksPlaced, pos[0], pos[1], pos[2]);

            BlockPos worldPos = new BlockPos(
                origin.getX() + pos[0],
                origin.getY() + pos[1],
                origin.getZ() + pos[2]
            );
            BackroomsMod.LOGGER.debug("Block {} world position: {}", blocksPlaced, worldPos);

            // Rotate the facing direction if applicable
            String newFacing = block.facing;
            if (!block.facing.isEmpty()) {
                Direction originalDir = Direction.byName(block.facing.toLowerCase());
                if (originalDir != null && originalDir.getAxis() != Direction.Axis.Y) {
                    Direction rotatedDir = RoomRotator.rotateDirection(originalDir, rotations);
                    newFacing = RoomRotator.directionToString(rotatedDir);
                    BackroomsMod.LOGGER.debug("Block {} facing rotation: {} -> {}", blocksPlaced, block.facing, newFacing);
                }
            }

            BlockState state = parseBlockState(block.blockId, newFacing, block.half, block.open, block.powered);
            level.setBlock(worldPos, state, 3);
            blocksPlaced++;
            
            if (block.blockId.equals("minecraft:dark_oak_door")) {
                doorBlocksPlaced++;
                BackroomsMod.LOGGER.info("Door block placed at {} with facing: {}, half: {}", worldPos, newFacing, block.half);
            }
        }

        BackroomsMod.LOGGER.info("=== PLACE ROOM END ===");
        BackroomsMod.LOGGER.info("Total blocks placed: {}", blocksPlaced);
        BackroomsMod.LOGGER.info("Door blocks placed: {}", doorBlocksPlaced);
        BackroomsMod.LOGGER.info("Rotations applied: {}", rotations);
    }

    private static BlockState parseBlockState(String blockId, String facing, String half, String open, String powered) {
        if (blockId.equals("minecraft:air")) {
            return Blocks.AIR.defaultBlockState();
        } else if (blockId.equals("backrooms:wallpaper")) {
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse("backrooms:wallpaper");
            net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(loc);
            return block != null ? block.defaultBlockState() : Blocks.AIR.defaultBlockState();
        } else {
            try {
                net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(blockId);
                if (loc != null) {
                    net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(loc);
                    if (block != null) {
                        BlockState state = block.defaultBlockState();
                        state = applyBlockProperties(state, block, facing, half, open, powered);
                        return state;
                    }
                }
            } catch (Exception e) {
                BackroomsMod.LOGGER.error("Failed to parse block ID: {}", blockId);
            }
        }
        
        return Blocks.AIR.defaultBlockState();
    }

    @SuppressWarnings("unchecked")
    private static BlockState applyBlockProperties(BlockState state, net.minecraft.world.level.block.Block block, 
                                                   String facing, String half, String open, String powered) {
        try {
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
            
            if (!open.isEmpty()) {
                net.minecraft.world.level.block.state.properties.BooleanProperty openProp = 
                    (net.minecraft.world.level.block.state.properties.BooleanProperty) block.getStateDefinition().getProperty("open");
                if (openProp != null) {
                    state = state.setValue(openProp, Boolean.parseBoolean(open));
                }
            }
            
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
}
