package com.github.razorplay01.fp_pal.firstperson;

import com.github.razorplay01.fp_pal.ModTemplate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.joml.Matrix4f;

/**
 * TEMPORARY diagnostics for the first-person render parity work.
 * <p>
 * It is off unless the game is started with {@code -Dfp_pal.debug=true} or with the
 * {@code FP_PAL_DEBUG} environment variable set, so a normal run pays nothing but a boolean check.
 * Every line is written through the mod logger, which means a run can be diagnosed from
 * {@code versions/<version>/run/logs/latest.log} without looking at the screen.
 * <p>
 * This class is meant to be deleted once the 1.21.1 / 1.21.11 parity is closed.
 */
public final class FpDebug {

    private static final boolean ENABLED =
            Boolean.getBoolean("fp_pal.debug") || System.getenv("FP_PAL_DEBUG") != null;

    /**
     * Every Nth frame is logged, so a long run does not flood the log.
     */
    private static final int PERIOD = 40;

    private static int frames;
    private static boolean logThisFrame;

    private FpDebug() {
        /* This utility class should not be instantiated */
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Starts a new frame. Returns true when this frame is one of the sampled ones.
     */
    public static boolean beginFrame() {
        if (!ENABLED) {
            return false;
        }
        logThisFrame = frames++ % PERIOD == 0;
        return logThisFrame;
    }

    public static boolean logThisFrame() {
        return ENABLED && logThisFrame;
    }

    public static void log(String message) {
        if (ENABLED) {
            ModTemplate.LOGGER.info("[FP] {}", message);
        }
    }

    /**
     * Compact, diffable description of a bone: name, position, rotation and scale in the raw
     * Blockbench units PAL stores.
     */
    public static String bone(PlayerAnimBone bone) {
        if (bone == null) {
            return "null";
        }
        return String.format("%s p(%.2f,%.2f,%.2f) r(%.2f,%.2f,%.2f) s(%.2f,%.2f,%.2f)",
                bone.getName(),
                bone.getPosX(), bone.getPosY(), bone.getPosZ(),
                bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
    }

    /**
     * Translation, rotation basis and scale of a matrix, so two runs can be compared by eye.
     */
    public static String mat(Matrix4f m) {
        return String.format("t(%.4f,%.4f,%.4f) x(%.3f,%.3f,%.3f) y(%.3f,%.3f,%.3f) z(%.3f,%.3f,%.3f)",
                m.m30(), m.m31(), m.m32(),
                m.m00(), m.m01(), m.m02(),
                m.m10(), m.m11(), m.m12(),
                m.m20(), m.m21(), m.m22());
    }

    public static String mat(PoseStack poseStack) {
        return mat(poseStack.last().pose());
    }
}
