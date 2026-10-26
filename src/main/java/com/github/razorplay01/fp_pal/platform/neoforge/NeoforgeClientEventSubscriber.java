package com.github.razorplay01.fp_pal.platform.neoforge;

//? neoforge {

import com.github.razorplay01.fp_pal.ModTemplate;
import com.github.razorplay01.fp_pal.command.FpPlayerAnimCommands;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationResources;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

@EventBusSubscriber(modid = ModTemplate.MOD_ID, value = Dist.CLIENT)
public class NeoforgeClientEventSubscriber {
	@SubscribeEvent
	public static void onClientSetup(final FMLClientSetupEvent event) {
		ModTemplate.onInitializeClient();
	}

	// NeoForge has no IdentifiableResourceReloadListener, the plain listener is registered directly.
	@SubscribeEvent
	public static void onRegisterClientReloadListeners(final AddClientReloadListenersEvent event) {
		event.addListener(FpAnimationResources.KEY, new FpAnimationResources());
	}

	@SubscribeEvent
	public static void onRegisterClientCommands(final RegisterClientCommandsEvent event) {
		FpPlayerAnimCommands.register(event.getDispatcher(), event.getBuildContext());
	}
}
//?}
