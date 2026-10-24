package com.github.razorplay01.fp_pal.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.RenderType;
//? if >=1.21.11 {
//import net.minecraft.client.renderer.RenderTypes;
//?}
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/**
 * Utility class for rendering player arms in first person with custom transforms.
 * <p>
 * Ported from AzureLib's {@code AzItemArmRenderUtil} (MIT License, Copyright (c) 2024 AzureDoom),
 * {@code mod.azure.azurelib.common.render.item}.
 * <p>
 * THIS IS A 1:1 PORT of that class, adapted only for PAL's types:
 * {@code PlayerAnimBone} instead of {@code AzBone}, and {@link FpRenderBuffer} instead of
 * {@code AzModelRenderer} (the {@code getOrRefreshBufferRenderType} calls map to
 * {@link FpRenderBuffer#drawModelPart} with the {@code entitySolid} / {@code entityTranslucent}
 * render types).
 *
 * <h2>What the AzureLib system does</h2>
 * AzureLib does not animate a separate first-person arm model. The arm bones ({@code leftArm} and
 * {@code rightArm}) are part of the animated model, exactly as you author them in Blockbench.
 * While the model is rendered, the arm bone is hidden (its children stay visible) and this class
 * draws the vanilla player arm mesh at that bone instead:
 * <ol>
 *     <li>{@code RenderUtils.prepMatrixForBone}: bone position, pivot, rotation, scale, un-pivot;</li>
 *     <li>{@code scale(0.67, 1.33, 0.67)} + {@code translate(±0.25, -0.43625, 0.1625)}, which turns
 *     the 4x12x4 vanilla arm into the 3x16x3 arm of {@code player_arms_import.bbmodel};</li>
 *     <li>the vanilla arm and sleeve are drawn on that bone with the part position set to the bone
 *     pivot, so the arm follows the Blockbench bone one to one.</li>
 * </ol>
 * The pose must already be inside the model frame. In AzureLib that frame is created by
 * {@code ItemRenderer} (hand anchor + item display transform); in PAL's first-person pass it is
 * created by {@link #applyCameraAnchor(PoseStack)} (default, so a rig authored around the model origin
 * is centred on the view axis) or {@link #applyHandTransform(PoseStack, boolean)} (the AzureLib hand
 * frame, selected with {@code fpDisplayAnchor: "hand"}), plus
 * {@link #applyArmDisplayTransform(PoseStack, float[], float)}.
 *
 * <h2>PAL conventions</h2>
 * PAL's loader stores the raw Blockbench values in {@link PlayerAnimBone}, while AzureLib's
 * {@code AzBone} stores positions with X negated and rotations with X and Y negated
 * ({@code AzBakedAnimationsAdapter} and {@code AzBuiltinBakedModelFactory}). This port applies the
 * same conversion in {@link #translateMatrixToBone(PoseStack, PlayerAnimBone)} and
 * {@link #rotateMatrixAroundBone(PoseStack, PlayerAnimBone)}, so a value authored for an AzureLib
 * arm rig produces the same result here. The rotation order is Z, Y, X, the same as AzureLib's
 * {@code RenderUtils.rotateMatrixAroundBone}.
 */
public final class FpArmRenderUtil {

    private FpArmRenderUtil() {
        /* This utility class should not be instantiated */
    }

    /**
     * Bone name used by AzureLib and by {@code player_arms_import.bbmodel}.
     */
    public static final String LEFT_ARM_BONE = "leftArm";

    /**
     * Bone name used by AzureLib and by {@code player_arms_import.bbmodel}.
     */
    public static final String RIGHT_ARM_BONE = "rightArm";

    /**
     * Rotation centre of the right arm as authored in {@code player_arms_import.bbmodel} (the
     * {@code rightArm} bone origin). It is the value {@code AzBone#getPivot*} holds after
     * {@code AzBuiltinBakedModelFactory} applies {@code -pivot.x}: the bbmodel origin is {@code x = 6}
     * and the exported geo pivot is {@code x = -6}, so the stored pivot is {@code x = 6}.
     */
    public static final float[] RIGHT_ARM_PIVOT = new float[]{6.0F, 6.0F, 6.0F};

