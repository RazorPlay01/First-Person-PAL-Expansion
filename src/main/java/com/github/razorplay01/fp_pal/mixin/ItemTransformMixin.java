package com.github.razorplay01.fp_pal.mixin;

import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
//? if >=26.1.2 {
import net.minecraft.client.resources.model.cuboid.ItemTransform;
//?} else {
//import net.minecraft.client.renderer.block.model.ItemTransform;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips vanilla's item display transform while the first-person arm rig is drawing the held item. That
 * transform belongs to the item model, not the animation, so it moves the item away from the pose the
 * {@code fp_r_item} / {@code fp_l_item} bone describes.
 * <p>
 * It is only skipped while {@link FpRenderState#isFpItemPass} is set; everything else (inventory,
 * dropped items, other players) keeps vanilla's transform.
 */
@Mixin(ItemTransform.class)
public class ItemTransformMixin {

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void playerAnimLib$skipItemDisplayTransform(boolean leftHand,
                                                       //? if >=1.21.11 {
                                                       PoseStack.Pose pose,
                                                       //?} else {
                                                       //PoseStack poseStack,
                                                       //?}
                                                       CallbackInfo ci) {
        if (FpRenderState.isFpItemPass) {
            ci.cancel();
        }
    }
}
