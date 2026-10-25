package com.github.razorplay01.fp_pal.firstperson;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.math.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
//? if >=1.21.11 {
import net.minecraft.world.entity.Avatar;
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

    private final AvatarAnimManager thirdPersonManager;
    private boolean active = false;
    private Identifier currentFpAnimation = null;
    private boolean pendingPlay = false;
    private boolean stopRequested = false;

    public FpAnimationController(
            //? if >=1.21.11 {
            
            Avatar player,
            //?} else {
            //AbstractClientPlayer player,
            //?}
            AnimationStateHandler animationStateHandler, AvatarAnimManager thirdPersonManager) {
        super(player, animationStateHandler);
        this.thirdPersonManager = thirdPersonManager;
    }

    public AvatarAnimManager getThirdPersonManager() {
        return thirdPersonManager;
    }

    @Nullable
    public Identifier getCurrentFpAnimation() {
        return currentFpAnimation;
    }

    /**
     * Sets the FP animation to play. A non-null id also arms the next play request.
     */
    public void setCurrentFpAnimation(@Nullable Identifier id) {
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
        // Only contribute in first person: otherwise this layer would make the animation stack active
        // in third person and its empty pose would freeze the player.
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
     * first person. Checking {@link LocalPlayer} instead of {@code Minecraft#player} is deliberate:
     * the singleton can briefly reference a different instance during player construction.
     */
    private boolean isLocalFirstPerson() {
        Minecraft minecraft = Minecraft.getInstance();
        return this.avatar instanceof LocalPlayer && minecraft.options.getCameraType().isFirstPerson();
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
     * Reports {@link FirstPersonMode#NONE} unless a mode was forced with {@code /fp_anim mode}. NONE is
     * the only mode that leaves PAL's own first-person machinery idle, which this addon needs because
     * it renders first person itself.
     */
    @Override
    public @NotNull FirstPersonMode getFirstPersonMode() {
        if (this.firstPersonMode != null) return super.getFirstPersonMode();
        return FirstPersonMode.NONE;
    }

    /**
     * @return true while this controller is playing a first-person animation, i.e. while the vanilla
     * arm has to be replaced by the {@code fp_*} rig.
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
     * Outside first person this layer must leave every bone untouched (returning the input bone is the
     * no-op); resetting it would overwrite the third-person animation.
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
     * Registers every bone of the first-person rig: {@code fp_player_arms} (root), the arms, the held
     * items and {@code fp_camera}. PAL's loader normalizes {@code rightArm}/{@code leftArm} to
     * {@code right_arm}/{@code left_arm}, so both spellings are registered. Any other bone is
     * registered too (see {@link #setupNewAnimation()}).
     */
    @Override
    public void registerBones() {
        super.registerBones();
        this.top_bones = new ArrayList<>();
        this.registerPlayerAnimBone("fp_player_arms");
        // PAL's loader normalizes rightArm/leftArm to right_arm/left_arm, so both are registered.
        this.registerTopPlayerAnimBone("right_arm");
        this.registerTopPlayerAnimBone("left_arm");
        this.registerTopPlayerAnimBone("rightArm");
        this.registerTopPlayerAnimBone("leftArm");
        // Legacy PAL names, kept so older first-person animations keep working.
        this.registerTopPlayerAnimBone("fp_right_arm");
        this.registerTopPlayerAnimBone("fp_left_arm");
        this.registerPlayerAnimBone("fp_right_item");
        this.registerPlayerAnimBone("fp_left_item");
        // Short aliases (fp_r_item / fp_l_item), resolved by FpAnimationSystem.
        this.registerPlayerAnimBone("fp_r_item");
        this.registerPlayerAnimBone("fp_l_item");
        this.registerPlayerAnimBone("fp_camera");
    }

    /**
     * Registers every bone the incoming first-person animation uses, so AzureLib extra bones
     * ({@code mag}, {@code slide}, {@code flash}...) are kept instead of being ignored. They are
     * available through {@link #getActiveBoneNames()} / {@link FpRenderState#getExtraBone(String)},
     * and their sound, particle and custom-instruction keyframes run through PAL's normal keyframe
     * system.
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
     * @return only the bones that the current animation actually has keyframes for. {@link
     * FpRenderState#updateExtraBones} uses this: a bone with no keyframes must keep its static geo pose
     * instead of being overwritten with an identity transform.
     */
    public Set<String> getAnimatedBoneNames() {
        if (this.currentAnimation == null) return Collections.emptySet();
        return this.currentAnimation.animation().boneAnimations().keySet();
    }
}