    /**
     * Rotation centre of the left arm as authored in {@code player_arms_import.bbmodel}.
     */
    public static final float[] LEFT_ARM_PIVOT = new float[]{-6.0F, 6.0F, 6.0F};

    /**
     * Pivot of the {@code fp_r_item} bone: in {@code player_arms_import.bbmodel} the item bone sits on
     * the hand, at the same place as {@code rightArm}.
     */
    public static final float[] RIGHT_ITEM_PIVOT = new float[]{6.0F, 6.0F, 6.0F};

    /**
     * Pivot of the {@code fp_l_item} bone, mirror of {@link #RIGHT_ITEM_PIVOT}.
     */
    public static final float[] LEFT_ITEM_PIVOT = new float[]{-6.0F, 6.0F, 6.0F};

    /**
     * The rig root needs no pivot translation: its pivot is the model origin.
     */
    public static final float[] NO_PIVOT = new float[]{0.0F, 0.0F, 0.0F};

    /**
     * Checks if the given bone is an arm bone that should be rendered.
     *
     * @param bone The bone to check
     * @return true if this is a left or right arm bone
     */
    public static boolean isArmBone(PlayerAnimBone bone) {
        String name = bone.getName();
        return LEFT_ARM_BONE.equals(name) || RIGHT_ARM_BONE.equals(name);
    }

    /**
     * Renders the vanilla player arm for the given arm bone, hiding the bone itself but keeping
     * children visible. 1:1 of {@code AzItemArmRenderUtil#renderArmForBone}.
     * <p>
     * The pose stack must already be in the model frame (see
     * {@link #applyCameraAnchor(PoseStack)} or {@link #applyHandTransform(PoseStack, boolean)}, plus
     * {@link #applyArmDisplayTransform(PoseStack, float[], float)}) and already contain the parent
     * chain of the arm (for PAL's first-person rig, that is {@code fp_player_arms}).
     *
     * @param poseStack   the pose stack, already inside the model frame
     * @param bone        the arm bone ({@code leftArm} or {@code rightArm})
     * @param pivot       the bone rotation centre in model units
     *                    ({@link #LEFT_ARM_PIVOT} / {@link #RIGHT_ARM_PIVOT})
     * @param playerModel the vanilla player model that owns the arm meshes
     * @param playerSkin  the skin texture of the local player
     * @param packedLight packed light coordinates
     * @param render      the render buffer the arm is submitted to
     */
    public static void renderArmForBone(
            PoseStack poseStack,
            PlayerAnimBone bone,
            float[] pivot,
            //? if >=1.21.11 {
            //PlayerModel playerModel,
            //?} else {
            PlayerModel<?> playerModel,
            //?}
            ResourceLocation playerSkin,
            int packedLight,
            FpRenderBuffer render
    ) {
        // Only first-person arm bones are rendered by this utility.
        if (!isArmBone(bone)) {
            return;
        }

        poseStack.pushPose();

        // Same order as AzItemArmRenderUtil -> RenderUtils.prepMatrixForBone.
        applyArmTransform(poseStack, bone, pivot);

        if (LEFT_ARM_BONE.equals(bone.getName())) {
            renderLeftArm(poseStack, pivot, playerModel, playerSkin, packedLight, render);
        } else {
            renderRightArm(poseStack, pivot, playerModel, playerSkin, packedLight, render);
        }

        poseStack.popPose();
    }

