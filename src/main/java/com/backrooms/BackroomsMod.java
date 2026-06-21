package com.backrooms;

import com.backrooms.event.BlockPlacementHandler;
import com.backrooms.event.DoorInteractionHandler;
import net.fabricmc.api.ModInitializer;

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
		
		LOGGER.info("Scanning system registered!");
	}
}
