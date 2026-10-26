package com.github.razorplay01.fp_pal.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationController;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationResources;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem;
import com.github.razorplay01.fp_pal.firstperson.FpModelRenderer;
import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import com.github.razorplay01.fp_pal.firstperson.FpSyncRegistry;
import com.zigythebird.playeranim.commands.AnimationArgumentProvider;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Commands for testing first-person animations.
 * Mirrors the functionality of PlayerAnimCommands but for FP animations.
 *
 * Commands:
 * - /fp_anim play {@code <animationID>}  - Play a FP animation (independent of 3rd person)
 * - /fp_anim stop               - Stop the current FP animation
 * - /fp_anim list               - List all loaded FP animations
 * - /fp_anim pairs              - List all registered 3rd person -> FP animation pairs
 * - /fp_anim pair {@code <thirdID> <fpID>} - Register a new animation pair
 * - /fp_anim mode [mode]        - Print or force the first-person mode of the local player
 * - /fp_anim bones              - Print the bones the current FP animation drives
 * - /fp_anim model [id] [tex]   - Override the auto-resolved geo.json model (test aid)
 * - /fp_anim model off          - Disable the model preview
 */
@SuppressWarnings({"unchecked", "unused"})
public class FpPlayerAnimCommands {
    private FpPlayerAnimCommands() {
        /* This utility class should not be instantiated */
    }

    /**
     * Send a message to the player (works with both ClientSuggestionProvider and CommandSourceStack).
     */
    private static void sendMessage(Object source, Component message) {
        if (source instanceof CommandSourceStack cmdSource) {
            cmdSource.sendSuccess(() -> message, false);
        } else if (Minecraft.getInstance().player != null) {
            //? if >=1.21.11 && <26.1.2 {
            
            //Minecraft.getInstance().player.displayClientMessage(message, false);
            //?} else {
            Minecraft.getInstance().player.sendSystemMessage(message);
            //?}
        }
    }

    /**
     * Send an error message to the player.
     */
    private static void sendError(Object source, Component message) {
        if (source instanceof CommandSourceStack cmdSource) {
            cmdSource.sendFailure(message);
        } else if (Minecraft.getInstance().player != null) {
            //? if >=1.21.11 && <26.1.2 {
            //Minecraft.getInstance().player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), false);
            //?} else {
            Minecraft.getInstance().player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
            //?}
        }
    }

