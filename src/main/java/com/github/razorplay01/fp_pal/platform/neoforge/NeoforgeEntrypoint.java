package com.github.razorplay01.fp_pal.platform.neoforge;

//? neoforge {

import com.github.razorplay01.fp_pal.ModTemplate;
import net.neoforged.fml.common.Mod;

@Mod(ModTemplate.MOD_ID)
public class NeoforgeEntrypoint {

	public NeoforgeEntrypoint() {
		ModTemplate.onInitialize();
	}
}
//?}
