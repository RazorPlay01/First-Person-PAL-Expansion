package com.github.razorplay01.fp_pal.firstperson;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for synchronizing 3rd person animations with first-person animations.
 * Maps 3rd person animation IDs to their FP counterparts.
 * Example: "player_animation_library:reload" -> "player_animation_library:fp_reload"
 */
public class FpSyncRegistry {
    private FpSyncRegistry() {
        /* This utility class should not be instantiated */
    }

    /**
     * Direct mapping from 3rd person animation to FP animation.
     */
    private static final Map<Identifier, Identifier> THIRD_TO_FP = new HashMap<>();

    /**
     * Reverse mapping from FP animation to 3rd person animation.
     */
    private static final Map<Identifier, Identifier> FP_TO_THIRD = new HashMap<>();

    /**
     * Register a paired 3rd person + FP animation.
     */
    public static void registerPaired(Identifier thirdPersonId, Identifier fpId) {
        THIRD_TO_FP.put(thirdPersonId, fpId);
        FP_TO_THIRD.put(fpId, thirdPersonId);
    }

    /**
     * Get the FP animation ID for a given 3rd person animation ID.
     * @return the FP animation ID, or null if no pairing exists.
     */
    @Nullable
    public static Identifier getFpAnimation(Identifier thirdPersonId) {
        return THIRD_TO_FP.get(thirdPersonId);
    }

    /**
     * Get the 3rd person animation ID for a given FP animation ID.
     * @return the 3rd person animation ID, or null if no pairing exists.
     */
    @Nullable
    public static Identifier getThirdPersonAnimation(Identifier fpId) {
        return FP_TO_THIRD.get(fpId);
    }

    /**
     * Check if a 3rd person animation has a paired FP animation.
     */
    public static boolean hasFpPair(Identifier thirdPersonId) {
        Identifier fpId = THIRD_TO_FP.get(thirdPersonId);
        if (fpId == null) return false;
        return FpAnimationResources.hasAnimation(fpId);
    }

    /**
     * @return all registered pairings.
     */
    public static Map<Identifier, Identifier> getAllPairs() {
        return Collections.unmodifiableMap(THIRD_TO_FP);
    }
}