    public static <T> void register(CommandDispatcher<T> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register((LiteralArgumentBuilder<T>) Commands.literal("fp_anim")
                .then(Commands.literal("play")
                        .then(Commands.argument("animationID", IdentifierArgument.id())
                                .suggests(new FpAnimationArgumentProvider<>())
                                .executes(FpPlayerAnimCommands::executePlay)
                        )
                )
                .then(Commands.literal("stop")
                        .executes(FpPlayerAnimCommands::executeStop)
                )
                .then(Commands.literal("list")
                        .executes(FpPlayerAnimCommands::executeList)
                )
                .then(Commands.literal("pairs")
                        .executes(FpPlayerAnimCommands::executePairs)
                )
                .then(Commands.literal("pair")
                        .then(Commands.argument("thirdPersonID", IdentifierArgument.id())
                                .suggests(new AnimationArgumentProvider<>())
                                .then(Commands.argument("fpID", IdentifierArgument.id())
                                        .suggests(new FpAnimationArgumentProvider<>())
                                        .executes(FpPlayerAnimCommands::executePair)
                                )
                        )
                )
                .then(Commands.literal("mode")
                        .executes(ctx -> executeMode(ctx, null))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (FirstPersonMode mode : FirstPersonMode.values()) builder.suggest(mode.name());
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeMode(ctx, ctx.getArgument("mode", String.class)))
                        )
                )
                .then(Commands.literal("bones")
                        .executes(FpPlayerAnimCommands::executeBones)
                )
                // "off" is handled by the id argument (minecraft:off) instead of a sibling literal:
                // Brigadier would otherwise report an ambiguity between the literal and the id argument.
                .then(Commands.literal("model")
                        .executes(FpPlayerAnimCommands::executeModelInfo)
                        .then(Commands.argument("model", IdentifierArgument.id())
                                .executes(ctx -> executeModel(ctx, null))
                                .then(Commands.argument("texture", IdentifierArgument.id())
                                        .executes(ctx -> executeModel(ctx,
                                                IdentifierArgument.getId(ctx, "texture")))
                                )
                        )
                )
        );
    }

    /**
     * Play a FP animation by ID.
     */
    private static int executePlay(CommandContext<?> context) {
        Identifier animationId = context.getArgument("animationID", Identifier.class);
        Object source = context.getSource();

        if (!FpAnimationResources.hasAnimation(animationId)) {
            sendError(source, Component.literal("FP animation not found: " + animationId));
            return 0;
        }

        if (Minecraft.getInstance().player == null) {
            sendError(source, Component.literal("Player is null!"));
            return 0;
        }

        boolean success = FpAnimationSystem.playFpAnimation(Minecraft.getInstance().player, animationId);
        if (success) {
            sendMessage(source, Component.literal("Playing FP animation: " + animationId)
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        } else {
            sendError(source, Component.literal("Failed to play FP animation: " + animationId));
            return 0;
        }
    }

    /**
     * Stop the currently playing FP animation.
     */
    private static int executeStop(CommandContext<?> context) {
        Object source = context.getSource();

        if (Minecraft.getInstance().player == null) {
            sendError(source, Component.literal("Player is null!"));
            return 0;
        }

        FpAnimationSystem.stopFpAnimation();
        sendMessage(source, Component.literal("FP animation stopped")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    /**
     * List all loaded FP animations.
     */
    private static int executeList(CommandContext<?> context) {
        Object source = context.getSource();
        Map<Identifier, com.zigythebird.playeranimcore.animation.Animation> animations = FpAnimationResources.getAnimations();

        if (animations.isEmpty()) {
            sendMessage(source, Component.literal("No FP animations loaded.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        sendMessage(source, Component.literal("=== Loaded FP Animations (" + animations.size() + ") ===")
                .withStyle(ChatFormatting.GOLD));

        for (Identifier id : animations.keySet()) {
            sendMessage(source, Component.literal("  - " + id));
        }

        return 1;
    }

    /**
     * List all registered 3rd person -> FP animation pairs.
     */
    private static int executePairs(CommandContext<?> context) {
        Object source = context.getSource();
        Map<Identifier, Identifier> pairs = FpSyncRegistry.getAllPairs();

        if (pairs.isEmpty()) {
            sendMessage(source, Component.literal("No animation pairs registered.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        sendMessage(source, Component.literal("=== Animation Pairs (" + pairs.size() + ") ===")
                .withStyle(ChatFormatting.GOLD));

        for (Map.Entry<Identifier, Identifier> entry : pairs.entrySet()) {
            sendMessage(source, Component.literal("  " + entry.getKey() + " -> " + entry.getValue()));
        }

        return 1;
    }

    /**
     * Register a new animation pair.
     */
    private static int executePair(CommandContext<?> context) {
        Identifier thirdPersonId = context.getArgument("thirdPersonID", Identifier.class);
        Identifier fpId = context.getArgument("fpID", Identifier.class);
        Object source = context.getSource();

        FpAnimationSystem.registerPaired(thirdPersonId, fpId);
        sendMessage(source, Component.literal("Registered pair: " + thirdPersonId + " -> " + fpId)
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * Print or force the first-person mode of the local player's FP controller.
     * AUTO removes any forced mode; the other values are PAL's own first-person modes.
     */
    private static int executeMode(CommandContext<?> context, @Nullable String modeName) {
        Object source = context.getSource();

        if (modeName == null) {
            FirstPersonMode current = FpAnimationSystem.getFirstPersonMode();
            if (current == null) {
                sendError(source, Component.literal("No FP controller for the local player!"));
                return 0;
            }
            sendMessage(source, Component.literal("Current first-person mode: " + current)
                    .withStyle(ChatFormatting.AQUA));
            return 1;
        }

        if (modeName.equalsIgnoreCase("auto")) {
            if (!FpAnimationSystem.clearFirstPersonModeOverride()) {
                sendError(source, Component.literal("No FP controller for the local player!"));
                return 0;
            }
            sendMessage(source, Component.literal("First-person mode is now automatic")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        }

        FirstPersonMode mode;
        try {
            mode = FirstPersonMode.valueOf(modeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sendError(source, Component.literal("Unknown first-person mode: " + modeName));
            return 0;
        }

        if (!FpAnimationSystem.setFirstPersonMode(mode)) {
            sendError(source, Component.literal("No FP controller for the local player!"));
            return 0;
        }

        sendMessage(source, Component.literal("First-person mode forced to " + mode)
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * Prints the currently selected preview model.
     */
    private static int executeModelInfo(CommandContext<?> context) {
        Object source = context.getSource();
        Identifier modelId = FpModelRenderer.modelId();
        if (modelId == null) {
            sendMessage(source, Component.literal("No FP preview model selected. Usage: /fp_anim model <namespace:path> [texture] or /fp_anim model off")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        sendMessage(source, Component.literal("FP preview model: " + modelId
                        + " (" + FpModelRenderer.boneCount() + " bones, texture "
                        + FpModelRenderer.texture() + ")")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * Clears the preview model.
     */
    private static int executeModelOff(CommandContext<?> context) {
        FpModelRenderer.setAutoResolve(false);
        FpModelRenderer.clear();
        sendMessage(context.getSource(), Component.literal("FP preview model disabled (auto-resolution off)")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    /**
     * Loads a geo.json for the first-person preview. The texture defaults to
     * {@code <namespace>:textures/fp/<path>.png}; a second argument overrides it.
     */
    private static int executeModel(CommandContext<?> context, @Nullable Identifier texture) {
        Object source = context.getSource();
        Identifier modelId = context.getArgument("model", Identifier.class);

        // /fp_anim model off is parsed as the id minecraft:off. Handling it here instead of as a
        // sibling literal avoids Brigadier's "ambiguous command" warning with the id argument.
        if (modelId.getNamespace().equals("minecraft")
                && (modelId.getPath().equals("off") || modelId.getPath().equals("none") || modelId.getPath().equals("clear"))) {
            return executeModelOff(context);
        }

        boolean loaded = texture == null
                ? FpModelRenderer.load(modelId)
                : FpModelRenderer.load(modelId, texture);

        if (!loaded) {
            sendError(source, Component.literal("Failed to load FP model " + modelId
                    + (FpModelRenderer.lastError() != null ? ": " + FpModelRenderer.lastError() : "")));
            return 0;
        }

        sendMessage(source, Component.literal("Loaded FP model " + modelId
                        + " (" + FpModelRenderer.boneCount() + " bones, texture "
                        + FpModelRenderer.texture() + ")")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * Prints the bones the current FP animation drives and the transforms of the extra (non-core)
     * bones, so rigs that use bones such as {@code mag}, {@code slide} or {@code flash} can be verified
     * in game.
     */
    private static int executeBones(CommandContext<?> context) {
        Object source = context.getSource();
        var player = Minecraft.getInstance().player;
        if (player == null) {
            sendError(source, Component.literal("Player is null!"));
            return 0;
        }

        if (!(player instanceof IAnimatedAvatar animated)) {
            sendError(source, Component.literal("Player is not animated by PAL!"));
            return 0;
        }
        if (!(player instanceof IFpAnimatedPlayer fpPlayer)) {
            sendError(source, Component.literal("Player has no first-person controller!"));
            return 0;
        }

        FpAnimationController controller = fpPlayer.fpPal$getFpAnimationController();
        if (controller == null) {
            sendError(source, Component.literal("No FP controller for the local player!"));
            return 0;
        }

        // Refresh from the live manager so the command always prints current data.
        FpRenderState.updateExtraBones(animated.playerAnimLib$getAnimManager(), controller.getAnimatedBoneNames());

        sendMessage(source, Component.literal("Active FP bones: " + controller.getActiveBoneNames())
                .withStyle(ChatFormatting.AQUA));
        sendMessage(source, Component.literal("Animated FP bones: " + controller.getAnimatedBoneNames())
                .withStyle(ChatFormatting.AQUA));

        Map<String, PlayerAnimBone> extraBones = FpRenderState.getExtraBones();
        if (extraBones.isEmpty()) {
            sendMessage(source, Component.literal("No extra bones are animated by this FP animation.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        for (Map.Entry<String, PlayerAnimBone> entry : extraBones.entrySet()) {
            PlayerAnimBone bone = entry.getValue();
            //? if >=26.1.2 {
            sendMessage(source, Component.literal(String.format(Locale.ROOT,
                    "  %s pos=(%.3f, %.3f, %.3f) rot=(%.1f, %.1f, %.1f) scale=(%.2f, %.2f, %.2f)",
                    entry.getKey(),
                    bone.position.x, bone.position.y, bone.position.z,
                    Math.toDegrees(bone.rotation.x), Math.toDegrees(bone.rotation.y), Math.toDegrees(bone.rotation.z),
                    bone.scale.x, bone.scale.y, bone.scale.z)));
            //?} else {
            /*sendMessage(source, Component.literal(String.format(Locale.ROOT,
                    "  %s pos=(%.3f, %.3f, %.3f) rot=(%.1f, %.1f, %.1f) scale=(%.2f, %.2f, %.2f)",
                    entry.getKey(),
                    bone.position.x, bone.position.y, bone.position.z,
                    Math.toDegrees(bone.rotation.x), Math.toDegrees(bone.rotation.y), Math.toDegrees(bone.rotation.z),
                    bone.scale.x, bone.scale.y, bone.scale.z)));
            *///?}
        }
        return 1;
    }

}
