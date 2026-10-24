package com.github.razorplay01.fp_pal.firstperson;

import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
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
     * Whether the held item of the local player is being drawn right now with the {@code fp_r_item} /
     * {@code fp_l_item} bone pose applied.
     * <p>
     * Vanilla draws a held item with the display transform of its {@code ItemDisplayContext}
     * ({@code firstperson_righthand}: {@code rotationXYZ(0, -90, 25)}, {@code scale(0.68)},
     * {@code translate(i * 1.13, 3.2, 1.13)}). That transform is not part of the animation, so while
     * this flag is set {@code ItemTransformMixin} skips it and the item lands exactly where the
     * {@code fp_*_item} bone puts it, which is what Blockbench shows. The flag is only raised for the
     * frames the item pass actually pushes a bone pose for, so an item in a hand the clip does not
     * animate keeps vanilla's transform.
     */
    public static boolean isFpItemPass = false;

    /**
     * Snapshot of the pose the vanilla first-person item pass starts from, taken at the head of
     * {@code ItemInHandRenderer#renderHandsWithItems} (see {@link #captureItemFrame(PoseStack)}).
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
     * Remembers the pose the vanilla first-person item pass starts from: the view frame the hands are
     * drawn in, before vanilla adds the hand sway and, per hand, the equip progress, the swing, the
     * hand anchor and the use-item pose.
     * <p>
     * Vanilla builds all of those on top of the caller's pose and gives the animation no say, so
     * {@code FpArmRendererMixin} rewinds to this frame before applying the animated bone poses. The
     * arms and the held item then sit in exactly the frame the animation describes, which is what the
     * Blockbench preview shows.
     */
    public static void captureItemFrame(PoseStack poseStack) {
        PoseStack.Pose pose = poseStack.last();
        ITEM_FRAME_POSE.set(pose.pose());
        ITEM_FRAME_NORMAL.set(pose.normal());
        itemFrameCaptured = true;
    }

    /**
     * Rewinds the top of the pose stack to the frame captured by {@link #captureItemFrame(PoseStack)},
     * dropping every transform vanilla applied to the first-person pass since then (the hand sway, the
     * equip progress, the swing, the hand anchor and the use-item poses). Does nothing before the first
     * capture.
     */
    public static void restoreItemFrame(PoseStack poseStack) {
        if (!itemFrameCaptured) return;
        PoseStack.Pose pose = poseStack.last();
        pose.pose().set(ITEM_FRAME_POSE);
        pose.normal().set(ITEM_FRAME_NORMAL);
    }

    /**
     * @return true if the given entity is currently playing a first-person animation, which is what
     * makes the render mixins replace the vanilla arm. The state lives in the addon's own controller
     * ({@link FpAnimationController#isPlayerArmMode()}); PAL only knows the vanilla modes.
     */
    public static boolean isPlayerArmMode(@Nullable Entity entity) {
        if (entity instanceof IFpAnimatedPlayer fpPlayer) {
            FpAnimationController controller = fpPlayer.fpPal$getFpAnimationController();
            return controller != null && controller.isPlayerArmMode();
        }
        return false;
    }

    /**
     * The {@link FirstPersonConfiguration} that decides what the first-person pass draws (the arms and
     * the held items of each hand).
     * <p>
     * It has to come from this addon's controller, <b>not</b> from PAL's manager:
     * {@code AnimationStack#getFirstPersonConfiguration} only resolves the configuration of the layer
     * that reports a non-{@code NONE} first-person mode, and this controller deliberately reports
     * {@link FirstPersonMode#NONE} so that PAL's own first-person machinery stays idle. Asking PAL's
     * manager therefore always returned {@link FirstPersonConfiguration}'s defaults, whose
     * {@code showRightArm} / {@code showLeftArm} are {@code false} while
     * {@code showRightItem} / {@code showLeftItem} are {@code true} — which is exactly what kept the
     * animated arms hidden while the items and the extra bones kept rendering.
     */
    public static FirstPersonConfiguration firstPersonConfiguration(@Nullable Entity entity) {
        if (entity instanceof IFpAnimatedPlayer fpPlayer) {
            FpAnimationController controller = fpPlayer.fpPal$getFpAnimationController();
            if (controller != null) return controller.getFirstPersonConfiguration();
        }
        return FpAnimationController.DEFAULT_FP_CONFIGURATION;
    }

    /**
     * Rotates the pose by a quaternion.
     * <p>
     * 26.3 renamed {@code PoseStack#mulPose(Quaternionfc)} to {@code rotate} (mulPose now only takes
     * matrices), so the difference is hidden here instead of at every call site.
     */
    public static void rotatePose(PoseStack poseStack, Quaternionf rotation) {
        //? if >=26.3 {
        //poseStack.rotate(rotation);
        //?} else {
        poseStack.mulPose(rotation);
        //?}
    }

    /**
     * Reads a bone transform out of PAL's animation stack.
     * <p>
     * PAL 1.2 (26.x) changed {@code get3DTransform} to fill the bone passed in instead of returning a
     * new one, so this wrapper keeps the call sites identical across versions.
     */
    public static PlayerAnimBone get3DTransform(PlayerAnimManager manager, String boneName) {
        PlayerAnimBone bone = new PlayerAnimBone(boneName);
        //? if >=26.1.2 {
        //manager.get3DTransform(bone);
        //?} else {
        bone = manager.get3DTransform(bone);
        //?}
        return bone;
    }

    /**
     * The FP root bone ({@code fp_player_arms}), which moves and rotates the whole rig
     * (arms, held items and the camera).
     */
    public static final PlayerAnimBone fpPlayerArms = new PlayerAnimBone("fp_player_arms");

    /**
     * Rotation centre of the animation's model root in model units. Most rigs (including
     * {@code player_arms_import.bbmodel}) use the origin, but an AzureLib item model can put its root
     * pivot anywhere; {@link FpAnimationSystem} sets this when it resolves the root bone. Ignoring it
     * makes root rotation animations ({@code firing}, {@code reload}) orbit the wrong point.
     */
    public static float[] fpRootPivot = FpArmRenderUtil.NO_PIVOT;

    /**
     * The FP right arm bone transform. The name is the same one AzureLib and
     * {@code player_arms_import.bbmodel} use, so the same Blockbench animation can drive it.
     */
    public static final PlayerAnimBone fpRightArm = new PlayerAnimBone(FpArmRenderUtil.RIGHT_ARM_BONE);

    /**
     * The FP left arm bone transform. The name is the same one AzureLib and
     * {@code player_arms_import.bbmodel} use.
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
     * Previous tick's camera transform, used to interpolate the camera between ticks. Camera setup
     * runs at frame rate while the animation state is updated at tick rate; without this the view
     * would visibly step at 20 Hz.
     */
    private static final PlayerAnimBone fpCameraPrevious = new PlayerAnimBone("fp_camera");

    /**
     * Scratch bone returned by {@link #getCameraForRender(float)}. Rendering happens on one thread,
     * so reusing it avoids allocating a bone every frame.
     */
    private static final PlayerAnimBone fpCameraRender = new PlayerAnimBone("fp_camera");

    /**
     * Whether the current animation actually contains an item bone ({@code fp_r_item} /
     * {@code fp_right_item}, or {@code fp_l_item} / {@code fp_left_item}). A zero transform is still
     * a valid animated item bone, so presence cannot be inferred from {@link #isIdentity(PlayerAnimBone)}.
     */
    private static boolean rightItemBoneActive = false;
    private static boolean leftItemBoneActive = false;

    /**
     * Whether the current animation contains the arm bone for a hand. This is checked against the
     * animation's own bone list, not against the resulting transform, so a clip that only animates
     * one hand can hide the other instead of falling back to its idle pose.
     */
    private static boolean rightArmBoneActive = false;
    private static boolean leftArmBoneActive = false;

    /**
     * Every first-person bone, so bulk operations can go over them.
     */
    private static final PlayerAnimBone[] BONES = {fpPlayerArms, fpRightArm, fpLeftArm, fpRightItem, fpLeftItem, fpCamera};

    /**
     * Bone names handled directly by the renderer. Everything else an animation animates is kept in
     * {@link #EXTRA_BONES} so mods can render their own details (weapon magazines, slides, flashes...)
     * with the exact transform the animation gives them.
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
    public static void updateExtraBones(PlayerAnimManager manager, Set<String> activeBoneNames) {
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
     * First-person display translation of the arm model, in 1/16 block, exactly like the
     * {@code translation} of a {@code firstperson_righthand} / {@code firstperson_lefthand} entry in an
     * item model JSON. Together with {@link #ARM_DISPLAY_SCALE} it is the equivalent of the item
     * display transform AzureLib has when it renders the arm bones.
     * <p>
     * An FP animation can override it with the extra data keys
     * {@code fpDisplayTranslation} (three numbers), {@code fpDisplayScale} (one number) and
     * {@code fpDisplayAnchor} ({@code "camera"} or {@code "hand"}) inside its
     * {@code player_animation_library} object. The peacemaker example animation ships with
     * {@code [-2.5, 0.5, 1.5]} / {@code 1.5} / {@code "hand"}. Without extra data the rig sits on the
     * camera anchor, so an arm rig authored around the model origin stays centred.
     */
    public static final float[] ARM_DISPLAY_TRANSLATION = new float[]{0.0F, 0.0F, 0.0F};

    /**
     * Uniform first-person display scale of the arm model, see {@link #ARM_DISPLAY_TRANSLATION}.
     */
    public static float ARM_DISPLAY_SCALE = 1.0F;

    /**
     * Base transform configured by the mod, restored for animations that do not carry their own
     * {@code fpDisplayTranslation} / {@code fpDisplayScale} extra data.
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
     * Extra animation data key that selects the frame the arm rig is rendered in:
     * <ul>
     *     <li>{@code "camera"} (default) - the rig origin is on the view axis, so a rig authored
     *     around {@code player_arms_import.bbmodel}'s origin (arms at {@code x = ±6}) shows both hands
     *     centred on screen, exactly like the Blockbench preview;</li>
     *     <li>{@code "hand"} - the rig origin is the vanilla first-person hand anchor
     *     ({@code ±0.56, -0.52, -0.72}), which is the frame AzureLib item rigs (guns) are authored
     *     against.</li>
     * </ul>
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
     * Applies the optional first-person display transform carried by the current animation, falling
     * back to the transform configured through {@link #setArmDisplayTransform}.
     *
     * @param translation the {@code fpDisplayTranslation} list, or {@code null}
     * @param scale       the {@code fpDisplayScale} value, or {@code null}
     * @param anchor      the {@code fpDisplayAnchor} value, or {@code null} for the camera frame
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
     * @return true when the bone has no position, rotation or scale, which is how a bone that the
     * current animation does not animate is returned.
     */
    public static boolean isIdentity(PlayerAnimBone bone) {
        return Math.abs(bone.getPosX()) < 1.0E-6F
                && Math.abs(bone.getPosY()) < 1.0E-6F
                && Math.abs(bone.getPosZ()) < 1.0E-6F
                && Math.abs(bone.getRotX()) < 1.0E-6F
                && Math.abs(bone.getRotY()) < 1.0E-6F
                && Math.abs(bone.getRotZ()) < 1.0E-6F
                && Math.abs(bone.getScaleX() - 1.0F) < 1.0E-6F
                && Math.abs(bone.getScaleY() - 1.0F) < 1.0E-6F
                && Math.abs(bone.getScaleZ() - 1.0F) < 1.0E-6F;
    }

    /**
     * Copies a complete bone transform (position, rotation and scale) into one of the render state bones.
     */
    public static void copy(PlayerAnimBone from, PlayerAnimBone to) {
        //? if >=26.1.2 {
        /*// PAL 1.2 keeps a bone's transform in public JOML vectors, so the copy is vector-wise.
        to.position.set(from.position);
        to.rotation.set(from.rotation);
        to.scale.set(from.scale);
        *///?} else {
        to.setPosX(from.getPosX());
        to.setPosY(from.getPosY());
        to.setPosZ(from.getPosZ());

        to.setRotX(from.getRotX());
        to.setRotY(from.getRotY());
        to.setRotZ(from.getRotZ());

        to.setScaleX(from.getScaleX());
        to.setScaleY(from.getScaleY());
        to.setScaleZ(from.getScaleZ());
        //?}
    }

    /**
     * @return true when the current animation contains a right-hand item bone, either the short
     * {@code fp_r_item} alias or the legacy {@code fp_right_item} name.
     */
    public static boolean hasRightItemBone() {
        return rightItemBoneActive;
    }

    /**
     * @return true when the current animation contains a left-hand item bone, either the short
     * {@code fp_l_item} alias or the legacy {@code fp_left_item} name.
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
     * @return true when the current animation contains the right arm bone
     * ({@code rightArm} / normalized {@code right_arm}, or the legacy {@code fp_right_arm})
     */
    public static boolean hasRightArmBone() {
        return rightArmBoneActive;
    }

    /**
     * @return true when the current animation contains the left arm bone
     * ({@code leftArm} / normalized {@code left_arm}, or the legacy {@code fp_left_arm})
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
        /*target.position.set(lerp(from.getPosX(), to.getPosX(), t),
                lerp(from.getPosY(), to.getPosY(), t), lerp(from.getPosZ(), to.getPosZ(), t));
        target.rotation.set(lerp(from.getRotX(), to.getRotX(), t),
                lerp(from.getRotY(), to.getRotY(), t), lerp(from.getRotZ(), to.getRotZ(), t));
        target.scale.set(lerp(from.getScaleX(), to.getScaleX(), t),
                lerp(from.getScaleY(), to.getScaleY(), t), lerp(from.getScaleZ(), to.getScaleZ(), t));
        *///?} else {
        target.setPosX(lerp(from.getPosX(), to.getPosX(), t));
        target.setPosY(lerp(from.getPosY(), to.getPosY(), t));
        target.setPosZ(lerp(from.getPosZ(), to.getPosZ(), t));

        target.setRotX(lerp(from.getRotX(), to.getRotX(), t));
        target.setRotY(lerp(from.getRotY(), to.getRotY(), t));
        target.setRotZ(lerp(from.getRotZ(), to.getRotZ(), t));

        target.setScaleX(lerp(from.getScaleX(), to.getScaleX(), t));
        target.setScaleY(lerp(from.getScaleY(), to.getScaleY(), t));
        target.setScaleZ(lerp(from.getScaleZ(), to.getScaleZ(), t));
        //?}
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /**
     * Reset all transforms to identity (position and rotation to zero, scale to one).
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
        // Go back to the mod-level base transform instead of keeping the last animation's
        // firstperson_* values.
        applyAnimationDisplayTransform(null, null, null);
    }
}