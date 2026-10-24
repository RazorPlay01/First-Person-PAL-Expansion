package com.github.razorplay01.fp_pal.platform.fabric;

//? fabric {

import com.github.razorplay01.fp_pal.ModTemplate;
import com.github.razorplay01.fp_pal.command.FpPlayerAnimCommands;
import com.github.razorplay01.fp_pal.platform.fabric.client.IdentifiableFpAnimationResources;
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

@Entrypoint("client")
public class FabricClientEntrypoint implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ModTemplate.onInitializeClient();
		// The first-person assets live in this mod's own namespace (assets/modtemplate/fp_animations),
		// so the addon registers the reload listener that fills FpAnimationResources.
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableFpAnimationResources());
		ClientCommandRegistrationCallback.EVENT.register(FpPlayerAnimCommands::register);
	}

}
//?}
