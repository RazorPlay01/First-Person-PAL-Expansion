package com.github.razorplay01.fp_pal.platform.fabric.client;

//? fabric {

import com.github.razorplay01.fp_pal.firstperson.FpAnimationResources;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

/**
 * Fabric needs a reload listener with an id so it can be ordered against other mods' listeners.
 * NeoForge/Forge register {@link FpAnimationResources} directly.
 */
public class IdentifiableFpAnimationResources extends FpAnimationResources implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return FpAnimationResources.KEY;
    }
}
//?}
