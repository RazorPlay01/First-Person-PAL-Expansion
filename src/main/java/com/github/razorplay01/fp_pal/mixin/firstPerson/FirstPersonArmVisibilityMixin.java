package com.github.razorplay01.fp_pal.mixin.firstPerson;

import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides vanilla's first-person arm mesh while a first-person animation is playing.
 */
//? if >=1.21.11 {
@Mixin(AvatarRenderer.class)
//?} else {
//@Mixin(AvatarRenderer.class)
//?}
public class FirstPersonArmVisibilityMixin {

    //? if >=1.21.11 {
    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void playerAnimLib$hideVanillaRightHand(PoseStack poseStack, SubmitNodeCollector collector,
                                                    int light, Identifier skin, boolean sleeveVisible,
                                                    CallbackInfo ci) {
        if (FpRenderState.isPlayerArmMode(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void playerAnimLib$hideVanillaLeftHand(PoseStack poseStack, SubmitNodeCollector collector,
                                                   int light, Identifier skin, boolean sleeveVisible,
                                                   CallbackInfo ci) {
        if (FpRenderState.isPlayerArmMode(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
    //?}
}
