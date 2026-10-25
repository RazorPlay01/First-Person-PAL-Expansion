package com.github.razorplay01.fp_pal.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.github.razorplay01.fp_pal.ModTemplate;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?}
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Reader;

/**
 * Renders a Bedrock geo.json model in PAL's first-person arm pass, using the same bone transforms the
 * FP animation produces ({@link FpRenderState#getExtraBone(String)}). The root bone is already applied
 * by {@link FpArmRenderUtil#applyRootTransform}, arm bones are skipped (PAL draws the vanilla arms on
 * them) and every other bone is rendered with its animated transform plus the static geo rotation.
 * <p>
 * The model is resolved automatically when a first-person animation is played (see
 * {@link #loadForAnimation(Identifier)}), and only when both the {@code .geo.json} and the matching
 * texture exist. {@code /fp_anim model} remains as an explicit override.
 */
public final class FpModelRenderer {

    private static @Nullable FpGeoModel model;
    private static @Nullable Identifier modelId;
    private static @Nullable Identifier texture;
    private static @Nullable String lastError;

    /**
     * Whether {@link #loadForAnimation(Identifier)} may select a model automatically.
     * Enabled by default; {@code /fp_anim model off} turns it off until a model is loaded manually.
     */
    private static boolean autoResolve = true;

    private static final Vector3f VERTEX_SCRATCH = new Vector3f();
    private static final Vector3f NORMAL_SCRATCH = new Vector3f();

    private FpModelRenderer() {
    }

    /**
     * Loads {@code assets/<namespace>/fp_models/<path>.geo.json} and points the renderer at
     * {@code assets/<namespace>/textures/fp/<path>.png}.
     *
     * @param id model id, for example {@code player_animation_library:peacemaker}
     * @return true when the model was parsed
     */
    public static boolean load(Identifier id) {
        return load(id, defaultTexture(id));
    }

