package com.github.razorplay01.fp_pal.firstperson;

import com.github.razorplay01.fp_pal.ModTemplate;
import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
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
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Central system for first-person animations.
 * Handles registration, ticking, and synchronization of FP animations.
 */
public class FpAnimationSystem {

    public static final Identifier FP_ANIMATION_LAYER_ID = ModTemplate.id("fp_factory");
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
                        AvatarAnimManager thirdPersonManager = ((IAnimatedAvatar) player).playerAnimLib$getAnimManager();
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

                                // Keep playing while the animation is still running (isRunning(), not
                                // isActive(), which is false in third person by design), so it is not
                                // stopped one tick after it starts.
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
                        // Hand the controller to the player so the render mixins can reach it.
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
        // FpRenderState is a process-wide cache for the local renderer; remote players tick the same
        // mixin and must not wipe it.
        if (!(player instanceof LocalPlayer localPlayer)) return;

        if (!(player instanceof IAnimatedAvatar animatedPlayer)) {
            FpRenderState.reset();
            return;
        }

        AvatarAnimManager manager = animatedPlayer.playerAnimLib$getAnimManager();

        // Only this addon's controller renders the fp_* arms; PAL's modes leave first person to vanilla.
        if (!FpRenderState.isPlayerArmMode(player)) {
            FpRenderState.reset();
            return;
        }

        FpRenderState.isFpActive = true;

        FpAnimationController fpController = getControllerFor(player);
        Set<String> animatedBoneNames = fpController != null
                ? fpController.getAnimatedBoneNames()
                : Set.of();

        // Save the previous camera transform so fp_camera motion can be interpolated between ticks.
        FpRenderState.beginCameraTick();

        // Full transforms are copied (position, rotation and scale), and arm/item presence is resolved
        // from the animation's bone list, so a clip that animates only one hand skips the other.
        FpRenderState.copy(resolveRootBone(manager), FpRenderState.fpPlayerArms);
        // PAL normalizes CamelCase bone names to snake_case, so rightArm/leftArm arrive as right_arm/left_arm.
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

        // Keep every other animated bone available so mods can render extra details.
        if (fpController != null) {
            FpRenderState.updateExtraBones(manager, animatedBoneNames);

            // The display transform can travel with the animation, so every rig keeps its own values.
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
     * {@code fp_player_arms}; the first animated one is used as the rig root.
     */
    private static final String[] AZURELIB_ROOT_BONES = {"player_arms", "playerArms", "peacemaker", "group", "root"};

    /**
     * Stored pivot of the {@code peacemaker} root bone (geo pivot {@code [-0.03125, 1.425, -1.2375]},
     * with AzureLib's X negation applied).
     */
    private static final float[] PEACEMAKER_ROOT_PIVOT = new float[]{0.03125F, 1.425F, -1.2375F};

    private static PlayerAnimBone resolveRootBone(AvatarAnimManager manager) {
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
     * Copies an arm bone only when the animation actually contains it (a zero transform is still
     * animated); a missing arm stays hidden.
     *
     * @return true if the animation contains any of the accepted names
     */
    private static boolean copyArmTransform(AvatarAnimManager manager, Set<String> animatedBoneNames,
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
     * Resolves the short and legacy item bone names, checking the animation's bone list.
     *
     * @return true if the animation contains either name
     */
    private static boolean copyItemTransform(AvatarAnimManager manager, Set<String> animatedBoneNames,
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
    public static boolean playFpAnimation(AbstractClientPlayer player, Identifier fpAnimationId) {
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

        // A matching item model is optional and resolved automatically; the preview is global, so only
        // resolve it for the local player.
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
     * Prefers the per-player captured controller (from IAnimatedAvatar), falls back to the static one.
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
    public static void registerPaired(Identifier thirdPersonId, Identifier fpId) {
        FpSyncRegistry.registerPaired(thirdPersonId, fpId);
    }
}
