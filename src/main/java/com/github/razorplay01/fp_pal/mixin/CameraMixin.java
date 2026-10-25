package com.github.razorplay01.fp_pal.mixin;

import com.github.razorplay01.fp_pal.firstperson.FpCameraUtil;
import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
//? if >=1.21.11 {
import net.minecraft.world.level.Level;
//?} else {
//import net.minecraft.world.level.BlockGetter;
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
 * Applies the animated {@code fp_camera} bone to the real first-person camera, at the end of
 * {@code Camera#setup} up to 1.21.11 and at the head of the render-state extraction in 26.x. Limited to
 * the local player in first person.
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
        /^// 26.3 hands the extraction a DeltaTracker instead of a partial tick.
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        ^///?}
        this.playerAnimLib$applyFpCameraTo((Camera) (Object) this, partialTick);
    }
    *///?} else {
    // 1.21.11 changed the level parameter of Camera#setup from BlockGetter to Level; Mixin compares
    // handler parameters by exact type.
    @Inject(method = "setup", at = @At("TAIL"))
    private void playerAnimLib$applyFpCamera(
                                             //? if >=1.21.11 {
                                             Level level,
                                             //?} else {
                                             //BlockGetter level,
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
        Entity cameraEntity = camera.entity();
        //?} else {
        //Entity cameraEntity = camera.getEntity();
        //?}
        if (!(cameraEntity instanceof LocalPlayer player)) return;
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        if (!FpRenderState.isPlayerArmMode(player)) return;

        FpCameraUtil.apply(camera, partialTick);
    }
}
