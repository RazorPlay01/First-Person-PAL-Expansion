package com.github.razorplay01.fp_pal.firstperson;

import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the current first-person render state.
 * Updated every tick and read during rendering.
 */
public class FpRenderState {
    private FpRenderState() {
        /* This utility class should not be instantiated */
    }

    /**
     * Whether FP rendering is active (a FP animation is currently playing).
     */
    public static boolean isFpActive = false;

    /**
     * Whether the local player's held item is currently drawn with the {@code fp_r_item} /
     * {@code fp_l_item} bone pose applied. While set, {@code ItemTransformMixin} skips vanilla's item
     * display transform so the item lands where the animation puts it.
     */
    public static boolean isFpItemPass = false;

    /**
     * Snapshot of the pose the vanilla first-person item pass starts from
     * (see {@link #captureItemFrame(PoseStack)}).
     */
    private static final Matrix4f ITEM_FRAME_POSE = new Matrix4f();

    /**
     * Normal matrix that belongs to {@link #ITEM_FRAME_POSE}.
     */
    private static final Matrix3f ITEM_FRAME_NORMAL = new Matrix3f();

    /**
     * Whether {@link #ITEM_FRAME_POSE} holds a frame of the current one.
     */
    private static boolean itemFrameCaptured = false;

    /**
     * Remembers the frame the vanilla first-person item pass starts from, before vanilla adds the
     * hand sway and the per-hand item transforms. {@code FpArmRendererMixin} rewinds to it before
     * applying the animated bone poses.
     */
    public static void captureItemFrame(PoseStack poseStack) {
        PoseStack.Pose pose = poseStack.last();
        ITEM_FRAME_POSE.set(pose.pose());
        ITEM_FRAME_NORMAL.set(pose.normal());
        itemFrameCaptured = true;
    }

    /**
     * Rewinds the top of the pose stack to the frame captured by {@link #captureItemFrame(PoseStack)},
     * dropping every transform vanilla applied since then. Does nothing before the first capture.
     */
    public static void restoreItemFrame(PoseStack poseStack) {
        if (!itemFrameCaptured) return;
        PoseStack.Pose pose = poseStack.last();
        pose.pose().set(ITEM_FRAME_POSE);
        pose.normal().set(ITEM_FRAME_NORMAL);
    }

    /**
     * @return true if the given entity is playing a first-person animation, which is what makes the
     * render mixins replace the vanilla arm.
     */
    public static boolean isPlayerArmMode(@Nullable Entity entity) {
        if (entity instanceof IFpAnimatedPlayer fpPlayer) {
            FpAnimationController controller = fpPlayer.fpPal$getFpAnimationController();
            return controller != null && controller.isPlayerArmMode();
        }
        return false;
    }

    /**
     * @return the {@link FirstPersonConfiguration} that decides what the first-person pass draws.
     * It comes from this addon's controller rather than PAL's manager, because this controller reports
     * {@link FirstPersonMode#NONE} and PAL would otherwise return the wrong defaults.
     */
    public static FirstPersonConfiguration firstPersonConfiguration(@Nullable Entity entity) {
        if (entity instanceof IFpAnimatedPlayer fpPlayer) {
            FpAnimationController controller = fpPlayer.fpPal$getFpAnimationController();
            if (controller != null) return controller.getFirstPersonConfiguration();
        }
        return FpAnimationController.DEFAULT_FP_CONFIGURATION;
    }

    /**
     * Rotates the pose by a quaternion, hiding the 26.3 {@code mulPose} -> {@code rotate} rename.
     */
    public static void rotatePose(PoseStack poseStack, Quaternionf rotation) {
        //? if >=26.3 {
        poseStack.rotate(rotation);
        //?} else {
        //poseStack.mulPose(rotation);
        //?}
    }

