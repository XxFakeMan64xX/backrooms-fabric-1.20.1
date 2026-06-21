package com.backrooms;

import com.backrooms.command.RandomRoomCommand;
import com.backrooms.command.ChainRoomCommand;
import com.backrooms.event.BlockPlacementHandler;
import com.backrooms.event.DoorInteractionHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackroomsMod implements ModInitializer {
	public static final String MOD_ID = "backrooms";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing The Backrooms mod...");
		ModBlocks.register();
		ModDimensions.register();
		
		// Register scanning events
		BlockPlacementHandler.register();
		DoorInteractionHandler.register();
		
		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			RandomRoomCommand.register(dispatcher);
			ChainRoomCommand.register(dispatcher);
		});
		
		LOGGER.info("Scanning system registered!");
	}
}
