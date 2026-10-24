package com.github.razorplay01.fp_pal.mixin;

import com.github.razorplay01.fp_pal.firstperson.FpCameraUtil;
import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
//? if >=1.21.11 {
//import net.minecraft.world.level.Level;
//?} else {
import net.minecraft.world.level.BlockGetter;
//?}
//? if >=26.1.2 {
//import net.minecraft.client.renderer.state.level.CameraRenderState;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the animated {@code fp_camera} bone to the real first-person camera.
 * <p>
 * Up to 1.21.11 this runs at the end of {@code Camera#setup}, after vanilla has computed the player's
 * eye position and look rotation. In 26.x the camera no longer feeds the renderer directly: the frame
 * builds a {@code CameraRenderState} from it, so the animation is applied at the head of that
 * extraction and the render state is built from the animated values.
 * <p>
 * It is deliberately limited to the local player in first person and never runs for the
 * detached/third-person camera.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    //? if >=26.1.2 {
    /*@Inject(method = "extractRenderState", at = @At("HEAD"))
    private void playerAnimLib$applyFpCamera(CameraRenderState renderState,
                                             //? if >=26.3 {
                                             //DeltaTracker deltaTracker,
                                             //?} else {
                                             float partialTick,
                                             //?}
                                             CallbackInfo ci) {
        //? if >=26.3 {
        /^// 26.3 hands the extraction a DeltaTracker instead of the partial tick the older
        // CameraRenderState extractors took.
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        ^///?}
        this.playerAnimLib$applyFpCameraTo((Camera) (Object) this, partialTick);
    }
    *///?} else {
    // 1.21.11 changed the level parameter of Camera#setup from BlockGetter to Level. Mixin compares
    // handler parameters by exact type, so the handler has to follow the signature.
    @Inject(method = "setup", at = @At("TAIL"))
    private void playerAnimLib$applyFpCamera(
                                             //? if >=1.21.11 {
                                             //Level level,
                                             //?} else {
                                             BlockGetter level,
                                             //?}
                                             Entity entity, boolean detached,
                                             boolean thirdPersonReverse, float partialTick,
                                             CallbackInfo ci) {
        if (detached || thirdPersonReverse) return;
        this.playerAnimLib$applyFpCameraTo((Camera) (Object) this, partialTick);
    }
    //?}

    /**
     * Shared entry point: the animated camera only applies to the local player's first-person view.
     */
    @Unique
    private void playerAnimLib$applyFpCameraTo(Camera camera, float partialTick) {
        if (camera.isDetached()) return;
        //? if >=1.21.11 {
        //Entity cameraEntity = camera.entity();
        //?} else {
        Entity cameraEntity = camera.getEntity();
        //?}
        if (!(cameraEntity instanceof LocalPlayer player)) return;
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        if (!FpRenderState.isPlayerArmMode(player)) return;

        FpCameraUtil.apply(camera, partialTick);
    }
}
