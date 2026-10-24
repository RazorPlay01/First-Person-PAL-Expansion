package com.github.razorplay01.fp_pal.firstperson;

import com.github.razorplay01.fp_pal.mixin.CameraAccessor;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Applies the {@code fp_camera} animation bone to Minecraft's real {@link Camera}.
 * <p>
 * The camera bone is authored in first-person view space:
 * <ul>
 *     <li>X moves right/left,</li>
 *     <li>Y moves up/down,</li>
 *     <li>Z moves forward/back,</li>
 *     <li>rotation Z rolls the view.</li>
 * </ul>
 * Values are in model units (1/16 block), matching the rest of the FP rig.
 */
public final class FpCameraUtil {

    private FpCameraUtil() {
        /* Utility class */
    }

    public static void apply(Camera camera, float partialTick) {
        if (!FpRenderState.isFpActive) return;

        PlayerAnimBone transform = FpRenderState.getCameraForRender(partialTick);
        if (FpRenderState.isIdentity(transform)) return;

        // Roll around the camera's local Z axis. Camera#rotation() is public and is what the view
        // matrix uses; the cached direction vectors must be refreshed afterwards because Camera
        // does not expose a setRoll method.
        float roll = transform.getRotZ();
        if (roll != 0.0F) {
            Quaternionf rotation = camera.rotation();
            rotation.rotateZ(roll);

            //? if >=1.21.11 {
            /*// 1.21.11 exposes the direction vectors as immutable Vector3fc views, so the refreshed
            // vectors are written back through accessors onto the (mutable) camera fields.
            ((CameraAccessor) camera).playerAnimLib$getForwardVector().set(new Vector3f(0.0F, 0.0F, -1.0F).rotate(rotation));
            ((CameraAccessor) camera).playerAnimLib$getUpVector().set(new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation));
            ((CameraAccessor) camera).playerAnimLib$getLeftVector().set(new Vector3f(-1.0F, 0.0F, 0.0F).rotate(rotation));
            *///?} else {
            camera.getLookVector().set(new Vector3f(0.0F, 0.0F, -1.0F).rotate(rotation));
            camera.getUpVector().set(new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation));
            camera.getLeftVector().set(new Vector3f(-1.0F, 0.0F, 0.0F).rotate(rotation));
            //?}
        }

        float x = transform.getPosX() / 16.0F;
        float y = transform.getPosY() / 16.0F;
        float z = transform.getPosZ() / 16.0F;
        if (x == 0.0F && y == 0.0F && z == 0.0F) return;

        //? if >=1.21.11 {
        /*Vector3f leftVector = ((CameraAccessor) camera).playerAnimLib$getLeftVector();
        Vector3f upVector = ((CameraAccessor) camera).playerAnimLib$getUpVector();
        Vector3f forwardVector = ((CameraAccessor) camera).playerAnimLib$getForwardVector();
        Vec3 cameraPos = camera.position();
        *///?} else {
        Vector3f leftVector = camera.getLeftVector();
        Vector3f upVector = camera.getUpVector();
        Vector3f forwardVector = camera.getLookVector();
        Vec3 cameraPos = camera.getPosition();
        //?}

        // Camera#getLeftVector is the camera-local negative X axis, so negate it to move a positive
        // animation X to the right. Positive animation Z moves the camera forward.
        Vector3f right = new Vector3f(leftVector).negate();
        Vector3f up = new Vector3f(upVector);
        Vector3f forward = new Vector3f(forwardVector);

        ((CameraAccessor) camera).playerAnimLib$setPosition(
                cameraPos.x + right.x * x + up.x * y + forward.x * z,
                cameraPos.y + right.y * x + up.y * y + forward.y * z,
                cameraPos.z + right.z * x + up.z * y + forward.z * z
        );
    }
}
