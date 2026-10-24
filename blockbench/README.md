# First Person Camera (Blockbench plugin)

`player_animation_library_fp_camera.js` puts the Blockbench viewport camera exactly where
Minecraft's first-person camera is, so the first-person arm rig can be animated while looking at the
same image as in game.

## Install

Copy `pal_fp_camera.js` into the Blockbench plugins folder:

* Windows: `%APPDATA%\Blockbench\plugins`
* macOS: `~/Library/Application Support/Blockbench/plugins`
* Linux: `~/.config/Blockbench/plugins`

The file name does not matter to Blockbench (the plugin registers itself as `pal_fp_camera`); the
copy installed here is named `player_animation_library_fp_camera.js` to match its `.about.md` /
`.icon.png` companions.

Then restart Blockbench. You can also use **File → Plugins → Load Plugin from File** for a one-off
test, but the plugin is only remembered when it lives in the plugins folder.

## Usage

* **View → First Person Camera** toggles the mode. The previous camera (position, rotation, FOV,
  near plane and orbit target) is restored when you toggle it off.
* **View → First Person Camera Settings...** opens every option below.

While the mode is on the camera is locked: the orbit controls are disabled and the camera is
re-applied every frame, so what you see is what the game renders. Toggle it off to look around the
rig again.

The plugin also applies the transforms the game applies to the model, every frame, and restores the
bones afterwards (so a paused timeline is not corrupted):

* the animated `fp_camera` **roll** and **position** (the camera and the rig move together, which is
  what `FpCameraUtil` does to the real camera while the arms stay in view space);
* the **item chain** of `fp_r_item` / `fp_l_item`, split exactly like the game splits it: vanilla's
  hand anchor (`applyItemArmTransform`) goes *outside* the item bone and the
  `firstperson_righthand` / `firstperson_lefthand` display transform goes *inside* it, as
  `ItemInHandRenderer#renderArmWithItem` and `ItemTransform#apply` apply them.

## What the plugin does *not* do

The held items are deliberately left alone. The plugin does not apply vanilla's hand anchor or the
`firstperson_righthand` display transform to `fp_r_item` / `fp_l_item`: the rig previews exactly the
pose the animation gives those bones, which is what the mod draws once the extra vanilla transforms
are taken out of the way (see `MIGRATION.md`, *The held item and vanilla's display transform*). If the
item looks different in game than in Blockbench, that is a renderer issue, not a preview one.

## Defaults

The defaults are the values the mod and vanilla Minecraft use, so a rig authored for PAL previews
1:1 with no setup:

| Option | Default | Where it comes from |
| --- | --- | --- |
| Field of view | `70` | Minecraft's *FOV* option (vertical FOV, the same convention Blockbench uses) |
| Eye height | `0.52` blocks | `FpArmRenderUtil.applyCameraAnchor` → `translate(0, -0.52, -0.72)` |
| Distance | `0.72` blocks | same anchor, the `-0.72` on Z |
| `fpDisplayTranslation` X/Y/Z | `0` | per-animation extra data, in model pixels |
| `fpDisplayScale` | `1` | per-animation extra data |
| Roll preview | on | the animated `fp_camera` rotation Z (`FpCameraUtil` rolls the real camera) |
| Position preview | on | the animated `fp_camera` position (`FpCameraUtil` moves the real camera) |
| `fpDisplayAnchor: "hand"` | off | on when the clip anchors the rig on the hand instead of the view axis |
| PAL first-person axes | on | see [Accuracy](#accuracy); the renderer always converts, so the preview does too |
| Item chain | on | `FpArmRendererMixin` (hand anchor + item display transform) |
| Item display rotation | `0, -90, 25` | `ItemDisplayContext.FIRST_PERSON_RIGHT_HAND` |
| Item display translation | `1.13, 3.2, 1.13` px | same entry (`ItemTransform#apply` mirrors X for the left hand) |
| Item display scale | `0.68` | same entry |

That puts the Blockbench camera at `(0, 8.32, 11.52)` model units, looking along `-Z`, which is the
frame `player_arms_import.bbmodel` is authored in: arms at `x = ±6`, rig origin between the hands.

If an animation ships with `player_animation_library` extras, copy them into the settings dialog:

```json
"player_animation_library": {
  "fpDisplayAnchor": "camera",
  "fpDisplayTranslation": [0, -2, 3],
  "fpDisplayScale": 1.5
}
```

`fpDisplayTranslation` is in model pixels; `fpDisplayScale` is reproduced as a field of view change
(scaling the rig around the eye is a pure zoom on screen, and a plugin cannot scale the model).

## Accuracy

For a perspective camera the projected position of every vertex is identical to the game: the game
computes `view = (0, -0.52, -0.72) + model / 16` and the plugin places the camera so that
`view = (model - camera) / 16`, which is the same ray.

What is reproduced exactly:

* **Camera frame.** Anchor, FOV, near plane and the aspect ratio behaviour of a perspective camera.
* **`fp_camera`.** Its roll rotates the camera and its position moves the camera *and* the rig by the
  same vector, in the camera's own axes. `FpCameraUtil` adds the bone to Minecraft's real camera
  along `right`, `up` and `forward`, and the arms are drawn in view space afterwards, so the world
  slides behind them and they stay glued to the screen: moving both reproduces that exactly.
* **Held items.** The plugin does not touch `fp_r_item` / `fp_l_item`, so the preview shows exactly
  the pose the animation gives them. That is what the mod draws once vanilla's display transform is
  skipped (see *What the plugin does not do* above).

Things that legitimately differ:

* **Item model.** The item *display transform* matches, but the preview shows whatever geometry the
  item bone contains. Vanilla also adds the swing/equip transforms (`applyItemArmAttackTransform`),
  which are driven by the player's swing progress and are not part of an animation clip.
* **Aspect ratio.** The FOV is vertical in both, so the vertical framing always matches; the
  horizontal one matches when the viewport has the same aspect ratio as the game window.
* **Animation values vs. the renderer's axes.** Blockbench stores the authored values and composes
  them on top of the rest pose, while `FpArmRenderUtil` converts them: `translateMatrixToBone` negates
  the animated X, `rotateMatrixAroundBone` negates the animated X and Y and multiplies the axes in the
  Z, Y, X order (Blockbench's default Euler order is X, Y, Z). The rig itself is not mirrored
  (`FpGeoModel` already negates the exported geo coordinates, so the bones sit exactly where the
  bbmodel puts them); only the animation values differ. That conversion is **always** applied by the
  renderer, so the **PAL first-person axes** toggle is on by default and the preview matches the game
  out of the box. Turn it off only to look at the raw values as Blockbench stores them.
* **Rest pose.** A bone whose *rest* rotation is not zero is not reproduced the way the renderer does
  it: Blockbench adds the animation to the rest rotation, while the renderer applies the animation
  values alone. The `player_arms_import.bbmodel` rig has zero rest rotations, so this does not show
  up there.

## Tests

`tools/test_fp_camera.js` drives the plugin with a small three.js stand-in (real `Euler`/`Quaternion`
order conventions, `rotation`/`quaternion` syncing) and checks the camera frame, the rig
compensation, the item chain and the axis conversion:

```
node tools/test_fp_camera.js
FP_TEST_GAME_AXES=1 node tools/test_fp_camera.js
```

