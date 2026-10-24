package com.github.razorplay01.fp_pal.firstperson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Bedrock geometry (geo.json) model used by {@link FpModelRenderer} to preview an AzureLib
 * item rig in first person.
 * <p>
 * The conversion is the same one AzureLib's {@code AzBuiltinBakedModelFactory} applies, so a model
 * authored for an AzureLib {@code AzItemRenderer} lines up with PAL's first-person arm frame:
 * <ul>
 *     <li>bone pivot: {@code (-x, y, z)};</li>
 *     <li>bone rotation: raw degrees converted to radians, with X and Y negated at render time by
 *     {@link FpArmRenderUtil} (the values stored here stay raw, like PAL's animation values);</li>
 *     <li>cube origin: {@code (-(x + width), y, z)};</li>
 *     <li>cube pivot: {@code (-x, y, z)} and rotation {@code (-rx, -ry, rz)} in radians.</li>
 * </ul>
 * Units inside this class are model pixels (1/16 block); the renderer divides by 16.
 */
public final class FpGeoModel {

    public static final int FACE_NORTH = 0;
    public static final int FACE_EAST = 1;
    public static final int FACE_SOUTH = 2;
    public static final int FACE_WEST = 3;
    public static final int FACE_UP = 4;
    public static final int FACE_DOWN = 5;

    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

    private final int textureWidth;
    private final int textureHeight;
    private final List<Bone> rootBones;
    private final Map<String, Bone> bones;

    private FpGeoModel(int textureWidth, int textureHeight, List<Bone> rootBones, Map<String, Bone> bones) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.rootBones = List.copyOf(rootBones);
        this.bones = Collections.unmodifiableMap(new LinkedHashMap<>(bones));
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    public List<Bone> rootBones() {
        return rootBones;
    }

    public Map<String, Bone> bones() {
        return bones;
    }

    public int boneCount() {
        return bones.size();
    }

    /**
     * @return the bone with the given name, or {@code null}
     */
    public @Nullable Bone bone(String name) {
        return bones.get(name);
    }

    /**
     * Parses the first geometry of a Bedrock {@code geo.json} file.
     *
     * @throws IllegalArgumentException if the file is not a geometry
     */
    public static FpGeoModel parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            throw new IllegalArgumentException("Model has no minecraft:geometry array");
        }

        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        int textureWidth = getInt(description, "texture_width", 16);
        int textureHeight = getInt(description, "texture_height", 16);

        JsonArray boneArray = geometry.getAsJsonArray("bones");
        if (boneArray == null || boneArray.isEmpty()) {
            throw new IllegalArgumentException("Model has no bones");
        }

        Map<String, Bone> bones = new LinkedHashMap<>();
        for (JsonElement element : boneArray) {
            if (!element.isJsonObject()) continue;
            Bone bone = parseBone(element.getAsJsonObject());
            bones.put(bone.name, bone);
        }

        List<Bone> roots = new ArrayList<>();
        for (Bone bone : bones.values()) {
            if (bone.parentName == null) {
                roots.add(bone);
                continue;
            }
            Bone parent = bones.get(bone.parentName);
            if (parent == null) {
                // A missing parent behaves like a root, which is what Blockbench previews do.
                roots.add(bone);
                continue;
            }
            bone.parent = parent;
            parent.children.add(bone);
        }

        return new FpGeoModel(textureWidth, textureHeight, roots, bones);
    }
    private static Bone parseBone(JsonObject json) {
        String name = getString(json, "name", "bone");
        String parent = getString(json, "parent", null);
        float[] pivot = getFloatArray(json, "pivot", new float[3]);
        float[] rotation = getFloatArray(json, "rotation", new float[3]);
        boolean visible = !(json.has("neverRender") && json.get("neverRender").getAsBoolean());

        List<Cube> cubes = new ArrayList<>();
        JsonArray cubeArray = json.getAsJsonArray("cubes");
        float boneInflate = getFloat(json, "inflate", 0.0F);
        if (cubeArray != null) {
            for (JsonElement element : cubeArray) {
                if (element.isJsonObject()) {
                    cubes.add(parseCube(element.getAsJsonObject(), boneInflate));
                }
            }
        }

        // AzureLib stores the X component of every pivot mirrored; rotations stay raw here because
        // FpArmRenderUtil applies the same X/Y negation as AzBone does.
        return new Bone(
                name,
                parent,
                -pivot[0], pivot[1], pivot[2],
                (float) Math.toRadians(rotation[0]),
                (float) Math.toRadians(rotation[1]),
                (float) Math.toRadians(rotation[2]),
                visible,
                cubes
        );
    }

    private static Cube parseCube(JsonObject json, float boneInflate) {
        float[] origin = getFloatArray(json, "origin", new float[3]);
        float[] size = getFloatArray(json, "size", new float[3]);
        float[] pivot = getFloatArray(json, "pivot", new float[3]);
        float[] rotation = getFloatArray(json, "rotation", new float[3]);
        float inflate = getFloat(json, "inflate", boneInflate);
        boolean mirror = json.has("mirror") && json.get("mirror").getAsBoolean();

        // Negative sizes are technically legal in Bedrock geometry. Normalise them so the renderer
        // always sees a positive extent; the UVs keep their own sign.
        float sizeX = size[0];
        float sizeY = size[1];
        float sizeZ = size[2];
        float originX = origin[0];
        float originY = origin[1];
        float originZ = origin[2];
        if (sizeX < 0) { originX += sizeX; sizeX = -sizeX; }
        if (sizeY < 0) { originY += sizeY; sizeY = -sizeY; }
        if (sizeZ < 0) { originZ += sizeZ; sizeZ = -sizeZ; }

        // AzureLib: origin = (-(x + width), y, z), pivot = (-x, y, z),
        // rotation = (-rx, -ry, rz) radians.
        float x0 = -(originX + sizeX);
        float cubePivotX = -pivot[0];
        float cubePivotY = pivot[1];
        float cubePivotZ = pivot[2];
        float rotX = (float) -Math.toRadians(rotation[0]);
        float rotY = (float) -Math.toRadians(rotation[1]);
        float rotZ = (float) Math.toRadians(rotation[2]);

        float[] boxUv = null;
        float[][] faceUv = new float[6][];
        JsonElement uvElement = json.get("uv");
        if (uvElement != null) {
            if (uvElement.isJsonArray()) {
                float[] uv = toFloatArray(uvElement.getAsJsonArray());
                if (uv.length >= 2) boxUv = new float[]{uv[0], uv[1]};
            } else if (uvElement.isJsonObject()) {
                JsonObject uvObject = uvElement.getAsJsonObject();
                for (int i = 0; i < FACE_NAMES.length; i++) {
                    faceUv[i] = parseFaceUv(uvObject.get(FACE_NAMES[i]));
                }
            }
        }

        return new Cube(
                x0, originY, originZ, sizeX, sizeY, sizeZ,
                cubePivotX, cubePivotY, cubePivotZ,
                rotX, rotY, rotZ,
                inflate, mirror,
                boxUv, faceUv
        );
    }

    private static float [] parseFaceUv(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        float[] uv = getFloatArray(object, "uv", new float[]{0, 0});
        float[] uvSize = getFloatArray(object, "uv_size", new float[]{1, 1});
        return new float[]{uv[0], uv[1], uvSize[0], uvSize[1]};
    }

    private static String getString(JsonObject object, String name, @Nullable String fallback) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int getInt(@Nullable JsonObject object, String name, int fallback) {
        if (object == null) return fallback;
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }

    private static float getFloat(JsonObject object, String name, float fallback) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static float[] getFloatArray(JsonObject object, String name, float[] fallback) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonArray()) return fallback.clone();
        float[] values = toFloatArray(element.getAsJsonArray());
        if (values.length >= 3) return new float[]{values[0], values[1], values[2]};
        if (values.length == 2) {
            if (fallback.length >= 3) return new float[]{values[0], values[1], fallback[2]};
            return new float[]{values[0], values[1]};
        }
        if (values.length == 1) {
            if (fallback.length >= 3) return new float[]{values[0], fallback[1], fallback[2]};
            if (fallback.length == 2) return new float[]{values[0], fallback[1]};
            return new float[]{values[0]};
        }
        return fallback.clone();
    }

    private static float[] toFloatArray(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            values[i] = element != null && element.isJsonPrimitive() ? element.getAsFloat() : 0.0F;
        }
        return values;
    }

    /**
     * A bone in the model tree.
     */
    public static final class Bone {
        private final String name;
        private final @Nullable String parentName;
        private @Nullable Bone parent;
        private final List<Bone> children = new ArrayList<>();
        private final List<Cube> cubes;
        private final float pivotX;
        private final float pivotY;
        private final float pivotZ;
        /** Raw Blockbench radians (not negated); {@link FpArmRenderUtil} negates X/Y. */
        private final float rotationX;
        private final float rotationY;
        private final float rotationZ;
        private final boolean visible;

        /**
         * Scratch transform reused every frame, so rendering does not allocate a bone per frame.
         */
        private final PlayerAnimBone transform;

        private Bone(String name, @Nullable String parentName,
                     float pivotX, float pivotY, float pivotZ,
                     float rotationX, float rotationY, float rotationZ,
                     boolean visible, List<Cube> cubes) {
            this.name = name;
            this.parentName = parentName;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.visible = visible;
            this.cubes = List.copyOf(cubes);
            this.transform = new PlayerAnimBone(name);
        }

        public String name() {
            return name;
        }

        public @Nullable Bone parent() {
            return parent;
        }

        public List<Bone> children() {
            return children;
        }

        public List<Cube> cubes() {
            return cubes;
        }

        /** Stored pivot (geo X already negated), in model pixels. */
        public float pivotX() { return pivotX; }
        public float pivotY() { return pivotY; }
        public float pivotZ() { return pivotZ; }

        /** Raw Blockbench rotation in radians. */
        public float rotationX() { return rotationX; }
        public float rotationY() { return rotationY; }
        public float rotationZ() { return rotationZ; }

        public boolean visible() {
            return visible;
        }

        /**
         * Fills and returns the reusable transform: the animated values of this bone plus its static
         * geo rotation. The caller must not keep the returned instance.
         */
        PlayerAnimBone animatedTransform() {
            PlayerAnimBone extra = FpRenderState.getExtraBone(name);
            if (extra == null) {
                // The animation has no keyframe for this bone: keep the static geo bind pose.
                transform.setToInitialPose();
                //? if >=26.1.2 {
                /*// PAL 1.2 keeps the transform in public JOML vectors instead of per-axis setters.
                transform.getRotX() = rotationX;
                transform.getRotY() = rotationY;
                transform.getRotZ() = rotationZ;
                *///?} else {
                transform.setRotX(rotationX);
                transform.setRotY(rotationY);
                transform.setRotZ(rotationZ);
                //?}
            } else {
                // AzureLib's AzBoneAnimationUpdateUtil writes the animated rotation over the bone's
                // bind rotation instead of adding to it, so the animation value wins here too.
                FpRenderState.copy(extra, transform);
            }
            return transform;
        }
    }
    /**
     * A baked cuboid. Coordinates are model pixels, already in the same mirrored frame AzureLib uses.
     */
    public static final class Cube {
        private final float x0, y0, z0;
        private final float sizeX, sizeY, sizeZ;
        private final float pivotX, pivotY, pivotZ;
        private final float rotationX, rotationY, rotationZ;
        private final float inflate;
        private final boolean mirror;
        private final float [] boxUv;
        private final float[] [] faceUv;

        private Cube(float x0, float y0, float z0, float sizeX, float sizeY, float sizeZ,
                     float pivotX, float pivotY, float pivotZ,
                     float rotationX, float rotationY, float rotationZ,
                     float inflate, boolean mirror,
                     float [] boxUv, float[] [] faceUv) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.inflate = inflate;
            this.mirror = mirror;
            this.boxUv = boxUv;
            this.faceUv = faceUv;
        }

        public float x0() { return x0; }
        public float y0() { return y0; }
        public float z0() { return z0; }
        public float sizeX() { return sizeX; }
        public float sizeY() { return sizeY; }
        public float sizeZ() { return sizeZ; }
        public float pivotX() { return pivotX; }
        public float pivotY() { return pivotY; }
        public float pivotZ() { return pivotZ; }
        public float rotationX() { return rotationX; }
        public float rotationY() { return rotationY; }
        public float rotationZ() { return rotationZ; }
        public float inflate() { return inflate; }
        public boolean mirror() { return mirror; }

        public float [] boxUv() { return boxUv; }

        public float [] faceUv(int face) { return faceUv[face]; }
    }
}



