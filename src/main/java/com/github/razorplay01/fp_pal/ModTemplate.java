package com.github.razorplay01.fp_pal;

import com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem;
import com.github.razorplay01.fp_pal.platform.Platform;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? fabric {
import com.github.razorplay01.fp_pal.platform.fabric.FabricPlatform;
//?} neoforge {
//import com.github.razorplay01.fp_pal.platform.neoforge.NeoforgePlatform;
 //?} forge {
//import com.github.razorplay01.fp_pal.platform.forge.ForgePlatform;
 //?}

@SuppressWarnings("LoggingSimilarMessage")
public class ModTemplate {

	public static final String MOD_ID = /*$ mod_id*/"fp_pal";
	public static final String MOD_VERSION = /*$ mod_version*/"1.1.0";
	public static final String MOD_FRIENDLY_NAME = /*$ mod_name*/"First Person PAL Expansion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Platform PLATFORM = createPlatformInstance();

	public static void onInitialize() {
		LOGGER.info("Initializing {} on {}", MOD_ID, ModTemplate.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
	}

	public static void onInitializeClient() {
		LOGGER.info("Initializing {} Client on {}", MOD_ID, ModTemplate.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
		// Registers the first-person animation layer factory. It has to be in place before any player
		// is created, because PAL builds each player's animation stack from the registered factories.
		FpAnimationSystem.init();
	}

	public static Platform xplat() {
		return PLATFORM;
	}

	private static Platform createPlatformInstance() {
		//? fabric {
		return new FabricPlatform();
		//?} neoforge {
		//return new NeoforgePlatform();
		 //?} forge {
		//return new ForgePlatform();
		 //?}
	}

	public static ResourceLocation id(String path) {
		//? > 1.19.2 {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
		 //?} <= 1.19.2 {
		//return new ResourceLocation(MOD_ID, path);
		//?}
	}

	public static ResourceLocation id(String namespace, String path) {
		//? > 1.19.2 {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
		 //?} <= 1.19.2 {
		//return new ResourceLocation(namespace, path);
		//?}
	}
}
