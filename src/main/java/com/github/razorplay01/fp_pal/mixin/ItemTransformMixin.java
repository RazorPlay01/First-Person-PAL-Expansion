package com.github.razorplay01.fp_pal.mixin;

import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
//? if >=26.1.2 {
//import net.minecraft.client.resources.model.cuboid.ItemTransform;
//?} else {
import net.minecraft.client.renderer.block.model.ItemTransform;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips vanilla's item display transform while the first-person arm rig is drawing the held item.
 * <p>
 * A held item is always drawn with the transform of its {@code ItemDisplayContext}
 * ({@code firstperson_righthand}: {@code rotationXYZ(0, -90, 25)}, {@code scale(0.68)},
 * {@code translate(i * 1.13, 3.2, 1.13)}). That transform belongs to the item model, not to the
 * animation, so it rotates, scales and moves the item away from the pose the {@code fp_r_item} /
 * {@code fp_l_item} bone describes, and a rig previewed in Blockbench never lines up with the game.
 * With it skipped the item lands exactly where the animation puts it.
 * <p>
 * It is only skipped for the frames {@code FpArmRendererMixin} actually pushes an item bone pose for
 * ({@link FpRenderState#isFpItemPass}), that is, the local player's first-person item pass while the
 * player-arm mode is active. Everything else - the inventory, dropped items, other players, and the
 * hand of a clip that does not animate that item - keeps vanilla's transform.
 */
@Mixin(ItemTransform.class)
public class ItemTransformMixin {

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void playerAnimLib$skipItemDisplayTransform(boolean leftHand,
                                                       //? if >=1.21.11 {
                                                       //PoseStack.Pose pose,
                                                       //?} else {
                                                       PoseStack poseStack,
                                                       //?}
                                                       CallbackInfo ci) {
        if (FpRenderState.isFpItemPass) {
            ci.cancel();
        }
    }
}
