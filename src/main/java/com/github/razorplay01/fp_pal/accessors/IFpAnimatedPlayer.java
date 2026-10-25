package com.github.razorplay01.fp_pal.accessors;

import com.github.razorplay01.fp_pal.firstperson.FpAnimationController;
import org.jetbrains.annotations.Nullable;

/**
 * Accessor for the first-person animation controller of a player.
 * <p>
 * The controller lives in this addon, so PAL's {@code IAnimatedAvatar} cannot expose it. The addon
 * mixes this interface into {@code AbstractClientPlayer} itself, which keeps PAL untouched.
 */
public interface IFpAnimatedPlayer {
    /**
     * @return the first-person controller of this player, or {@code null} while the animation stack
     * has not been built yet.
     */
    @Nullable
    FpAnimationController fpPal$getFpAnimationController();

    /**
     * Called by the FP animation factory when it creates the controller for this player.
     */
    void fpPal$setFpAnimationController(@Nullable FpAnimationController controller);
}