    /**
     * Reads a bone transform out of PAL's animation stack, keeping the call sites identical across the
     * PAL 1.2 signature change.
     */
    public static PlayerAnimBone get3DTransform(AvatarAnimManager manager, String boneName) {
        PlayerAnimBone bone = new PlayerAnimBone(boneName);
        //? if >=26.1.2 {
        manager.get3DTransform(bone);
        //?} else {
        //bone = manager.get3DTransform(bone);
        //?}
        return bone;
    }

    /**
     * The FP root bone ({@code fp_player_arms}), which moves and rotates the whole rig
     * (arms, held items and the camera).
     */
    public static final PlayerAnimBone fpPlayerArms = new PlayerAnimBone("fp_player_arms");

    /**
     * Rotation centre of the animation's model root in model units. {@link FpAnimationSystem} sets it
     * when resolving the root bone; most rigs use the origin.
     */
    public static float[] fpRootPivot = FpArmRenderUtil.NO_PIVOT;

    /**
     * The FP right arm bone transform, named like AzureLib's {@code rightArm}.
     */
    public static final PlayerAnimBone fpRightArm = new PlayerAnimBone(FpArmRenderUtil.RIGHT_ARM_BONE);

    /**
     * The FP left arm bone transform, named like AzureLib's {@code leftArm}.
     */
    public static final PlayerAnimBone fpLeftArm = new PlayerAnimBone(FpArmRenderUtil.LEFT_ARM_BONE);

    /**
     * The FP right item bone transform (for item offset in right hand).
     */
    public static final PlayerAnimBone fpRightItem = new PlayerAnimBone("fp_right_item");

    /**
     * The FP left item bone transform (for item offset in left hand).
     */
    public static final PlayerAnimBone fpLeftItem = new PlayerAnimBone("fp_left_item");

    /**
     * The FP camera transform.
     */
    public static final PlayerAnimBone fpCamera = new PlayerAnimBone("fp_camera");

    /**
     * Previous tick's camera transform, used to interpolate the camera between ticks (the camera runs
     * at frame rate, the animation state at tick rate).
     */
    private static final PlayerAnimBone fpCameraPrevious = new PlayerAnimBone("fp_camera");

    /**
     * Scratch bone returned by {@link #getCameraForRender(float)}, reused to avoid allocating one per
     * frame.
     */
    private static final PlayerAnimBone fpCameraRender = new PlayerAnimBone("fp_camera");

    /**
     * Whether the current animation actually contains an item bone. A zero transform is still a valid
     * animated bone, so presence cannot be inferred from {@link #isIdentity(PlayerAnimBone)}.
     */
    private static boolean rightItemBoneActive = false;
    private static boolean leftItemBoneActive = false;

    /**
     * Whether the current animation contains the arm bone for a hand, checked against the animation's
     * own bone list so a clip that only animates one hand can hide the other.
     */
    private static boolean rightArmBoneActive = false;
    private static boolean leftArmBoneActive = false;

    /**
     * Every first-person bone, so bulk operations can go over them.
     */
    private static final PlayerAnimBone[] BONES = {fpPlayerArms, fpRightArm, fpLeftArm, fpRightItem, fpLeftItem, fpCamera};

    /**
     * Bone names handled directly by the renderer. Everything else is kept in {@link #EXTRA_BONES} so
     * mods can render their own details with the animation's exact transform.
     */
    private static final Set<String> CORE_BONE_NAMES = Set.of(
            "fp_player_arms",
            FpArmRenderUtil.RIGHT_ARM_BONE,
            FpArmRenderUtil.LEFT_ARM_BONE,
            "right_arm",
            "left_arm",
            "fp_right_arm",
            "fp_left_arm",
            "fp_right_item",
            "fp_left_item",
            "fp_r_item",
            "fp_l_item",
            "fp_camera"
    );

    private static final Map<String, PlayerAnimBone> EXTRA_BONES = new Object2ObjectOpenHashMap<>();

