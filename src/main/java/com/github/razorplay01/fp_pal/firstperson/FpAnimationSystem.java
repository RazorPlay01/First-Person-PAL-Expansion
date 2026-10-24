package com.github.razorplay01.fp_pal.firstperson;

import com.github.razorplay01.fp_pal.ModTemplate;
import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.enums.State;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Central system for first-person animations.
 * Handles registration, ticking, and synchronization of FP animations.
 */
public class FpAnimationSystem {

    public static final ResourceLocation FP_ANIMATION_LAYER_ID = ModTemplate.id("fp_factory");
    private static boolean initialized = false;
    private static FpAnimationController fpController = null;

    /**
     * Initialize the FP animation system. Call this during mod init.
     */
    public static void init() {
        if (initialized) {
            ModTemplate.LOGGER.warn("FpAnimationSystem already initialized, skipping");
            return;
        }
        initialized = true;

        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(FP_ANIMATION_LAYER_ID, 500,
                player -> {
                    try {
                        PlayerAnimManager thirdPersonManager = ((IAnimatedPlayer) player).playerAnimLib$getAnimManager();
                        FpAnimationController ctrl = new FpAnimationController(player,
                            (handler, state, animSetter) -> {
                                FpAnimationController fpCtrl = (FpAnimationController) handler;

                                if (fpCtrl.isStopRequested()) {
                                    fpCtrl.clearStopRequest();
                                    fpCtrl.setActive(false);
                                    return PlayState.STOP;
                                }

                                if (fpCtrl.isPendingPlay() && fpCtrl.getCurrentFpAnimation() != null) {
                                    Animation fpAnim = FpAnimationResources.getAnimation(fpCtrl.getCurrentFpAnimation());
                                    fpCtrl.clearPendingPlay();
                                    if (fpAnim != null) {
                                        // setAnimation() is a no-op for an equal RawAnimation that is already
                                        // loaded, so reset first to restart the same animation from tick 0.
                                        fpCtrl.forceAnimationReset();
                                        animSetter.setAnimation(RawAnimation.begin().then(fpAnim, Animation.LoopType.DEFAULT), 0);
                                        fpCtrl.setActive(true);
                                        return PlayState.CONTINUE;
                                    }
                                }

                                // Keep playing while the underlying animation is still running, otherwise
                                // it would be stopped one tick after it starts. The animation's own loop
                                // type is used (DEFAULT above), so looping FP animations stay active and
                                // hold_on_last_frame ones stop when they finish. Use isRunning() instead
                                // of isActive(): the latter is false in third person by design, but the
                                // animation must keep ticking until it ends.
                                if (fpCtrl.isRunning()) {
                                    if (fpCtrl.getAnimationState() == State.RUNNING) {
                                        return PlayState.CONTINUE;
                                    }
                                    fpCtrl.setActive(false);
                                }

                                return PlayState.STOP;
                            },
                            thirdPersonManager
                    );
                        fpController = ctrl;
                        // Hand the controller to the player so the render mixins can reach it without
                        // going through PAL: PAL has no first-person API of its own here.
                        if (player instanceof IFpAnimatedPlayer fpPlayer) {
                            fpPlayer.fpPal$setFpAnimationController(ctrl);
                        }
                        return ctrl;
                    } catch (Exception e) {
                        ModTemplate.LOGGER.error("Error creating FpAnimationController", e);
                        return null;
                    }
                }
        );
    }