    /**
     * Loads a model with an explicit texture.
     */
    public static synchronized boolean load(Identifier id, Identifier textureLocation) {
        Identifier geoId = geoLocation(id);
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(geoId);
            if (resource.isEmpty()) {
                lastError = "Model not found: " + geoId;
                return false;
            }
            try (Reader reader = resource.get().openAsReader()) {
                FpGeoModel parsed = FpGeoModel.parse(reader);
                model = parsed;
                modelId = id;
                texture = textureLocation;
                autoResolve = true;
                lastError = null;
                return true;
            }
        } catch (Exception e) {
            lastError = "Failed to load " + geoId + ": " + e.getMessage();
            ModTemplate.LOGGER.error("Failed to load first-person model {}", geoId, e);
            return false;
        }
    }

    /**
     * Stops rendering the preview model.
     */
    public static synchronized void clear() {
        model = null;
        modelId = null;
        texture = null;
        lastError = null;
    }

    /**
     * Resolves and loads the item model that belongs to a first-person animation: the animation id
     * itself first, then the {@code .animation.json} file it was declared in. When neither has a model
     * the preview is cleared, so a previous animation's model is never shown.
     *
     * @return true when a model was loaded
     */
    public static synchronized boolean loadForAnimation(Identifier animationId) {
        if (!autoResolve) return false;

        if (tryLoad(animationId)) {
            ModTemplate.LOGGER.info("Using FP model {} for animation {}", modelId, animationId);
            return true;
        }

        Identifier sourceModel = FpAnimationResources.getModelId(animationId);
        if (sourceModel != null && !sourceModel.equals(animationId) && tryLoad(sourceModel)) {
            ModTemplate.LOGGER.info("Using FP model {} for animation {}", modelId, animationId);
            return true;
        }

        clear();
        return false;
    }

    /**
     * Loads a model only when both the geo file and its default texture are present. This keeps the
     * automatic path silent for animations that do not have an item model.
     */
    private static boolean tryLoad(Identifier id) {
        var resources = Minecraft.getInstance().getResourceManager();
        if (resources.getResource(geoLocation(id)).isEmpty()) return false;
        if (resources.getResource(defaultTexture(id)).isEmpty()) return false;
        return load(id);
    }

    /**
     * Enables or disables automatic model selection for future FP animations. Loading a model
     * explicitly with {@link #load(Identifier)} re-enables it.
     */
    public static synchronized void setAutoResolve(boolean value) {
        autoResolve = value;
    }

    public static synchronized boolean isAutoResolve() {
        return autoResolve;
    }

    public static @Nullable Identifier modelId() {
        return modelId;
    }

    public static @Nullable Identifier texture() {
        return texture;
    }

    public static @Nullable String lastError() {
        return lastError;
    }

    public static int boneCount() {
        return model == null ? 0 : model.boneCount();
    }

    /**
     * @return the resource id a model id maps to ({@code fp_models/<path>.geo.json})
     */
    public static Identifier geoLocation(Identifier id) {
        String path = id.getPath();
        if (path.endsWith(".geo.json")) {
            path = path.substring(0, path.length() - ".geo.json".length());
        } else if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "fp_models/" + path + ".geo.json");
    }

    private static Identifier defaultTexture(Identifier id) {
        String path = id.getPath();
        if (path.endsWith(".geo.json")) {
            path = path.substring(0, path.length() - ".geo.json".length());
        } else if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/fp/" + path + ".png");
    }

    /**
     * Renders the selected model in the current pose. The pose must already be the model frame
     * (hand anchor + display transform + root bone), exactly like the arm pass.
     */
    public static void render(PoseStack poseStack, FpRenderBuffer render, int packedLight) {
        FpGeoModel currentModel = model;
        Identifier currentTexture = texture;
        if (currentModel == null || currentTexture == null) return;

        //? if >=26.1.2 {
        /*// 26.1.2 renamed entityCutoutNoCull to entityCutout, so the plain variant is the "no cull" one.
        RenderType renderType = RenderTypes.entityCutout(currentTexture);
        *///?} else {
        RenderType renderType = RenderTypes.entityCutoutNoCull(currentTexture);
        //?}
        // The whole model is submitted as one geometry batch; FpRenderBuffer hides the pipeline difference.
        render.drawGeometry(poseStack, renderType, (pose, consumer) -> {
            for (FpGeoModel.Bone root : currentModel.rootBones()) {
                renderBone(pose, consumer, currentModel, root, packedLight, true);
            }
        });
    }

    private static void renderBone(PoseStack poseStack, VertexConsumer consumer, FpGeoModel geoModel,
                                   FpGeoModel.Bone bone, int packedLight, boolean root) {
        poseStack.pushPose();

        boolean visible = bone.visible() && !isArmBone(bone.name());

        // The root transform is already applied by applyRootTransform; children add their transform on top.
        if (!root) {
            PlayerAnimBone transform = bone.animatedTransform();
            FpArmRenderUtil.applyBoneTransform(poseStack, transform, new float[]{
                    bone.pivotX(), bone.pivotY(), bone.pivotZ()
            });
            // A zero scale is how flash bones hide themselves; collapsed quads are not worth drawing.
            visible = visible
                    && Math.abs(transform.getScaleX()) > 1.0E-5F
                    && Math.abs(transform.getScaleY()) > 1.0E-5F
                    && Math.abs(transform.getScaleZ()) > 1.0E-5F;
        }

        // The modeled arm cubes are not rendered: PAL draws the vanilla arm mesh there instead.
        if (visible) {
            for (FpGeoModel.Cube cube : bone.cubes()) {
                renderCube(poseStack, consumer, geoModel, cube, packedLight);
            }
        }

        for (FpGeoModel.Bone child : bone.children()) {
            renderBone(poseStack, consumer, geoModel, child, packedLight, false);
        }

        poseStack.popPose();
    }

    private static boolean isArmBone(String name) {
        return FpArmRenderUtil.RIGHT_ARM_BONE.equals(name) || FpArmRenderUtil.LEFT_ARM_BONE.equals(name);
    }

    private static void renderCube(PoseStack poseStack, VertexConsumer consumer, FpGeoModel geoModel,
                                   FpGeoModel.Cube cube, int packedLight) {
        if (Math.abs(cube.sizeX()) < 1.0E-5F || Math.abs(cube.sizeY()) < 1.0E-5F
                || Math.abs(cube.sizeZ()) < 1.0E-5F) {
            return;
        }

        float inflate = cube.inflate() / 16.0F;
        float x0 = cube.x0() / 16.0F - inflate;
        float y0 = cube.y0() / 16.0F - inflate;
        float z0 = cube.z0() / 16.0F - inflate;
        float x1 = (cube.x0() + cube.sizeX()) / 16.0F + inflate;
        float y1 = (cube.y0() + cube.sizeY()) / 16.0F + inflate;
        float z1 = (cube.z0() + cube.sizeZ()) / 16.0F + inflate;

        Matrix4f cubePose = new Matrix4f()
                .translate(cube.pivotX() / 16.0F, cube.pivotY() / 16.0F, cube.pivotZ() / 16.0F)
                .rotateZ(cube.rotationZ())
                .rotateY(cube.rotationY())
                .rotateX(cube.rotationX())
                .translate(-cube.pivotX() / 16.0F, -cube.pivotY() / 16.0F, -cube.pivotZ() / 16.0F);
        Matrix3f cubeNormal = new Matrix3f()
                .rotateZ(cube.rotationZ())
                .rotateY(cube.rotationY())
                .rotateX(cube.rotationX());

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = new Matrix3f(poseStack.last().normal()).mul(cubeNormal);
        float texWidth = geoModel.textureWidth();
        float texHeight = geoModel.textureHeight();

        // north / min Z
        emitQuad(consumer, pose, normal, packedLight, cubePose, texWidth, texHeight,
                uv(cube, geoModel, FpGeoModel.FACE_NORTH), cube.mirror(),
                0.0F, 0.0F, -1.0F,
                x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        // south / max Z
        emitQuad(consumer, pose, normal, packedLight, cubePose, texWidth, texHeight,
                uv(cube, geoModel, FpGeoModel.FACE_SOUTH), cube.mirror(),
                0.0F, 0.0F, 1.0F,
                x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
        // east / min X (geo +X is mirrored into -X by AzureLib's bake)
        emitQuad(consumer, pose, normal, packedLight, cubePose, texWidth, texHeight,
                uv(cube, geoModel, FpGeoModel.FACE_EAST), cube.mirror(),
                -1.0F, 0.0F, 0.0F,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        // west / max X
        emitQuad(consumer, pose, normal, packedLight, cubePose, texWidth, texHeight,
                uv(cube, geoModel, FpGeoModel.FACE_WEST), cube.mirror(),
                1.0F, 0.0F, 0.0F,
                x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        // up / min Y (model space is Y-down, like the vanilla player model)
        emitQuad(consumer, pose, normal, packedLight, cubePose, texWidth, texHeight,
                uv(cube, geoModel, FpGeoModel.FACE_UP), cube.mirror(),
                0.0F, -1.0F, 0.0F,
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        // down / max Y
        emitQuad(consumer, pose, normal, packedLight, cubePose, texWidth, texHeight,
                uv(cube, geoModel, FpGeoModel.FACE_DOWN), cube.mirror(),
                0.0F, 1.0F, 0.0F,
                x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
    }

    /**
     * Resolves the UV rectangle of a face: per-face UVs first, then the classic box UV layout, and
     * finally the whole texture.
     */
    private static float[] uv(FpGeoModel.Cube cube, FpGeoModel geoModel, int face) {
        float[] faceUv = cube.faceUv(face);
        if (faceUv != null) return faceUv;

        float[] boxUv = cube.boxUv();
        if (boxUv != null) return boxFaceUv(cube, boxUv, face);

        return new float[]{0.0F, 0.0F, geoModel.textureWidth(), geoModel.textureHeight()};
    }

    /**
     * Classic Minecraft box UV layout, using the cube's pixel dimensions.
     */
    private static float[] boxFaceUv(FpGeoModel.Cube cube, float[] boxUv, int face) {
        float u = boxUv[0];
        float v = boxUv[1];
        float sx = cube.sizeX();
        float sy = cube.sizeY();
        float sz = cube.sizeZ();

        return switch (face) {
            case FpGeoModel.FACE_NORTH -> new float[]{u + sz, v + sz, sx, sy};
            case FpGeoModel.FACE_EAST -> new float[]{u, v + sz, sz, sy};
            case FpGeoModel.FACE_SOUTH -> new float[]{u + sz + sx + sz, v + sz, sx, sy};
            case FpGeoModel.FACE_WEST -> new float[]{u + sz + sx, v + sz, sz, sy};
            case FpGeoModel.FACE_UP -> new float[]{u + sz, v, sx, sz};
            case FpGeoModel.FACE_DOWN -> new float[]{u + sz + sx, v, sx, sz};
            default -> new float[]{u, v, sx, sy};
        };
    }

    /**
     * Emits one textured quad. Corners are ordered top-left, top-right, bottom-right, bottom-left as
     * seen from outside the cube.
     */
    private static void emitQuad(VertexConsumer consumer, Matrix4f pose, Matrix3f normalMatrix, int packedLight,
                                 Matrix4f cubePose, float texWidth, float texHeight,
                                 float[] uv, boolean mirror, float nx, float ny, float nz,
                                 float ax, float ay, float az,
                                 float bx, float by, float bz,
                                 float cx, float cy, float cz,
                                 float dx, float dy, float dz) {
        float u0 = uv[0] / texWidth;
        float v0 = uv[1] / texHeight;
        float u1 = (uv[0] + uv[2]) / texWidth;
        float v1 = (uv[1] + uv[3]) / texHeight;

        if (mirror) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }

        NORMAL_SCRATCH.set(nx, ny, nz);
        normalMatrix.transform(NORMAL_SCRATCH);

        vertex(consumer, pose, cubePose, packedLight, NORMAL_SCRATCH, ax, ay, az, u0, v0);
        vertex(consumer, pose, cubePose, packedLight, NORMAL_SCRATCH, bx, by, bz, u1, v0);
        vertex(consumer, pose, cubePose, packedLight, NORMAL_SCRATCH, cx, cy, cz, u1, v1);
        vertex(consumer, pose, cubePose, packedLight, NORMAL_SCRATCH, dx, dy, dz, u0, v1);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix4f cubePose, int packedLight,
                               Vector3f normal, float x, float y, float z, float u, float v) {
        VERTEX_SCRATCH.set(x, y, z);
        cubePose.transformPosition(VERTEX_SCRATCH);
        pose.transformPosition(VERTEX_SCRATCH);

        consumer.addVertex(VERTEX_SCRATCH.x, VERTEX_SCRATCH.y, VERTEX_SCRATCH.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(normal.x, normal.y, normal.z);
    }
}