    /**
     * Updates the extra-bone cache with every bone the FP animation drives.
     */
    public static void updateExtraBones(AvatarAnimManager manager, Set<String> activeBoneNames) {
        EXTRA_BONES.keySet().retainAll(activeBoneNames);
        for (String name : activeBoneNames) {
            if (CORE_BONE_NAMES.contains(name)) continue;
            PlayerAnimBone target = EXTRA_BONES.computeIfAbsent(name, PlayerAnimBone::new);
            copy(get3DTransform(manager, name), target);
        }
    }

    /**
     * @return the transform of an extra bone of the current FP animation (for example {@code mag},
     * {@code slide}, {@code flash} or the model root), or {@code null} if it is not animated.
     */
    public static @Nullable PlayerAnimBone getExtraBone(String name) {
        return EXTRA_BONES.get(name);
    }

    /**
     * @return every extra bone transform of the current FP animation, read-only.
     */
    public static Map<String, PlayerAnimBone> getExtraBones() {
        return Collections.unmodifiableMap(EXTRA_BONES);
    }

    /**
     * First-person display translation of the arm model, in 1/16 block, like the {@code translation}
     * of a {@code firstperson_*} item model entry. An FP animation can override it through the extra
     * data keys {@code fpDisplayTranslation}, {@code fpDisplayScale} and {@code fpDisplayAnchor}.
     */
    public static final float[] ARM_DISPLAY_TRANSLATION = new float[]{0.0F, 0.0F, 0.0F};

    /**
     * Uniform first-person display scale of the arm model, see {@link #ARM_DISPLAY_TRANSLATION}.
     */
    public static float ARM_DISPLAY_SCALE = 1.0F;

    /**
     * Base transform configured by the mod, used for animations without their own display data.
     */
    private static final float[] BASE_ARM_DISPLAY_TRANSLATION = new float[]{0.0F, 0.0F, 0.0F};
    private static float baseArmDisplayScale = 1.0F;

    /**
     * Extra animation data keys used to override {@link #ARM_DISPLAY_TRANSLATION} and
     * {@link #ARM_DISPLAY_SCALE} per animation.
     */
    public static final String FP_DISPLAY_TRANSLATION_KEY = "fpDisplayTranslation";
    public static final String FP_DISPLAY_SCALE_KEY = "fpDisplayScale";

    /**
     * Sets the first-person display transform used by the arm pass. Mods can call this when their
     * item model uses a different {@code firstperson_*} display entry.
     */
    public static void setArmDisplayTransform(float x, float y, float z, float scale) {
        BASE_ARM_DISPLAY_TRANSLATION[0] = x;
        BASE_ARM_DISPLAY_TRANSLATION[1] = y;
        BASE_ARM_DISPLAY_TRANSLATION[2] = z;
        baseArmDisplayScale = scale;

        ARM_DISPLAY_TRANSLATION[0] = x;
        ARM_DISPLAY_TRANSLATION[1] = y;
        ARM_DISPLAY_TRANSLATION[2] = z;
        ARM_DISPLAY_SCALE = scale;
    }

    private static boolean handAnchored = false;

    /**
     * Extra animation data key that selects the frame the arm rig is rendered in: {@code "camera"}
     * (default, origin on the view axis) or {@code "hand"} (the vanilla hand anchor used by AzureLib
     * item rigs).
     */
    public static final String FP_DISPLAY_ANCHOR_KEY = "fpDisplayAnchor";

    /**
     * @return true when the rig is rendered in the vanilla hand frame ({@code fpDisplayAnchor} =
     * {@code "hand"}) instead of the camera frame.
     */
    public static boolean isHandAnchored() {
        return handAnchored;
    }

