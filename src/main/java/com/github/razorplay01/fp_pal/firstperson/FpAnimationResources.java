package com.github.razorplay01.fp_pal.firstperson;

import com.github.razorplay01.fp_pal.ModTemplate;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Cache class for holding loaded {@link Animation Animations} for first-person rendering.
 * Loads from assets/modid/fp_animations/*.json
 */
public class FpAnimationResources implements ResourceManagerReloadListener {
    public static final ResourceLocation KEY = ModTemplate.id("fp_animation");
    private static final Map<ResourceLocation, Animation> ANIMATIONS = new Object2ObjectOpenHashMap<>();

    /**
     * Model id ({@code namespace:path} inside {@code fp_models/}) inferred from the file each
     * animation was loaded from. A file such as {@code fp_animations/peacemaker.animation.json}
     * can contain several clips ({@code firing}, {@code aim}, {@code reload}...), all of which use
     * the same {@code peacemaker.geo.json} model and {@code textures/fp/peacemaker.png} texture.
     */
    private static final Map<ResourceLocation, ResourceLocation> SOURCE_MODELS = new Object2ObjectOpenHashMap<>();

    /**
     * Get an animation from the registry.
     */
    public static @Nullable Animation getAnimation(ResourceLocation id) {
        return ANIMATIONS.get(id);
    }

    /**
     * Get Optional animation from registry.
     */
    @NotNull
    public static Optional<Animation> getAnimationOptional(@NotNull ResourceLocation identifier) {
        return Optional.ofNullable(getAnimation(identifier));
    }

    /**
     * @return an unmodifiable map of all the animations.
     */
    public static Map<ResourceLocation, Animation> getAnimations() {
        return Collections.unmodifiableMap(ANIMATIONS);
    }

    /**
     * Returns the animations of a specific mod/namespace.
     */
    @NotNull
    public static Map<String, Animation> getModAnimations(@NotNull String modid) {
        HashMap<String, Animation> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, Animation> entry : ANIMATIONS.entrySet()) {
            if (entry.getKey().getNamespace().equals(modid)) {
                map.put(entry.getKey().getPath(), entry.getValue());
            }
        }
        return map;
    }

    /**
     * @return Returns true if that animation is available.
     */
    public static boolean hasAnimation(ResourceLocation id) {
        return ANIMATIONS.containsKey(id);
    }

    /**
     * @return the model id inferred from the {@code .animation.json} file that declared this
     * animation, or {@code null} if the animation is unknown.
     * <p>
     * For example, {@code player_animation_library:firing} declared inside
     * {@code fp_animations/peacemaker.animation.json} returns
     * {@code player_animation_library:peacemaker}, which maps to
     * {@code fp_models/peacemaker.geo.json} and {@code textures/fp/peacemaker.png}.
     */
    public static @Nullable ResourceLocation getModelId(ResourceLocation animationId) {
        return SOURCE_MODELS.get(animationId);
    }

    /**
     * Load animations using ResourceManager from fp_animations/ folder.
     */
    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        ANIMATIONS.clear();
        SOURCE_MODELS.clear();

        for (var resource : manager.listResources("fp_animations", resourceLocation -> resourceLocation.getPath().endsWith(".json")).entrySet()) {
            String namespace = resource.getKey().getNamespace();
            ResourceLocation sourceModel = ResourceLocation.fromNamespaceAndPath(namespace,
                    modelPath(resource.getKey().getPath()));
            try (InputStream is = resource.getValue().open()) {
                for (var entry : UniversalAnimLoader.loadAnimations(is).entrySet()) {
                    ResourceLocation animationId = ResourceLocation.fromNamespaceAndPath(namespace, entry.getKey());
                    ANIMATIONS.put(animationId, entry.getValue());
                    SOURCE_MODELS.put(animationId, sourceModel);
                }
            } catch (Exception e) {
                ModTemplate.LOGGER.error("Failed to load FP animation {} because:", resource.getKey(), e);
            }
        }
    }

    /**
     * Converts {@code fp_animations/guns/peacemaker.animation.json} into {@code guns/peacemaker}.
     * The result is used as the model id ({@code fp_models/guns/peacemaker.geo.json}) and as the
     * texture id ({@code textures/fp/guns/peacemaker.png}).
     */
    private static String modelPath(String resourcePath) {
        String path = resourcePath;
        String prefix = "fp_animations/";
        if (path.startsWith(prefix)) path = path.substring(prefix.length());
        if (path.endsWith(".animation.json")) {
            path = path.substring(0, path.length() - ".animation.json".length());
        } else if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return path;
    }

    //? if <1.21.11 {
    @Override
    public @NotNull CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier preparationBarrier, ResourceManager resourceManager, ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.runAsync(() -> onResourceManagerReload(resourceManager), backgroundExecutor).thenCompose(preparationBarrier::wait);
    }
    //?}
}