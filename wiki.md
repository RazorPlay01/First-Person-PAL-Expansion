# First Person PAL Expansion — API & Format Reference

Documentation for using **First Person PAL Expansion** (`fp_pal`) as a library: the bone conventions, the animation/model formats, the in-game commands, and the Java API.

---

## Table of Contents

1. [Requirements](#requirements)
2. [Bone conventions](#bone-conventions)
3. [Animation format](#animation-format)
4. [Extra animation data](#extra-animation-data)
5. [Model preview](#model-preview-geojson)
6. [In-game commands](#in-game-commands)
7. [Java API](#java-api)
8. [Examples](#examples)

---

## Requirements

- **Player Animation Library (PAL)** — this add-on builds on PAL's animation stack.
  - Dependency id: `player_animation_library` (1.21.1+).
  - Package: `com.zigythebird.playeranim` / `com.zigythebird.playeranimcore`.
- The mod is **client-only**.

The FP classes live under `com.github.razorplay01.fp_pal`:

```java
import com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationResources;
import com.github.razorplay01.fp_pal.firstperson.FpSyncRegistry;
import net.minecraft.resources.Identifier;
```

---

## Bone conventions

Your first-person animation drives these bones. Names follow the AzureLib convention, so existing
AzureLib arm rigs (e.g. `player_arms_import.bbmodel`) work with minimal changes.

| Bone | Purpose |
|---|---|
| `fp_player_arms` | Root of the whole first-person rig (arms, items and camera). |
| `rightArm` / `leftArm` | The arms. PAL also normalizes these to `right_arm` / `left_arm`. |
| `fp_r_item` / `fp_l_item` | The held items (legacy aliases: `fp_right_item` / `fp_left_item`). |
| `fp_camera` | The real first-person camera (position + roll). |

**Root aliases** (AzureLib-style roots, accepted instead of `fp_player_arms`):
`player_arms`, `playerArms`, `peacemaker`, `group`, `root`.

**Extra bones**: any other bone your animation touches (`mag`, `slide`, `flash`, `body`, ...) is kept
available for custom detail rendering via `FpRenderState#getExtraBone(String)`, and its PAL sound /
particle keyframes keep working as usual.

---

## Animation format

First-person animations use the **Bedrock animation format** (the same one PAL loads).

- Location: `assets/<namespace>/fp_animations/<path>.animation.json`.
- Each file can declare several animations; the id is `<namespace>:<animationName>`.

```jsonc
{
  "format_version": "1.8.0",
  "animations": {
    "fp_reload": {
      "loop": "hold_on_last_frame",   // true, "hold_on_last_frame", or omitted (play once)
      "animation_length": 1.5,
      "bones": {
        "rightArm": {
          "position": { "0.0": { "vector": [6, -6.3, -0.8] } },
          "rotation": { "0.0": { "vector": [90, 0, -180] } }
        },
        "fp_r_item": {
          "rotation": {
            "0.0": { "vector": [0, -90, 0] },
            "0.5": { "vector": [90, -77.5, -450] }
          }
        }
      },
      "sound_effects": {
        "0.0417": { "effect": "minecraft:item.trident.throw" }
      }
    }
  },
  "azurelib_format_version": 2
}
```

- `loop`: `true` (loop forever), `"hold_on_last_frame"` (stop and hold the last frame), or omitted
  (play once).
- `bones.<name>.position` / `.rotation` / `.scale` use the standard Bedrock keyframe structure
  (`"<time>": { "vector": [...], "easing": "..." }`).
- `sound_effects` uses PAL's sound keyframe format.
- `azurelib_format_version: 2` enables the AzureLib-compatible extra-data block (see below).

---

## Extra animation data

Inside an animation you can add a `player_animation_library` object to configure how the rig is
framed in first person. These keys are read by the add-on and are **not** bones.

```jsonc
"player_animation_library": {
  "fpDisplayTranslation": [-2.5, 0.5, 1.5],
  "fpDisplayScale": 1.5,
  "fpDisplayAnchor": "hand"
}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `fpDisplayTranslation` | `[x, y, z]` | `[0, 0, 0]` | Display translation of the arm model, in 1/16 block. |
| `fpDisplayScale` | number | `1.0` | Uniform display scale of the arm model. |
| `fpDisplayAnchor` | `"camera"` or `"hand"` | `"camera"` | The frame the rig is drawn in (see below). |

**Anchors**

- `"camera"` (default): the rig origin sits on the view axis, centred like the Blockbench preview.
  Use this for rigs authored around the model origin (`player_arms_import.bbmodel` convention).
- `"hand"`: the rig origin is the vanilla first-person hand anchor. Use this for AzureLib item rigs
  (guns) whose arms are authored around the held item.

---

## Model preview (`.geo.json`)

You can ship an optional **Bedrock geometry model** to preview extra geometry (weapon body, magazine,
slide, flash...) in first person. It is resolved automatically from the animation:

- `assets/<namespace>/fp_models/<path>.geo.json`
- `assets/<namespace>/textures/fp/<path>.png`

Resolution order for an animation:
1. The animation id itself (`fp_models/<animation>.geo.json`).
2. The `.animation.json` file it was declared in (e.g. `firing` inside `peacemaker.animation.json`
   falls back to `fp_models/peacemaker.geo.json`).

Only when **both** the `.geo.json` and its texture exist is the model loaded. The model root follows
the same animated root bone; arm bones are skipped (PAL draws the vanilla arms there) and every other
bone renders with its animated transform plus the static geo rotation.

---

## In-game commands

Commands are client-side and registered under `/fp_anim`:

| Command | Description |
|---|---|
| `/fp_anim play <animationID>` | Play a first-person animation. |
| `/fp_anim stop` | Stop the current first-person animation. |
| `/fp_anim list` | List all loaded first-person animations. |
| `/fp_anim pairs` | List registered third-person → first-person pairs. |
| `/fp_anim pair <thirdID> <fpID>` | Register a third-person → first-person pair. |
| `/fp_anim mode [mode]` | Print or force the first-person mode (`auto` resets it). |
| `/fp_anim bones` | Print the bones the current animation drives and their transforms. |
| `/fp_anim model [id] [texture]` | Override the auto-resolved `.geo.json` model (test aid). |
| `/fp_anim model off` | Disable the model preview. |

---

## Java API

### `FpAnimationSystem`

Central entry point for playing and controlling first-person animations.

```java
// Plays a first-person animation on the given (client) player.
// Returns false if the animation is unknown or no controller exists.
boolean FpAnimationSystem.playFpAnimation(AbstractClientPlayer player, Identifier fpAnimationId);

// Stops the local player's current first-person animation.
void FpAnimationSystem.stopFpAnimation();

// Registers a 3rd-person -> first-person pairing (see FpSyncRegistry).
void FpAnimationSystem.registerPaired(Identifier thirdPersonId, Identifier fpId);

// First-person mode control (reports FirstPersonMode, or null if no controller).
FirstPersonMode FpAnimationSystem.getFirstPersonMode();
boolean FpAnimationSystem.setFirstPersonMode(FirstPersonMode mode);
boolean FpAnimationSystem.clearFirstPersonModeOverride();
```

### `FpAnimationResources`

Read-only access to the loaded first-person animations.

```java
Animation FpAnimationResources.getAnimation(Identifier id);          // null if unknown
boolean   FpAnimationResources.hasAnimation(Identifier id);
Map<Identifier, Animation> FpAnimationResources.getAnimations();     // read-only
Map<String, Animation> FpAnimationResources.getModAnimations(String modid);
Identifier FpAnimationResources.getModelId(Identifier animationId);  // inferred .geo.json id
```

### `FpSyncRegistry`

Maps third-person animations to their first-person counterparts.

```java
void FpSyncRegistry.registerPaired(Identifier thirdPersonId, Identifier fpId);
Identifier FpSyncRegistry.getFpAnimation(Identifier thirdPersonId);   // null if none
Identifier FpSyncRegistry.getThirdPersonAnimation(Identifier fpId);   // null if none
boolean     FpSyncRegistry.hasFpPair(Identifier thirdPersonId);
Map<Identifier, Identifier> FpSyncRegistry.getAllPairs();
```

> Pairing only stores the mapping. Play the FP animation yourself (typically from a PAL animation
> event listener) using `FpAnimationSystem.playFpAnimation` + `getFpAnimation`.

### `FpRenderState` (advanced)

Exposes the current first-person render state for custom detail rendering.

```java
// True while the given entity is playing a first-person animation.
boolean FpRenderState.isPlayerArmMode(Entity entity);

// Extra-bone transforms of the current animation (mag, slide, flash, ...).
PlayerAnimBone FpRenderState.getExtraBone(String name);   // null if not animated
Map<String, PlayerAnimBone> FpRenderState.getExtraBones();

// Mod-level display transform used when an animation has no fpDisplayTranslation/Scale.
void FpRenderState.setArmDisplayTransform(float x, float y, float z, float scale);
void FpRenderState.resetArmDisplayTransform();
```

---

## Examples

### Play a first-person animation from a third-person event

```java
import com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem;
import com.github.razorplay01.fp_pal.firstperson.FpSyncRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;

public class MyAnimations {
    // Call when a third-person animation starts.
    public static void onAnimationStart(AbstractClientPlayer player, Identifier thirdPersonId) {
        Identifier fpId = FpSyncRegistry.getFpAnimation(thirdPersonId);
        if (fpId != null) {
            FpAnimationSystem.playFpAnimation(player, fpId);
        }
    }
}
```

### Ship a first-person rig

```
assets/mymod/
├── fp_animations/
│   └── peacemaker.animation.json   // declares "firing", "reload", ...
├── fp_models/
│   └── peacemaker.geo.json
└── textures/fp/
    └── peacemaker.png
```

---

## License

MIT. See [LICENSE](LICENSE).


