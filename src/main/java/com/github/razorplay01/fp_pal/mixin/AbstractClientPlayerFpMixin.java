package com.github.razorplay01.fp_pal.mixin;

import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationController;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the addon-side first-person accessor to the client player and keeps the FP render state in sync
 * every tick. PAL's own mixin on the same class already implements {@code IAnimatedAvatar}, so this one
 * only adds {@link IFpAnimatedPlayer} and the tick hook.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerFpMixin implements IFpAnimatedPlayer {

    /**
     * Set by {@link FpAnimationSystem}'s factory while PAL builds the animation stack. Deliberately has
     * no initializer, which would overwrite a controller the factory already assigned.
     */
    @Unique
    private FpAnimationController fpPal$fpAnimationController;

    /**
     * Refresh the FP render state right after PAL ticks the animation processor, so the renderer sees
     * the bones of the current tick.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void fpPal$updateFpRenderState(CallbackInfo ci) {
        FpAnimationSystem.updateRenderState((AbstractClientPlayer) (Object) this);
    }

    @Override
    public FpAnimationController fpPal$getFpAnimationController() {
        return this.fpPal$fpAnimationController;
    }

    @Override
    public void fpPal$setFpAnimationController(FpAnimationController controller) {
        this.fpPal$fpAnimationController = controller;
    }
}
