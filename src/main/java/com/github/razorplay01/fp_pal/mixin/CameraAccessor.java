package com.github.razorplay01.fp_pal.mixin;

import net.minecraft.client.Camera;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    //? if <1.21.11 {
    /*@Accessor
    void setDetached(boolean detached);
    *///?}

    //? if >=1.21.11 {
    /**
     * The cached direction vectors of the camera. 1.21.11 only exposes them as immutable
     * {@code Vector3fc} views, but the animated camera has to write the roll back into them (vanilla
     * refreshes them in {@code setup}, which runs before the roll is applied).
     */
    @Accessor("forwards")
    Vector3f playerAnimLib$getForwardVector();

    @Accessor("up")
    Vector3f playerAnimLib$getUpVector();

    @Accessor("left")
    Vector3f playerAnimLib$getLeftVector();
    //?}

    //? if >=26.1.2 {
    // 26.x caches the view-rotation matrix and only recomputes it when this dirty flag is set.
    // Rolling the camera via rotation() alone leaves a stale (un-rolled) matrix in the render state,
    // so the roll must mark the matrix dirty too. Bit 1 invalidates the view-rotation matrix and
    // bit 2 the view-rotation-projection matrix.
    @Accessor("matrixPropertiesDirty")
    int playerAnimLib$getMatrixPropertiesDirty();

    @Accessor("matrixPropertiesDirty")
    void playerAnimLib$setMatrixPropertiesDirty(int matrixPropertiesDirty);
    //?}

    /**
     * Applies the final world position of the animated first-person camera. Camera#setPosition is
     * protected, so this invoker is used by {@code FpCameraUtil}.
     */
    @Invoker("setPosition")
    void playerAnimLib$setPosition(double x, double y, double z);
}
