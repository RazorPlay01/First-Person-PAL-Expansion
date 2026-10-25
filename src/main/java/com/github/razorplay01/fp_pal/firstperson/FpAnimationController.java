package com.github.razorplay01.fp_pal.firstperson;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.math.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.11 {
//import net.minecraft.world.entity.Avatar;
//?}
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Separate animation controller for first-person animations.
 * This is independent from the 3rd person animation stack to avoid priority conflicts.
 */
public class FpAnimationController extends PlayerAnimationController {

    // Bone pivot positions for first-person arms (relative to camera/view)
    public static final Map<String, Vec3f> FP_BONE_POSITIONS = Map.of(
            "fp_player_arms", new Vec3f(0, 0, 0),
            "rightArm", new Vec3f(0, 0, 0),
            "leftArm", new Vec3f(0, 0, 0),
            "fp_right_arm", new Vec3f(0, 0, 0),
            "fp_left_arm", new Vec3f(0, 0, 0),
            "fp_right_item", new Vec3f(0, 0, 0),
            "fp_left_item", new Vec3f(0, 0, 0),
            "fp_r_item", new Vec3f(0, 0, 0),
            "fp_l_item", new Vec3f(0, 0, 0),
            "fp_camera", new Vec3f(0, 0, 0)
    );

    private final PlayerAnimManager thirdPersonManager;
    private boolean active = false;
    private ResourceLocation currentFpAnimation = null;
    private boolean pendingPlay = false;
    private boolean stopRequested = false;

    public FpAnimationController(
            //? if >=1.21.11 {
            /*
            Avatar player,
            *///?} else {
            AbstractClientPlayer player,
            //?}
            AnimationStateHandler animationStateHandler, PlayerAnimManager thirdPersonManager) {
        super(player, animationStateHandler);
        this.thirdPersonManager = thirdPersonManager;
    }

    public PlayerAnimManager getThirdPersonManager() {
        return thirdPersonManager;
    }

    @Nullable
    public ResourceLocation getCurrentFpAnimation() {
        return currentFpAnimation;
    }

    /**
     * Sets the FP animation to play. A non-null id also arms the next play request.
     */
    public void setCurrentFpAnimation(@Nullable ResourceLocation id) {
        this.currentFpAnimation = id;
        if (id != null) {
            this.pendingPlay = true;
            this.stopRequested = false;
        }
    }

    public void requestStop() {
        this.stopRequested = true;
        this.currentFpAnimation = null;
    }

    public boolean isPendingPlay() {
        return pendingPlay;
    }

    public void clearPendingPlay() {
        this.pendingPlay = false;
    }

    @Override
    public boolean isActive() {
        // This controller is a high-priority layer of the player's animation stack. It must only
        // contribute while the local player is actually in first person: otherwise the layer would
        // make AnimationStack#isActive() true in third person and PlayerModelMixin would apply this
        // layer's (empty) pose on top of the vanilla / third-person animation, freezing the player.
        return this.active && isLocalFirstPerson();
    }

    /**
     * @return whether this controller is playing an animation, independently of the current camera.
     * Use this for the animation state handler; {@link #isActive()} is the render-layer check and is
     * false while the local player is in third person.
     */
    public boolean isRunning() {
        return this.active;
    }

    /**
     * @return true when this controller belongs to a client-side local player and the camera is in
     * first person. Remote players never contribute FP transforms to the local first-person pass.
     * <p>
     * Checking {@link LocalPlayer} instead of comparing against {@code Minecraft#player} is
     * deliberate: during the client-player construction and render setup the singleton can briefly
     * reference a different instance, which made the FP layer report inactive even though the
     * animation was running and {@code /fp_anim mode} reported {@code PLAYER_ARM}.
     */
    private boolean isLocalFirstPerson() {
        Minecraft minecraft = Minecraft.getInstance();
        return this.player instanceof LocalPlayer && minecraft.options.getCameraType().isFirstPerson();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    public void clearStopRequest() {
        this.stopRequested = false;
    }

    /**
     * Removes a forced first-person mode, going back to the automatic one.
     */
    public void clearFirstPersonModeOverride() {
        this.firstPersonMode = null;
    }

    /**
     * Reports {@link FirstPersonMode#NONE} unless a mode was forced with {@code /fp_anim mode}.
     * <p>
     * {@code NONE} is the only mode that leaves PAL's own first-person machinery completely idle (no
     * camera detach, no first-person render pass, the vanilla first person stays visible), which is
     * exactly what this addon needs because it renders first person itself. PAL is an unmodified
     * dependency here, so the addon cannot add a {@code PLAYER_ARM} constant to PAL's enum;
     * {@link #isPlayerArmMode()} is the addon-side equivalent and is what every FP check uses.
     */
    @Override
    public @NotNull FirstPersonMode getFirstPersonMode() {
        if (this.firstPersonMode != null) return super.getFirstPersonMode();
        return FirstPersonMode.NONE;
    }

    /**
     * @return true while this controller is playing a first-person animation, i.e. while the vanilla
     * arm has to be replaced by the {@code fp_*} rig. This is the addon-side replacement for the
     * {@code FirstPersonMode.PLAYER_ARM} constant the prototype had inside PAL.
     */
    public boolean isPlayerArmMode() {
        return this.active;
    }

    /**
     * Both arms and both held items are shown by default in player-arm mode.
     */
    public static final FirstPersonConfiguration DEFAULT_FP_CONFIGURATION = new FirstPersonConfiguration(true, true, true, true);

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration() {
        if (this.firstPersonConfiguration != null) return super.getFirstPersonConfiguration();
        return DEFAULT_FP_CONFIGURATION;
    }

    /**
     * The FP controller is a normal layer of the player's animation stack, so outside first person
     * it must leave every bone exactly as the lower-priority (third-person) layers or the vanilla
     * model left it. Returning the input bone unchanged is the no-op: resetting it to the initial
     * pose would overwrite the third-person animation and freeze the player in T-pose/idle.
     */
    @Override
    public PlayerAnimBone get3DTransformRaw(@NotNull PlayerAnimBone bone) {
        if (!isLocalFirstPerson()) return bone;
        return super.get3DTransformRaw(bone);
    }

    @Override
    public Vec3f getBonePosition(String name) {
        if (FP_BONE_POSITIONS.containsKey(name)) return FP_BONE_POSITIONS.get(name);
        return super.getBonePosition(name);
    }

    /**
     * Registers every bone of the first-person rig:
     * <ul>
     *     <li>{@code fp_player_arms} - root of the whole rig (arms, items and camera). AzureLib rigs
     *     may instead animate {@code playerArms}, {@code player_arms}, {@code peacemaker},
     *     {@code group} or {@code root}, which {@link FpAnimationSystem} accepts as root aliases;</li>
     *     <li>{@code rightArm} / {@code leftArm} - the arms (PAL's loader also normalizes them to
     *     {@code right_arm} / {@code left_arm});</li>
     *     <li>{@code fp_r_item} / {@code fp_l_item} - the held items of each hand (legacy aliases
     *     {@code fp_right_item} / {@code fp_left_item});</li>
     *     <li>{@code fp_camera} - the real first-person camera (position and roll).</li>
     * </ul>
     * {@code player_arms_import.bbmodel} contains exactly these groups, so anything authored there
     * can be driven by the first-person controller. Any other bone an animation contains is
     * registered too (see {@link #setupNewAnimation()}), which keeps extra AzureLib bones such as
     * {@code mag}, {@code slide} or {@code flash} available for custom detail rendering.
     */
    @Override
    public void registerBones() {
        super.registerBones();
        this.top_bones = new ArrayList<>();
        this.registerPlayerAnimBone("fp_player_arms");
        // AzureLib / player_arms_import.bbmodel bone names. PAL's animation loader normalizes
        // rightArm/leftArm to right_arm/left_arm, so both spellings are registered.
        this.registerTopPlayerAnimBone("right_arm");
        this.registerTopPlayerAnimBone("left_arm");
        this.registerTopPlayerAnimBone("rightArm");
        this.registerTopPlayerAnimBone("leftArm");
        // Legacy PAL names, kept so older first-person animations keep working.
        this.registerTopPlayerAnimBone("fp_right_arm");
        this.registerTopPlayerAnimBone("fp_left_arm");
        this.registerPlayerAnimBone("fp_right_item");
        this.registerPlayerAnimBone("fp_left_item");
        // Short aliases used by animation rigs (fp_r_item / fp_l_item). They are equivalent to
        // fp_right_item / fp_left_item and are resolved by FpAnimationSystem.
        this.registerPlayerAnimBone("fp_r_item");
        this.registerPlayerAnimBone("fp_l_item");
        this.registerPlayerAnimBone("fp_camera");
    }

    /**
     * Registers every bone the incoming first-person animation uses, so AzureLib animations keep
     * their extra bones ({@code mag}, {@code slide}, {@code flash}, the root bone...) instead of
     * having them silently ignored. They are available through
     * {@link #getActiveBoneNames()} / {@link FpRenderState#getExtraBone(String)} for custom detail
     * rendering, and their sound, particle and custom-instruction keyframes still run through PAL's
     * normal keyframe system. PAL only auto-handles sounds; particles and custom instructions require
     * the same listeners (global events or per-controller handlers) as a third-person controller.
     */
    @Override
    protected void setupNewAnimation() {
        if (this.currentAnimation != null) {
            for (String name : this.currentAnimation.animation().boneAnimations().keySet()) {
                if (!this.bones.containsKey(name)) {
                    this.registerPlayerAnimBone(name);
                }
            }
        }
        super.setupNewAnimation();
    }

    /**
     * @return the names of every bone the currently playing first-person animation drives.
     */
    public Set<String> getActiveBoneNames() {
        return Collections.unmodifiableSet(this.activeBones.keySet());
    }

    /**
     * @return only the bones that the current animation actually has keyframes for. This is what
     * {@link FpRenderState#updateExtraBones} uses: a bone with no keyframes must keep its static geo
     * pose instead of being overwritten with an identity transform.
     */
    public Set<String> getAnimatedBoneNames() {
        if (this.currentAnimation == null) return Collections.emptySet();
        return this.currentAnimation.animation().boneAnimations().keySet();
    }
}