    /**
     * Applies the display transform carried by the current animation, falling back to the transform
     * configured through {@link #setArmDisplayTransform}.
     */
    public static void applyAnimationDisplayTransform(@Nullable List<?> translation, @Nullable Float scale, @Nullable Object anchor) {
        handAnchored = anchor instanceof String value && "hand".equalsIgnoreCase(value);

        if (translation != null && translation.size() >= 3
                && translation.get(0) instanceof Number
                && translation.get(1) instanceof Number
                && translation.get(2) instanceof Number) {
            ARM_DISPLAY_TRANSLATION[0] = ((Number) translation.get(0)).floatValue();
            ARM_DISPLAY_TRANSLATION[1] = ((Number) translation.get(1)).floatValue();
            ARM_DISPLAY_TRANSLATION[2] = ((Number) translation.get(2)).floatValue();
        } else {
            ARM_DISPLAY_TRANSLATION[0] = BASE_ARM_DISPLAY_TRANSLATION[0];
            ARM_DISPLAY_TRANSLATION[1] = BASE_ARM_DISPLAY_TRANSLATION[1];
            ARM_DISPLAY_TRANSLATION[2] = BASE_ARM_DISPLAY_TRANSLATION[2];
        }
        ARM_DISPLAY_SCALE = scale != null ? scale : baseArmDisplayScale;
    }

    /**
     * Restores the neutral (identity) first-person display transform.
     */
    public static void resetArmDisplayTransform() {
        setArmDisplayTransform(0.0F, 0.0F, 0.0F, 1.0F);
    }

    /**
     * @return true when the bone has no position, rotation or scale, i.e. it is not animated.
     */
    public static boolean isIdentity(PlayerAnimBone bone) {
        return Math.abs(bone.position.x) < 1.0E-6F
                && Math.abs(bone.position.y) < 1.0E-6F
                && Math.abs(bone.position.z) < 1.0E-6F
                && Math.abs(bone.rotation.x) < 1.0E-6F
                && Math.abs(bone.rotation.y) < 1.0E-6F
                && Math.abs(bone.rotation.z) < 1.0E-6F
                && Math.abs(bone.scale.x - 1.0F) < 1.0E-6F
                && Math.abs(bone.scale.y - 1.0F) < 1.0E-6F
                && Math.abs(bone.scale.z - 1.0F) < 1.0E-6F;
    }

    /**
     * Copies a complete bone transform (position, rotation and scale) into one of the render state bones.
     */
    public static void copy(PlayerAnimBone from, PlayerAnimBone to) {
        //? if >=26.1.2 {
        // PAL 1.2 keeps a bone's transform in public JOML vectors, so the copy is vector-wise.
        to.position.set(from.position);
        to.rotation.set(from.rotation);
        to.scale.set(from.scale);
        //?} else {
        /*to.setPosX(from.position.x);
        to.setPosY(from.position.y);
        to.setPosZ(from.position.z);

        to.setRotX(from.rotation.x);
        to.setRotY(from.rotation.y);
        to.setRotZ(from.rotation.z);

        to.setScaleX(from.scale.x);
        to.setScaleY(from.scale.y);
        to.setScaleZ(from.scale.z);
        *///?}
    }

    /**
     * @return true when the current animation contains a right-hand item bone ({@code fp_r_item} or
     * the legacy {@code fp_right_item}).
     */
    public static boolean hasRightItemBone() {
        return rightItemBoneActive;
    }

    /**
     * @return true when the current animation contains a left-hand item bone ({@code fp_l_item} or
     * the legacy {@code fp_left_item}).
     */
    public static boolean hasLeftItemBone() {
        return leftItemBoneActive;
    }

    /**
     * @param right true for the right hand, false for the left hand
     * @return true when the current animation contains the item bone for that hand
     */
    public static boolean hasItemBone(boolean right) {
        return right ? rightItemBoneActive : leftItemBoneActive;
    }

    /**
     * Updates whether the current animation contains the item bones. Called by
     * {@link FpAnimationSystem} from the animation's actual bone set, not from transform identity.
     */
    public static void setItemBoneActive(boolean right, boolean left) {
        rightItemBoneActive = right;
        leftItemBoneActive = left;
    }

    /**
     * @return true when the current animation contains the right arm bone ({@code rightArm} /
     * {@code right_arm}, or the legacy {@code fp_right_arm}).
     */
    public static boolean hasRightArmBone() {
        return rightArmBoneActive;
    }

