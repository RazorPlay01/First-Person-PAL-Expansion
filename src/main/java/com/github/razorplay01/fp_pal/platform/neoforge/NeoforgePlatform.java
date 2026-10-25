package com.github.razorplay01.fp_pal.platform.neoforge;

//? neoforge {

/*import com.github.razorplay01.fp_pal.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class NeoforgePlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public ModLoader loader() {
		return ModLoader.NEOFORGE;
	}

	//~ if >1.21.7 'FMLLoader.' -> 'FMLLoader.getCurrent().' {
	@Override
	public String mcVersion() {
		//~ if >1.21.7 'versionInfo()' -> 'getVersionInfo()'
		return FMLLoader.getCurrent().getVersionInfo().mcVersion();
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLLoader.getCurrent().isProduction();
	}
	//~}
}
*///?}