    /**
     * Renders the left arm and sleeve. 1:1 of {@code AzItemArmRenderUtil#renderLeftArm}.
     */
    private static void renderLeftArm(
            PoseStack poseStack,
            float[] pivot,
            //? if >=1.21.11 {
            //PlayerModel playerModel,
            //?} else {
            PlayerModel<?> playerModel,
            //?}
            ResourceLocation playerSkin,
            int packedLight,
            FpRenderBuffer render
    ) {
        poseStack.scale(0.67F, 1.33F, 0.67F);
        poseStack.translate(-0.25, -0.43625, 0.1625);

        // The player model is shared with the third-person renderer, which PAL also animates, so
        // reset the part scale before drawing the arm. Rotation and position are set below.
        playerModel.leftArm.xScale = 1.0F;
        playerModel.leftArm.yScale = 1.0F;
        playerModel.leftArm.zScale = 1.0F;
        playerModel.leftSleeve.xScale = 1.0F;
        playerModel.leftSleeve.yScale = 1.0F;
        playerModel.leftSleeve.zScale = 1.0F;

        // Set up and render the left arm
        playerModel.leftArm.setPos(pivot[0], pivot[1], pivot[2]);
        playerModel.leftArm.setRotation(0, 0, 0);
        render.drawModelPart(
                playerModel.leftArm,
                poseStack,
                RenderType.entitySolid(playerSkin),
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        // Set up and render a left sleeve
        playerModel.leftSleeve.setPos(pivot[0], pivot[1], pivot[2]);
        playerModel.leftSleeve.setRotation(0, 0, 0);
        render.drawModelPart(
                playerModel.leftSleeve,
                poseStack,
                RenderType.entityTranslucent(playerSkin),
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        // The player model is shared with the third-person renderer, so leave it exactly as it was
        // before this first-person pass. Without this the third-person arms stay on the FP rig pivot.
        playerModel.leftArm.resetPose();
        playerModel.leftSleeve.resetPose();
    }

    /**
     * Renders the right arm and sleeve. 1:1 of {@code AzItemArmRenderUtil#renderRightArm}.
     */
    private static void renderRightArm(
            PoseStack poseStack,
            float[] pivot,
            //? if >=1.21.11 {
            //PlayerModel playerModel,
            //?} else {
            PlayerModel<?> playerModel,
            //?}
            ResourceLocation playerSkin,
            int packedLight,
            FpRenderBuffer render
    ) {
        poseStack.scale(0.67F, 1.33F, 0.67F);
        poseStack.translate(0.25, -0.43625, 0.1625);

        // The player model is shared with the third-person renderer, which PAL also animates, so
        // reset the part scale before drawing the arm. Rotation and position are set below.
        playerModel.rightArm.xScale = 1.0F;
        playerModel.rightArm.yScale = 1.0F;
        playerModel.rightArm.zScale = 1.0F;
        playerModel.rightSleeve.xScale = 1.0F;
        playerModel.rightSleeve.yScale = 1.0F;
        playerModel.rightSleeve.zScale = 1.0F;

        // Set up and render the right arm
        playerModel.rightArm.setPos(pivot[0], pivot[1], pivot[2]);
        playerModel.rightArm.setRotation(0, 0, 0);
        render.drawModelPart(
                playerModel.rightArm,
                poseStack,
                RenderType.entitySolid(playerSkin),
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        // Set up and render a right sleeve
        playerModel.rightSleeve.setPos(pivot[0], pivot[1], pivot[2]);
        playerModel.rightSleeve.setRotation(0, 0, 0);
        render.drawModelPart(
                playerModel.rightSleeve,
                poseStack,
                RenderType.entityTranslucent(playerSkin),
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        // The player model is shared with the third-person renderer, so leave it exactly as it was
        // before this first-person pass. Without this the third-person arms stay on the FP rig pivot.
        playerModel.rightArm.resetPose();
        playerModel.rightSleeve.resetPose();
    }

    /**
     * Applies the vanilla first-person item hand anchor,
     * {@code ItemInHandRenderer#applyItemArmTransform} without equip progress. This is the origin
     * AzureLib's item model lives in before the item display transform, and therefore the frame
     * {@code leftArm} / {@code rightArm} are authored against in an AzureLib gun rig.
     *
     * @param isLeftHand true for the left-hand anchor ({@code -0.56} on X)
     */
    public static void applyHandTransform(PoseStack poseStack, boolean isLeftHand) {
        float side = isLeftHand ? -1.0F : 1.0F;
        poseStack.translate(side * 0.56F, -0.52F, -0.72F);
    }

    /**
     * Applies the camera anchor: same distance and height as the vanilla hand anchor, but on the view
     * axis ({@code x = 0}). A rig authored around the model origin (the {@code player_arms_import.bbmodel}
     * convention, arms at {@code x = ±6}) then renders centred on screen like it does in Blockbench,
     * instead of being pushed to the right hand.
     */
    public static void applyCameraAnchor(PoseStack poseStack) {
        poseStack.translate(0.0F, -0.52F, -0.72F);
    }

    /**
     * Applies the first-person display transform of the item model that owns the arm bones: the same
     * translation (in 1/16 block) and uniform scale a {@code firstperson_righthand} /
     * {@code firstperson_lefthand} display entry applies before the model is drawn.
     * <p>
     * AzureLib gets this from the item's model JSON, so the Blockbench preview and the game agree.
     * PAL's first-person pass has no item model, so the values are configuration
     * ({@link FpRenderState#ARM_DISPLAY_TRANSLATION} and {@link FpRenderState#ARM_DISPLAY_SCALE}).
     * Identity leaves the rig exactly at the hand anchor.
     */
    public static void applyArmDisplayTransform(PoseStack poseStack, float[] translation, float scale) {
        poseStack.translate(translation[0] / 16.0F, translation[1] / 16.0F, translation[2] / 16.0F);
        poseStack.scale(scale, scale, scale);
    }

    /**
     * Applies the {@code fp_player_arms} root transform, which moves, rotates and scales the whole
     * rig (both arms and both held items) around the model origin.
     */
    public static void applyRootTransform(PoseStack poseStack, PlayerAnimBone wholeRig) {
        applyRootTransform(poseStack, wholeRig, NO_PIVOT);
    }

    /**
     * Applies the animated model root around its own pivot, like AzureLib's {@code prepMatrixForBone}.
     *
     * @param pivot root pivot in model units ({@link FpRenderState#fpRootPivot})
     */
    public static void applyRootTransform(PoseStack poseStack, PlayerAnimBone wholeRig, float[] pivot) {
        applyBoneTransform(poseStack, wholeRig, pivot);
    }

    /**
     * Applies the {@code fp_camera} transform, which moves, rotates and scales the first-person pass
     * around the view origin. Unlike the arm chain, the camera is a PAL-only bone authored directly in
     * view space, so it keeps PAL's {@code (+X, -Y, +Z)} and non-negated rotation convention.
     */
    public static void applyCameraTransform(PoseStack poseStack, PlayerAnimBone camera) {
        poseStack.translate(camera.getPosX() / 16.0F, -camera.getPosY() / 16.0F, camera.getPosZ() / 16.0F);

        float rotX = camera.getRotX();
        float rotY = camera.getRotY();
        float rotZ = camera.getRotZ();

        if (rotZ != 0)
            FpRenderState.rotatePose(poseStack, Z_QUATERNION_CACHE.rotationXYZ(0f, 0f, rotZ));

        if (rotY != 0)
            FpRenderState.rotatePose(poseStack, Y_QUATERNION_CACHE.rotationXYZ(0f, rotY, 0f));

        if (rotX != 0)
            FpRenderState.rotatePose(poseStack, X_QUATERNION_CACHE.rotationXYZ(rotX, 0f, 0f));

        poseStack.scale(camera.getScaleX(), camera.getScaleY(), camera.getScaleZ());
    }

    /**
     * Applies an arm bone's full transform in the model frame. This is the same transform used by
     * {@link #renderArmForBone(PoseStack, PlayerAnimBone, float[], PlayerModel, ResourceLocation, int, FpRenderBuffer)},
     * exposed so a held item can be rendered in exactly the same arm frame.
     */
    public static void applyArmTransform(PoseStack poseStack, PlayerAnimBone arm, float[] pivot) {
        applyBoneTransform(poseStack, arm, pivot);
    }

    /**
     * Applies the item bone offset ({@code fp_right_item} / {@code fp_left_item}) in the model frame.
     * In AzureLib the item is part of the same model and is a sibling of the arm bones, so only the
     * rig root and the item bone affect it; the arms do not drag the item.
     */
    public static void applyItemTransform(PoseStack poseStack, PlayerAnimBone item) {
        applyBoneTransform(poseStack, item, NO_PIVOT);
    }

    /**
     * Applies the item bone offset around a pivot in model units ({@link #RIGHT_ITEM_PIVOT} /
     * {@link #LEFT_ITEM_PIVOT}), which is how the item bone is authored in Blockbench.
     */
    public static void applyItemTransform(PoseStack poseStack, PlayerAnimBone item, float[] pivot) {
        applyBoneTransform(poseStack, item, pivot);
    }

    /**
     * Applies the offset, rotation and scale of a bone around the pose origin.
     */
    public static void applyBoneTransform(PoseStack poseStack, PlayerAnimBone bone) {
        applyBoneTransform(poseStack, bone, NO_PIVOT);
    }

    /**
     * Applies the offset, pivot, rotation and scale of a bone in one go (same order as
     * {@code RenderUtils.prepMatrixForBone}).
     *
     * @param pivot rotation centre in model units (raw model space, Y keeps its sign here)
     */
    public static void applyBoneTransform(PoseStack poseStack, PlayerAnimBone bone, float[] pivot) {
        translateMatrixToBone(poseStack, bone);
        translateToPivotPoint(poseStack, pivot);
        rotateMatrixAroundBone(poseStack, bone);
        scaleMatrixForBone(poseStack, bone);
        translateAwayFromPivotPoint(poseStack, pivot);
    }

    /**
     * Same convention as AzureLib's {@code RenderUtils.translateMatrixToBone}: X is negated and Y is
     * not. PAL's loader keeps the raw Blockbench value, so the X negation is applied here instead of
     * in the loader.
     */
    public static void translateMatrixToBone(PoseStack poseStack, PlayerAnimBone bone) {
        poseStack.translate(-bone.getPosX() / 16.0F, bone.getPosY() / 16.0F, bone.getPosZ() / 16.0F);
    }

    public static void translateToPivotPoint(PoseStack poseStack, float[] pivot) {
        poseStack.translate(pivot[0] / 16.0F, pivot[1] / 16.0F, pivot[2] / 16.0F);
    }

    private static final Quaternionf X_QUATERNION_CACHE = new Quaternionf();
    private static final Quaternionf Y_QUATERNION_CACHE = new Quaternionf();
    private static final Quaternionf Z_QUATERNION_CACHE = new Quaternionf();

    public static void rotateMatrixAroundBone(PoseStack poseStack, PlayerAnimBone bone) {
        // AzureLib's AzBone stores rotations with X and Y negated (AzBakedAnimationsAdapter), while
        // PAL's PlayerAnimBone keeps the raw value, so the same negation is applied here.
        float rotX = -bone.getRotX();
        float rotY = -bone.getRotY();
        float rotZ = bone.getRotZ();

        if (rotZ != 0)
            FpRenderState.rotatePose(poseStack, Z_QUATERNION_CACHE.rotationXYZ(0f, 0f, rotZ));

        if (rotY != 0)
            FpRenderState.rotatePose(poseStack, Y_QUATERNION_CACHE.rotationXYZ(0f, rotY, 0f));

        if (rotX != 0)
            FpRenderState.rotatePose(poseStack, X_QUATERNION_CACHE.rotationXYZ(rotX, 0f, 0f));
    }

    public static void scaleMatrixForBone(PoseStack poseStack, PlayerAnimBone bone) {
        poseStack.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
    }

    public static void translateAwayFromPivotPoint(PoseStack poseStack, float[] pivot) {
        poseStack.translate(-pivot[0] / 16.0F, -pivot[1] / 16.0F, -pivot[2] / 16.0F);
    }
}