    /**
     * @return true when the current animation contains the left arm bone ({@code leftArm} /
     * {@code left_arm}, or the legacy {@code fp_left_arm}).
     */
    public static boolean hasLeftArmBone() {
        return leftArmBoneActive;
    }

    /**
     * @param right true for the right hand, false for the left hand
     * @return true when the current animation contains the arm bone for that hand
     */
    public static boolean hasArmBone(boolean right) {
        return right ? rightArmBoneActive : leftArmBoneActive;
    }

    /**
     * Updates whether the current animation contains the arm bones. Called by
     * {@link FpAnimationSystem} from the animation's actual bone set.
     */
    public static void setArmBoneActive(boolean right, boolean left) {
        rightArmBoneActive = right;
        leftArmBoneActive = left;
    }

    /**
     * Saves the current camera transform as the previous tick. Call once before copying a new
     * camera transform into {@link #fpCamera}.
     */
    public static void beginCameraTick() {
        copy(fpCamera, fpCameraPrevious);
    }

    /**
     * @param partialTick frame interpolation factor in {@code [0, 1]}
     * @return the camera transform interpolated between the last two ticks
     */
    public static PlayerAnimBone getCameraForRender(float partialTick) {
        if (!isFpActive || partialTick <= 0.0F) return fpCamera;
        if (partialTick >= 1.0F) return fpCamera;

        float t = Math.max(0.0F, Math.min(1.0F, partialTick));
        lerp(fpCameraPrevious, fpCamera, fpCameraRender, t);
        return fpCameraRender;
    }

    private static void lerp(PlayerAnimBone from, PlayerAnimBone to, PlayerAnimBone target, float t) {
        //? if >=26.1.2 {
        target.position.set(lerp(from.position.x, to.position.x, t),
                lerp(from.position.y, to.position.y, t), lerp(from.position.z, to.position.z, t));
        target.rotation.set(lerp(from.rotation.x, to.rotation.x, t),
                lerp(from.rotation.y, to.rotation.y, t), lerp(from.rotation.z, to.rotation.z, t));
        target.scale.set(lerp(from.scale.x, to.scale.x, t),
                lerp(from.scale.y, to.scale.y, t), lerp(from.scale.z, to.scale.z, t));
        //?} else {
        /*target.setPosX(lerp(from.position.x, to.position.x, t));
        target.setPosY(lerp(from.position.y, to.position.y, t));
        target.setPosZ(lerp(from.position.z, to.position.z, t));

        target.setRotX(lerp(from.rotation.x, to.rotation.x, t));
        target.setRotY(lerp(from.rotation.y, to.rotation.y, t));
        target.setRotZ(lerp(from.rotation.z, to.rotation.z, t));

        target.setScaleX(lerp(from.scale.x, to.scale.x, t));
        target.setScaleY(lerp(from.scale.y, to.scale.y, t));
        target.setScaleZ(lerp(from.scale.z, to.scale.z, t));
        *///?}
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /**
     * Resets all transforms to identity and leaves first-person arm mode.
     */
    public static void reset() {
        isFpActive = false;
        fpRootPivot = FpArmRenderUtil.NO_PIVOT;
        rightItemBoneActive = false;
        leftItemBoneActive = false;
        rightArmBoneActive = false;
        leftArmBoneActive = false;
        for (PlayerAnimBone bone : BONES) {
            bone.setToInitialPose();
        }
        fpCameraPrevious.setToInitialPose();
        fpCameraRender.setToInitialPose();
        EXTRA_BONES.clear();
        itemFrameCaptured = false;
        // Restore the mod-level base display transform instead of keeping the last animation's values.
        applyAnimationDisplayTransform(null, null, null);

        // The vanilla arm parts of the shared player model still carry the pose of the last rendered
        // first-person frame (the collector reads them one frame after submission). Put them back now
        // that the arm mode is off, so the third-person renderer keeps the real arm pose.
        FpArmRenderUtil.resetPlayerModelArms();
    }
}
