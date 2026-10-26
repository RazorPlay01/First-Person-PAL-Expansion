package com.github.razorplay01.fp_pal.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?}
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

/**
 * Utility class for rendering player arms in first person with custom transforms.
 * <p>
 * 1:1 port of AzureLib's {@code AzItemArmRenderUtil} (MIT License, Copyright (c) 2024 AzureDoom),
 * adapted only for PAL's types ({@code PlayerAnimBone} instead of {@code AzBone},
 * {@link FpRenderBuffer} instead of {@code AzModelRenderer}).
 * <p>
 * AzureLib does not animate a separate first-person arm model: the {@code leftArm} / {@code rightArm}
 * bones are part of the animated model, and this class draws the vanilla arm mesh at that bone (the
 * bone itself stays hidden). The pose must already be in the model frame, created by
 * {@link #applyCameraAnchor(PoseStack)} or {@link #applyHandTransform(PoseStack, boolean)} plus
 * {@link #applyArmDisplayTransform(PoseStack, float[], float)}.
 * <p>
 * PAL's loader keeps the raw Blockbench values, so the X/Y negation AzureLib applies when baking is
 * done here in {@link #translateMatrixToBone(PoseStack, PlayerAnimBone)} and
 * {@link #rotateMatrixAroundBone(PoseStack, PlayerAnimBone)} (rotation order Z, Y, X).
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
     * Rotation centre of the right arm in model units, as stored after AzureLib's X mirroring.
     */
    public static final float[] RIGHT_ARM_PIVOT = new float[]{6.0F, 6.0F, 6.0F};

    /**
     * Rotation centre of the left arm as authored in {@code player_arms_import.bbmodel}.
     */
    public static final float[] LEFT_ARM_PIVOT = new float[]{-6.0F, 6.0F, 6.0F};

    /**
     * Pivot of the {@code fp_r_item} bone, which sits on the hand like {@code rightArm}.
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
     * @return true if this is a left or right arm bone
     */
    public static boolean isArmBone(PlayerAnimBone bone) {
        String name = bone.getName();
        return LEFT_ARM_BONE.equals(name) || RIGHT_ARM_BONE.equals(name);
    }

    /**
     * Renders the vanilla player arm for the given arm bone, hiding the bone itself but keeping
     * children visible. The pose stack must already be in the model frame and contain the parent chain
     * of the arm (for PAL's first-person rig, {@code fp_player_arms}).
     */
    public static void renderArmForBone(
            PoseStack poseStack,
            PlayerAnimBone bone,
            float[] pivot,
            //? if >=1.21.11 {
            PlayerModel playerModel,
            //?} else {
            //PlayerModel<?> playerModel,
            //?}
            Identifier playerSkin,
            int packedLight,
            FpRenderBuffer render
    ) {
        if (!isArmBone(bone)) {
            return;
        }

        poseStack.pushPose();

        applyArmTransform(poseStack, bone, pivot);

        if (LEFT_ARM_BONE.equals(bone.getName())) {
            renderArm(poseStack, playerModel.leftArm, playerModel.leftSleeve, pivot, false, playerSkin, packedLight, render);
        } else {
            renderArm(poseStack, playerModel.rightArm, playerModel.rightSleeve, pivot, true, playerSkin, packedLight, render);
        }

        poseStack.popPose();
    }

    // entityCutout instead of vanilla's entityTranslucent: the deferred collector path does not honour
    // entityTranslucent, so the sleeve's transparent pixels would render as black. entityCutout discards
    // them with an alpha test and also works for the opaque arm.
    private static RenderType sleeveRenderType(Identifier playerSkin) {
        return RenderTypes.entityCutout(playerSkin);
    }

    /**
     * Renders a vanilla first-person arm with its sleeve.
     * <p>
     * Since 1.21.9 the sleeve is a child of the arm, so a single {@code entityCutout} pass draws both:
     * the alpha test keeps the opaque arm and discards the sleeve's transparent pixels (which would
     * render black with {@code entitySolid} or {@code entityTranslucent} on the deferred pipeline).
     */
    private static void renderArm(
            PoseStack poseStack,
            ModelPart arm,
            ModelPart sleeve,
            float[] pivot,
            boolean rightHand,
            Identifier playerSkin,
            int packedLight,
            FpRenderBuffer render
    ) {
        poseStack.scale(0.67F, 1.33F, 0.67F);
        poseStack.translate(rightHand ? 0.25F : -0.25F, -0.43625F, 0.1625F);

        arm.resetPose();
        sleeve.resetPose();
        arm.xScale = 1.0F;
        arm.yScale = 1.0F;
        arm.zScale = 1.0F;
        sleeve.xScale = 1.0F;
        sleeve.yScale = 1.0F;
        sleeve.zScale = 1.0F;

        arm.setPos(pivot[0], pivot[1], pivot[2]);
        arm.setRotation(0, 0, 0);

        //? if >=1.21.11 {
        // The sleeve is a child of the arm, so a single entityCutout pass draws both correctly.
        render.drawGeometry(poseStack, RenderTypes.entityCutout(playerSkin), (pose, consumer) -> {
            arm.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        });
        //?} else {
        /*sleeve.setPos(pivot[0], pivot[1], pivot[2]);
        sleeve.setRotation(0, 0, 0);
        render.drawModelPart(arm, poseStack, RenderTypes.entitySolid(playerSkin), packedLight, OverlayTexture.NO_OVERLAY);
        render.drawModelPart(sleeve, poseStack, sleeveRenderType(playerSkin), packedLight, OverlayTexture.NO_OVERLAY);
        *///?}
    }

    /**
     * Restores the vanilla arm and sleeve parts of the local player's shared model, so the
     * third-person renderer never inherits the first-person rig pose.
     */
    public static void resetPlayerModelArms() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) {
            return;
        }

        // Only the local player's model is written to by the first-person pass.
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(localPlayer);
        if (!(renderer instanceof AvatarRenderer playerRenderer)) {
            return;
        }

        //? if >=1.21.11 {
        PlayerModel playerModel = (PlayerModel) playerRenderer.getModel();
        //?} else {
        //PlayerModel<?> playerModel = playerRenderer.getModel();
        //?}

        playerModel.leftArm.resetPose();
        playerModel.rightArm.resetPose();
        playerModel.leftSleeve.resetPose();
        playerModel.rightSleeve.resetPose();
    }

    /**
     * Applies the vanilla first-person hand anchor (without equip progress), the frame AzureLib gun
     * rigs are authored against.
     */
    public static void applyHandTransform(PoseStack poseStack, boolean isLeftHand) {
        float side = isLeftHand ? -1.0F : 1.0F;
        poseStack.translate(side * 0.56F, -0.52F, -0.72F);
    }

    /**
     * Applies the camera anchor: same distance and height as the vanilla hand anchor, but on the view
     * axis, so a rig authored around the model origin renders centred like in Blockbench.
     */
    public static void applyCameraAnchor(PoseStack poseStack) {
        poseStack.translate(0.0F, -0.52F, -0.72F);
    }

    /**
     * Applies the display transform of the item model that owns the arm bones: the translation (in
     * 1/16 block) and uniform scale of a {@code firstperson_*} entry.
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
     * around the view origin. The camera keeps PAL's {@code (+X, -Y, +Z)} convention.
     */
    public static void applyCameraTransform(PoseStack poseStack, PlayerAnimBone camera) {
        //? if >=26.1.2 {
        poseStack.translate(camera.position.x / 16.0F, -camera.position.y / 16.0F, camera.position.z / 16.0F);
        //?} else {
        //poseStack.translate(camera.position.x / 16.0F, -camera.position.y / 16.0F, camera.position.z / 16.0F);
        //?}

        //? if >=26.1.2 {
        float rotX = camera.rotation.x;
        float rotY = camera.rotation.y;
        float rotZ = camera.rotation.z;
        //?} else {
        /*float rotX = camera.rotation.x;
        float rotY = camera.rotation.y;
        float rotZ = camera.rotation.z;
        *///?}

        if (rotZ != 0)
            FpRenderState.rotatePose(poseStack, Z_QUATERNION_CACHE.rotationXYZ(0f, 0f, rotZ));

        if (rotY != 0)
            FpRenderState.rotatePose(poseStack, Y_QUATERNION_CACHE.rotationXYZ(0f, rotY, 0f));

        if (rotX != 0)
            FpRenderState.rotatePose(poseStack, X_QUATERNION_CACHE.rotationXYZ(rotX, 0f, 0f));

        //? if >=26.1.2 {
        poseStack.scale(camera.scale.x, camera.scale.y, camera.scale.z);
        //?} else {
        //poseStack.scale(camera.scale.x, camera.scale.y, camera.scale.z);
        //?}
    }

    /**
     * Applies an arm bone's full transform in the model frame. This is the same transform used by
     * {@link #renderArmForBone(PoseStack, PlayerAnimBone, float[], PlayerModel, Identifier, int, FpRenderBuffer)},
     * exposed so a held item can be rendered in exactly the same arm frame.
     */
    public static void applyArmTransform(PoseStack poseStack, PlayerAnimBone arm, float[] pivot) {
        applyBoneTransform(poseStack, arm, pivot);
    }

    /**
     * Applies the item bone offset in the model frame, keeping the item on the model origin (the
     * {@code fpDisplayAnchor: "hand"} convention). A rig authored around the model origin uses
     * {@link #applyItemTransform(PoseStack, PlayerAnimBone, float[])} instead.
     */
    public static void applyItemTransform(PoseStack poseStack, PlayerAnimBone item) {
        applyItemTransform(poseStack, item, NO_PIVOT);
    }

    /**
     * Applies the item bone transform so the item model ends up on the bone itself. Vanilla draws the
     * item's {@code 1x1x1} box around the origin of the frame it is handed, so the box has to be moved
     * to the bone origin before the bone rotation is applied; rotating it about the pivot without that
     * translation would offset it by {@code -R * pivot}.
     *
     * @param pivot the bone origin in model units, or {@link #NO_PIVOT} for a rig whose item sits on
     *              the model origin
     */
    public static void applyItemTransform(PoseStack poseStack, PlayerAnimBone item, float[] pivot) {
        // The pivot is applied before the rotation on purpose: translating back afterwards would offset
        // the item by -R * pivot, because the closing translate is rotated by the bone too.
        translateToPivotPoint(poseStack, pivot);
        translateMatrixToBone(poseStack, item);
        rotateMatrixAroundBone(poseStack, item);
        scaleMatrixForBone(poseStack, item);
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
     * Same convention as AzureLib's {@code translateMatrixToBone}: X is negated and Y is not, because
     * PAL's loader keeps the raw Blockbench value.
     */
    public static void translateMatrixToBone(PoseStack poseStack, PlayerAnimBone bone) {
        //? if >=26.1.2 {
        poseStack.translate(-bone.position.x / 16.0F, bone.position.y / 16.0F, bone.position.z / 16.0F);
        //?} else {
        //poseStack.translate(-bone.position.x / 16.0F, bone.position.y / 16.0F, bone.position.z / 16.0F);
        //?}
    }

    public static void translateToPivotPoint(PoseStack poseStack, float[] pivot) {
        poseStack.translate(pivot[0] / 16.0F, pivot[1] / 16.0F, pivot[2] / 16.0F);
    }

    private static final Quaternionf X_QUATERNION_CACHE = new Quaternionf();
    private static final Quaternionf Y_QUATERNION_CACHE = new Quaternionf();
    private static final Quaternionf Z_QUATERNION_CACHE = new Quaternionf();

    public static void rotateMatrixAroundBone(PoseStack poseStack, PlayerAnimBone bone) {
        // AzureLib negates X and Y when baking, PAL keeps the raw value, so the same negation is applied here.
        //? if >=26.1.2 {
        float rotX = -bone.rotation.x;
        float rotY = -bone.rotation.y;
        float rotZ = bone.rotation.z;
        //?} else {
        /*float rotX = -bone.rotation.x;
        float rotY = -bone.rotation.y;
        float rotZ = bone.rotation.z;
        *///?}

        if (rotZ != 0)
            FpRenderState.rotatePose(poseStack, Z_QUATERNION_CACHE.rotationXYZ(0f, 0f, rotZ));

        if (rotY != 0)
            FpRenderState.rotatePose(poseStack, Y_QUATERNION_CACHE.rotationXYZ(0f, rotY, 0f));

        if (rotX != 0)
            FpRenderState.rotatePose(poseStack, X_QUATERNION_CACHE.rotationXYZ(rotX, 0f, 0f));
    }

    public static void scaleMatrixForBone(PoseStack poseStack, PlayerAnimBone bone) {
        //? if >=26.1.2 {
        poseStack.scale(bone.scale.x, bone.scale.y, bone.scale.z);
        //?} else {
        //poseStack.scale(bone.scale.x, bone.scale.y, bone.scale.z);
        //?}
    }

    public static void translateAwayFromPivotPoint(PoseStack poseStack, float[] pivot) {
        poseStack.translate(-pivot[0] / 16.0F, -pivot[1] / 16.0F, -pivot[2] / 16.0F);
    }
}
