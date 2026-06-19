package com.backrooms.client;

import net.fabricmc.api.ClientModInitializer;
import com.backrooms.BackroomsMod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackroomsClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(BackroomsMod.MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing The Backrooms client...");
	}
}