    /**
     * Update the FP render state. Call this every tick.
     */
    public static void updateRenderState(AbstractClientPlayer player) {
        // FpRenderState is a single process-wide cache consumed by the local first-person renderer.
        // Remote players also tick the same mixin; letting them write/reset it would wipe the local
        // camera/arm state every tick.
        if (!(player instanceof LocalPlayer)) return;

        if (!(player instanceof IAnimatedPlayer animatedPlayer)) {
            FpRenderState.reset();
            return;
        }

        PlayerAnimManager manager = animatedPlayer.playerAnimLib$getAnimManager();

        // Only this addon's own controller renders the arms from the fp_* bones. PAL's modes
        // (THIRD_PERSON_MODEL, NONE or DISABLED) leave first person entirely to vanilla.
        if (!FpRenderState.isPlayerArmMode(player)) {
            FpRenderState.reset();
            return;
        }

        FpRenderState.isFpActive = true;

        FpAnimationController fpController = getControllerFor(player);
        Set<String> animatedBoneNames = fpController != null
                ? fpController.getAnimatedBoneNames()
                : Set.of();

        // Save the previous camera transform before replacing it with this tick's value. Camera
        // setup runs per frame, so this is what makes fp_camera motion smooth instead of 20 Hz.
        FpRenderState.beginCameraTick();

        // Full transforms (position, rotation and scale) are copied here; copying only rotations would make
        // any bone that is moved or scaled by the animation look like it does nothing. Arm presence is
        // resolved from the animation's bone list (not from the transform), so a clip that only animates
        // one hand skips the other completely instead of falling back to its idle pose.
        FpRenderState.copy(resolveRootBone(manager), FpRenderState.fpPlayerArms);
        // PAL's AnimationLoader normalizes CamelCase bone names to snake_case
        // (UniversalAnimLoader.getCorrectPlayerBoneName), so rightArm/leftArm from an AzureLib
        // animation arrive here as right_arm/left_arm.
        boolean rightArmBone = copyArmTransform(manager, animatedBoneNames, FpRenderState.fpRightArm,
                "right_arm", "rightArm", "fp_right_arm");
        boolean leftArmBone = copyArmTransform(manager, animatedBoneNames, FpRenderState.fpLeftArm,
                "left_arm", "leftArm", "fp_left_arm");
        FpRenderState.setArmBoneActive(rightArmBone, leftArmBone);
        boolean rightItemBone = copyItemTransform(manager, animatedBoneNames,
                "fp_r_item", "fp_right_item", FpRenderState.fpRightItem);
        boolean leftItemBone = copyItemTransform(manager, animatedBoneNames,
                "fp_l_item", "fp_left_item", FpRenderState.fpLeftItem);
        FpRenderState.setItemBoneActive(rightItemBone, leftItemBone);
        FpRenderState.copy(FpRenderState.get3DTransform(manager, "fp_camera"), FpRenderState.fpCamera);

        // Keep every other animated bone (mag, slide, flash, the model root...) available so mods can
        // render extra details, and so PAL's sound/particle keyframes keep working as usual.
        if (fpController != null) {
            FpRenderState.updateExtraBones(manager, animatedBoneNames);

            // The first-person display transform of the item model that owns the rig can travel with
            // the animation (fpDisplayTranslation / fpDisplayScale), so every rig keeps its own
            // firstperson_* values without a global hardcoded transform.
            Animation currentAnimation = fpController.getCurrentAnimationInstance();
            if (currentAnimation != null) {
                Float scale = currentAnimation.data().getNullable(FpRenderState.FP_DISPLAY_SCALE_KEY);
                FpRenderState.applyAnimationDisplayTransform(
                        currentAnimation.data().getList(FpRenderState.FP_DISPLAY_TRANSLATION_KEY), scale,
                        currentAnimation.data().getNullable(FpRenderState.FP_DISPLAY_ANCHOR_KEY));
            } else {
                FpRenderState.applyAnimationDisplayTransform(null, null, null);
            }
        } else {
            FpRenderState.applyAnimationDisplayTransform(null, null, null);
        }
    }

    /**
     * AzureLib animations usually animate a model root instead of PAL's synthetic
     * {@code fp_player_arms}. If the animation does not use the PAL root, take the first animated
     * AzureLib-style root so the whole rig still follows the animation.
     */
    private static final String[] AZURELIB_ROOT_BONES = {"player_arms", "playerArms", "peacemaker", "group", "root"};

    /**
     * Stored pivot of the {@code peacemaker} root bone: the geo pivot is
     * {@code [-0.03125, 1.425, -1.2375]} and AzureLib negates X when it bakes the model.
     */
    private static final float[] PEACEMAKER_ROOT_PIVOT = new float[]{0.03125F, 1.425F, -1.2375F};

    private static PlayerAnimBone resolveRootBone(PlayerAnimManager manager) {
        PlayerAnimBone palRoot = FpRenderState.get3DTransform(manager, "fp_player_arms");
        if (!FpRenderState.isIdentity(palRoot)) {
            FpRenderState.fpRootPivot = FpArmRenderUtil.NO_PIVOT;
            return palRoot;
        }
        for (String name : AZURELIB_ROOT_BONES) {
            PlayerAnimBone candidate = FpRenderState.get3DTransform(manager, name);
            if (!FpRenderState.isIdentity(candidate)) {
                FpRenderState.fpRootPivot = "peacemaker".equals(name) ? PEACEMAKER_ROOT_PIVOT : FpArmRenderUtil.NO_PIVOT;
                return candidate;
            }
        }
        FpRenderState.fpRootPivot = FpArmRenderUtil.NO_PIVOT;
        return palRoot;
    }

    /**
     * Copies an arm bone only when the current animation actually contains it. Presence is checked
     * against the animation's bone list, because an arm with an all-zero transform is still animated.
     * A missing arm stays hidden instead of falling back to the idle pose.
     *
     * @return true if the animation contains any of the accepted names
     */
    private static boolean copyArmTransform(PlayerAnimManager manager, Set<String> animatedBoneNames,
                                            PlayerAnimBone target, String... acceptedNames) {
        for (String name : acceptedNames) {
            if (!animatedBoneNames.contains(name)) continue;
            FpRenderState.copy(FpRenderState.get3DTransform(manager, name), target);
            return true;
        }
        target.setToInitialPose();
        return false;
    }

    /**
     * Resolves the short ({@code fp_r_item} / {@code fp_l_item}) and legacy
     * ({@code fp_right_item} / {@code fp_left_item}) item bone names. Presence is checked against the
     * animation's bone list, because an item bone with an all-zero transform is still animated.
     *
     * @return true if the animation contains either name
     */
    private static boolean copyItemTransform(PlayerAnimManager manager, Set<String> animatedBoneNames,
                                             String shortName, String legacyName, PlayerAnimBone target) {
        String selected = animatedBoneNames.contains(shortName) ? shortName
                : animatedBoneNames.contains(legacyName) ? legacyName
                : null;
        if (selected == null) {
            target.setToInitialPose();
            return false;
        }

        FpRenderState.copy(FpRenderState.get3DTransform(manager, selected), target);
        return true;
    }

    /**
     * Play a FP animation for the given player.
     */
    public static boolean playFpAnimation(AbstractClientPlayer player, ResourceLocation fpAnimationId) {
        if (!FpAnimationResources.hasAnimation(fpAnimationId)) {
            ModTemplate.LOGGER.warn("FP animation not found: {}", fpAnimationId);
            return false;
        }

        FpAnimationController controller = getControllerFor(player);
        if (controller == null) {
            ModTemplate.LOGGER.error("FpAnimationController is null, cannot play {}", fpAnimationId);
            return false;
        }

        controller.setCurrentFpAnimation(fpAnimationId);

        // A matching item model is optional. When the animation has one
        // (fp_models/<animation>.geo.json or the .animation.json source name, plus the same-named
        // texture) it is selected automatically, so /fp_anim model is no longer required. The
        // preview is global, so only resolve it for the local player.
        if (player == Minecraft.getInstance().player) {
            FpModelRenderer.loadForAnimation(fpAnimationId);
        }
        return true;
    }

    /**
     * Stop the currently playing FP animation.
     */
    public static void stopFpAnimation() {
        FpAnimationController controller = getControllerFor(Minecraft.getInstance().player);
        if (controller != null) {
            controller.requestStop();
        }
    }

    /**
     * Resolve the FP controller for the given player.
     * Prefers the per-player captured controller (from IAnimatedPlayer), falls back to the static one.
     */
    private static FpAnimationController getControllerFor(@Nullable AbstractClientPlayer player) {
        if (player instanceof IFpAnimatedPlayer fpPlayer) {
            FpAnimationController controller = fpPlayer.fpPal$getFpAnimationController();
            if (controller != null) return controller;
        }
        return fpController;
    }

    /**
     * @return the first-person mode the local player's FP controller currently reports,
     * or null if there is no controller.
     */
    @Nullable
    public static FirstPersonMode getFirstPersonMode() {
        FpAnimationController controller = getControllerFor(Minecraft.getInstance().player);
        return controller != null ? controller.getFirstPersonMode() : null;
    }

    /**
     * Force a first-person mode on the local player's FP controller, overriding the automatic
     * {@link FirstPersonMode#NONE} reported while an FP animation is playing.
     *
     * @return false if there is no FP controller for the local player
     */
    public static boolean setFirstPersonMode(FirstPersonMode mode) {
        FpAnimationController controller = getControllerFor(Minecraft.getInstance().player);
        if (controller == null) return false;
        controller.setFirstPersonMode(mode);
        return true;
    }

    /**
     * Remove a forced first-person mode, going back to the automatic behaviour.
     *
     * @return false if there is no FP controller for the local player
     */
    public static boolean clearFirstPersonModeOverride() {
        FpAnimationController controller = getControllerFor(Minecraft.getInstance().player);
        if (controller == null) return false;
        controller.clearFirstPersonModeOverride();
        return true;
    }

    /**
     * Register a paired 3rd person + FP animation.
     */
    public static void registerPaired(ResourceLocation thirdPersonId, ResourceLocation fpId) {
        FpSyncRegistry.registerPaired(thirdPersonId, fpId);
    }
}