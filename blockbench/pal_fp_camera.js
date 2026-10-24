/**
 * Player Animation Library - First Person Camera
 *
 * Blockbench plugin that puts the viewport camera exactly where Minecraft's first-person camera
 * is, so a first-person arm rig can be animated while looking at the same image as in game.
 *
 * <h2>Why the anchor is not the origin</h2>
 * PAL renders the first-person pass in view space: the eye is at (0, 0, 0) and the rig origin is
 * translated to (0, -0.52, -0.72) before the {@code fp_player_arms} root is applied (see
 * {@code FpArmRenderUtil.applyCameraAnchor}). In other words, the camera sits 0.52 blocks above
 * the rig origin and 0.72 blocks behind it, looking along -Z. This plugin places the Blockbench
 * camera at exactly that point, which is the frame {@code player_arms_import.bbmodel} is authored
 * in: arms at x = ±6, items in front of them, rig origin between the hands.
 *
 * <h2>Why the camera is written through the orbit controls</h2>
 * {@code Preview#render} calls {@code controls.update()} before drawing, and Blockbench's
 * OrbitControls derives the spherical offset from the camera position and the target on every
 * call. Setting both (position + a target one unit straight ahead) therefore survives the update
 * instead of fighting it, and the result is stable frame to frame without disabling the controls.
 *
 * <h2>Why fpDisplayScale becomes a field of view</h2>
 * The mod applies {@code fpDisplayScale} as a uniform scale around the eye, which on screen is a
 * pure zoom. Scaling the model is not possible from a plugin, so the plugin divides the tangent of
 * half the FOV by the scale, which produces the same on-screen size.
 *
 * <h2>Why the fp_camera position moves the camera *and* the rig</h2>
 * {@code FpCameraUtil} adds the {@code fp_camera} bone to Minecraft's real camera along the camera's
 * right, up and forward vectors. The arms are drawn in view space afterwards, so a camera
 * translation slides the world behind them and leaves them glued to the screen. The plugin moves the
 * camera by that vector and the {@code fp_player_arms} root by the same one, which reproduces both
 * halves of that effect. The roll is handled by rotating the camera itself, because a roll turns the
 * whole image, arms included.
 *
 * <h2>Why there is an axis conversion toggle</h2>
 * Blockbench applies animation values as they are stored, while {@code FpArmRenderUtil} converts
 * them: it negates the animated X and the animated X and Y rotations and composes the axes in the
 * Z, Y, X order. The rig itself is not mirrored ({@code FpGeoModel} already negates the exported geo
 * coordinates), so only the animation values differ. The "PAL first-person axes" toggle applies that
 * same conversion, which makes the preview show what the current renderer draws. Leave it off while
 * the mod uses the raw Blockbench convention.
 *
 * @author RazorPlay01
 * @license MIT
 */
(function () {
	'use strict';

	const PLUGIN_ID = 'pal_fp_camera';
	const STORAGE_KEY = PLUGIN_ID + '.settings';
	const PX_PER_BLOCK = 16;

	/**
	 * Mirrors {@code FpArmRenderUtil.applyCameraAnchor} (0, -0.52, -0.72) and Minecraft's default
	 * FOV. Change these if the mod's anchor changes.
	 */
	const DEFAULTS = {
		fov: 70,
		height: 0.52,
		distance: 0.72,
		offset_x: 0,
		offset_y: 0,
		offset_z: 0,
		scale: 1,
		roll: true,
		camera_move: true,
		hand_anchored: false,
		// PAL's first-person renderer always converts the authored values
		// (FpArmRenderUtil#translateMatrixToBone negates X, #rotateMatrixAroundBone negates X and Y and
		// composes in the Z, Y, X order), so the preview needs the conversion on to match the game.
		game_axes: true,
	};

	const FIELDS = {
		fov: { label: 'Field of view (the Minecraft "FOV" option)', type: 'number', min: 30, max: 120, step: 1 },
		height: { label: 'Eye height above the rig origin (blocks)', type: 'number', min: -4, max: 4, step: 0.01 },
		distance: { label: 'Distance from the eye to the rig origin (blocks)', type: 'number', min: 0.05, max: 8, step: 0.01 },
		offset_x: { label: 'fpDisplayTranslation X (model pixels)', type: 'number', min: -64, max: 64, step: 0.5 },
		offset_y: { label: 'fpDisplayTranslation Y (model pixels)', type: 'number', min: -64, max: 64, step: 0.5 },
		offset_z: { label: 'fpDisplayTranslation Z (model pixels)', type: 'number', min: -64, max: 64, step: 0.5 },
		scale: { label: 'fpDisplayScale', type: 'number', min: 0.1, max: 4, step: 0.05 },
		roll: { label: 'Preview the fp_camera roll (its rotation Z)', type: 'toggle' },
		camera_move: { label: 'Preview the fp_camera position (moves the camera and the rig, like in game)', type: 'toggle' },
		hand_anchored: { label: 'The clip uses fpDisplayAnchor "hand" (arms on the vanilla hand anchor)', type: 'toggle' },
		game_axes: { label: 'Convert bone values to the PAL first-person axes (negate X position and X/Y rotation)', type: 'toggle' },
	};

	let settings = Object.assign({}, DEFAULTS);
	let enabled = false;
	let active_preview = null;
	let saved_preview_state = null;
	let settings_dialog = null;
	let toggle_action = null;
	let settings_action = null;


	// MARK: Helpers

	function toast(message) {
		try {
			if (typeof Blockbench !== 'undefined' && typeof Blockbench.showQuickMessage === 'function') {
				Blockbench.showQuickMessage(message, 1600);
			} else {
				console.log('[FP Camera] ' + message);
			}
		} catch (error) {
			console.log('[FP Camera] ' + message);
		}
	}

	function warn(message, error) {
		console.warn('[FP Camera] ' + message, error || '');
	}

	function clamp(value, min, max, fallback) {
		value = Number(value);
		if (!isFinite(value)) return fallback;
		return Math.min(max, Math.max(min, value));
	}

	function selectedPreview() {
		if (typeof Preview === 'undefined' || !Preview) return null;
		return Preview.selected || null;
	}

	function loadSettings() {
		try {
			const raw = localStorage.getItem(STORAGE_KEY);
			if (!raw) return;
			const parsed = JSON.parse(raw);
			for (const key in DEFAULTS) {
				if (typeof DEFAULTS[key] === 'boolean') {
					if (typeof parsed[key] === 'boolean') settings[key] = parsed[key];
				} else if (typeof parsed[key] === 'number' && isFinite(parsed[key])) {
					settings[key] = clamp(parsed[key], FIELDS[key].min, FIELDS[key].max, DEFAULTS[key]);
				}
			}
		} catch (error) {
			// First run, storage disabled or corrupt data: keep the defaults.
		}
	}

	function saveSettings() {
		try {
			localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
		} catch (error) {
			// Storage is optional; the camera still works with the in-memory values.
		}
	}

	// MARK: Camera math

	/**
	 * @return the vertical field of view that makes the rig appear {@code fpDisplayScale} times
	 * bigger, which is what a uniform scale around the eye does on screen.
	 */
	function effectiveFov() {
		const scale = Math.max(0.05, settings.scale);
		const half = (settings.fov * Math.PI / 180) / 2;
		return 2 * Math.atan(Math.tan(half) / scale) * 180 / Math.PI;
	}

	/**
	 * @return the animated {@code fp_camera} roll in radians, or 0 when the bone is missing.
	 * <p>
	 * Only the roll is reproduced. In game {@code FpCameraUtil} applies the camera position to the
	 * real camera, but the arms are drawn in view space afterwards, so a moving camera leaves the
	 * hands glued to the screen. A roll, on the other hand, rotates the whole image, arms included,
	 * which is exactly what rotating the Blockbench camera does.
	 */
	function fpCameraRoll() {
		try {
			if (typeof Group === 'undefined' || !Group.all) return 0;
			const group = Group.all.find(candidate => candidate && String(candidate.name).toLowerCase() === 'fp_camera');
			if (!group || !group.mesh || !group.mesh.rotation) return 0;
			const roll = Number(group.mesh.rotation.z);
			return isFinite(roll) ? roll : 0;
		} catch (error) {
			return 0;
		}
	}

	/**
	 * @return the camera position in Blockbench units (1/16 block) for the configured anchor.
	 * <p>
	 * The eye is 0.52 blocks above the rig origin and 0.72 blocks behind it, so the origin is in
	 * front of the camera. {@code fpDisplayTranslation} moves the rig in view space, so the camera
	 * is moved by the opposite amount to keep the same on-screen result.
	 */
	function cameraPosition() {
		// fpDisplayAnchor "hand" puts the rig on vanilla's right-hand anchor instead of the view
		// axis (see FpArmRenderUtil.applyHandTransform), so the eye sits 0.56 blocks to its left.
		const handAnchor = settings.hand_anchored ? -0.56 * PX_PER_BLOCK : 0;
		return {
			x: -settings.offset_x + handAnchor,
			y: settings.height * PX_PER_BLOCK - settings.offset_y,
			z: settings.distance * PX_PER_BLOCK - settings.offset_z,
		};
	}

	function applyTo(preview) {
		if (!preview || !preview.camPers) return;

		// The first-person pass is a perspective view; an orthographic camera screen would not
		// match the game at all.
		if (preview.isOrtho && typeof preview.setProjectionMode === 'function') {
			preview.setProjectionMode(false);
		}

		const camera = preview.camPers;
		const position = cameraPosition();

		// fp_camera translation: Minecraft's camera moves along its own axes, and the rig is drawn in
		// view space afterwards, so the preview moves the camera and moves the rig back by the same
		// amount (see applyRigCompensation) to keep the arms where they are on screen.
		if (settings.camera_move) {
			const offset = fpCameraOffset();
			if (offset) {
				position.x += offset.x;
				position.y += offset.y;
				position.z += offset.z;
			}
		}

		camera.position.set(position.x, position.y, position.z);
		camera.rotation.set(0, 0, settings.roll ? fpCameraRoll() : 0);

		// Keep the orbit target one unit straight ahead (-Z) instead of at the model centre. The
		// controls are frozen while the mode is on, but a consistent target keeps the state sane for
		// the restore and for older Blockbench versions whose update() cannot be replaced.
		if (preview.controls && preview.controls.target) {
			preview.controls.target.set(position.x, position.y, position.z - 1);
		}

		const fov = effectiveFov();
		if (Math.abs(camera.fov - fov) > 0.001) {
			camera.fov = fov;
			camera.updateProjectionMatrix();
		}

		// Minecraft's near plane is 0.05 blocks; the rig starts 0.25 blocks away, so Blockbench's
		// default near plane can clip a hand held close to the eye.
		const near = 0.05 * PX_PER_BLOCK;
		if (camera.near !== near) {
			camera.near = near;
			camera.updateProjectionMatrix();
		}
	}

	function savePreviewState(preview) {
		const camera = preview.camPers || preview.camera;
		const controls = preview.controls || null;
		return {
			preview: preview,
			wasOrtho: !!preview.isOrtho,
			position: camera.position.clone(),
			quaternion: camera.quaternion.clone(),
			fov: camera.fov,
			near: camera.near,
			target: controls && controls.target ? controls.target.clone() : null,
			controls_update: controls && typeof controls.update === 'function' ? controls.update : null,
			controls_enabled: controls ? controls.enabled : null,
		};
	}

	/**
	 * Stops OrbitControls from touching the camera while the first-person view is on.
	 * <p>
	 * {@code Preview#render} calls {@code controls.update()} before drawing, and that call ends with
	 * {@code camera.lookAt(target)}, which would wipe the {@code fp_camera} roll right before every
	 * frame. Replacing the per-instance update function is the only hook that runs late enough.
	 */
	function lockPreview(preview, state) {
		if (!preview.controls || !state || !state.controls_update) return;
		preview.controls.update = function () {
			return false;
		};
		preview.controls.enabled = false;
	}

	function restorePreviewState(state) {
		if (!state || !state.preview || !state.preview.camPers) return;
		const preview = state.preview;
		const controls = preview.controls;

		if (controls) {
			if (state.controls_update) controls.update = state.controls_update;
			if (state.controls_enabled !== null) controls.enabled = state.controls_enabled;
		}

		if (preview.isOrtho && typeof preview.setProjectionMode === 'function') {
			preview.setProjectionMode(false);
		}

		const camera = preview.camPers;
		camera.position.copy(state.position);
		camera.quaternion.copy(state.quaternion);
		camera.fov = state.fov;
		camera.near = state.near;
		camera.updateProjectionMatrix();

		if (state.target && controls && controls.target) {
			controls.target.copy(state.target);
		}

		if (state.wasOrtho && typeof preview.setProjectionMode === 'function') {
			preview.setProjectionMode(true);
		}
	}


	// MARK: Game model transforms

	const CAMERA_BONE = 'fp_camera';
	const RIG_ROOT = 'fp_player_arms';

	function findGroup(name) {
		try {
			if (typeof Group === 'undefined' || !Group.all) return null;
			const wanted = String(name).toLowerCase();
			return Group.all.find(candidate => candidate && String(candidate.name).toLowerCase() === wanted) || null;
		} catch (error) {
			return null;
		}
	}

	/**
	 * Saves the local transform of a mesh so a frame's edits can be reverted.
	 * <p>
	 * The animator rewrites the transform on every frame, but a paused timeline does not, so the
	 * preview has to leave the model exactly as it found it.
	 */
	function saveTransform(mesh) {
		const position = mesh.position.clone();
		const rotation = mesh.rotation.clone();
		const quaternion = mesh.quaternion.clone();
		const scale = mesh.scale.clone();
		return function restore() {
			mesh.position.copy(position);
			mesh.rotation.copy(rotation);
			mesh.quaternion.copy(quaternion);
			mesh.scale.copy(scale);
			mesh.updateMatrix();
		};
	}

	/**
	 * @return the animated offset of a bone in the units Blockbench stores it in: degrees for
	 * rotations, model pixels for positions. Blockbench composes it on top of the rest pose
	 * ({@code mesh = origin + animation}), which is what the animation file holds.
	 */
	function animatedOffset(group) {
		const mesh = group.mesh;
		const rest = group.origin || [0, 0, 0];
		const restRotation = group.rotation || [0, 0, 0];
		const toDegrees = 180 / Math.PI;
		return {
			x: mesh.position.x - rest[0],
			y: mesh.position.y - rest[1],
			z: mesh.position.z - rest[2],
			rotX: (mesh.rotation.x - restRotation[0] / toDegrees) * toDegrees,
			rotY: (mesh.rotation.y - restRotation[1] / toDegrees) * toDegrees,
			rotZ: (mesh.rotation.z - restRotation[2] / toDegrees) * toDegrees,
		};
	}

	/**
	 * Replicates the axis conventions of PAL's first-person renderer, which are not the ones
	 * Blockbench shows.
	 * <p>
	 * {@code FpArmRenderUtil.translateMatrixToBone} negates the animated X and
	 * {@code rotateMatrixAroundBone} negates the animated X and Y and multiplies the three axes in
	 * the Z, Y, X order (Blockbench's default Euler order is X, Y, Z). The rig itself is not
	 * mirrored: {@code FpGeoModel} already negates the exported geo coordinates, so the bones sit
	 * exactly where the bbmodel puts them. Only the animation values are converted, which is why the
	 * offsets are read out of the mesh and written back mirrored.
	 */
	function applyGameAxes() {
		const undos = [];
		let groups;
		try {
			groups = (typeof Group !== 'undefined' && Group.all) ? Group.all.slice() : [];
		} catch (error) {
			return function () { };
		}

		const toRadians = Math.PI / 180;
		for (const group of groups) {
			if (!group || !group.mesh) continue;
			// fp_camera is authored directly in view space and PAL applies it without conversion
			// (see FpCameraUtil), so it is left alone.
			if (String(group.name).toLowerCase() === CAMERA_BONE) continue;

			const offset = animatedOffset(group);
			if (offset.x === 0 && offset.rotX === 0 && offset.rotY === 0) continue;

			const mesh = group.mesh;
			const rest = group.origin || [0, 0, 0];
			const restRotation = group.rotation || [0, 0, 0];
			undos.push(saveTransform(mesh));

			mesh.position.x = rest[0] - offset.x;
			mesh.rotation.order = 'ZYX';
			mesh.rotation.set(
				(restRotation[0] - offset.rotX) * toRadians,
				(restRotation[1] - offset.rotY) * toRadians,
				(restRotation[2] + offset.rotZ) * toRadians
			);
			mesh.updateMatrix();
		}

		return function () {
			for (const undo of undos) undo();
		};
	}



	/**
	 * @return the {@code fp_camera} bone position in Blockbench units, in the camera's own axes, or
	 * null when there is nothing to preview.
	 * <p>
	 * {@code FpCameraUtil} adds the bone to Minecraft's real camera along the camera's right, up and
	 * forward vectors, which moves the world but leaves the arms glued to the screen because they are
	 * drawn in view space afterwards. The preview does the same by moving the camera and the rig by
	 * the same vector, so the grid slides exactly like the world does in game.
	 */
	function fpCameraOffset() {
		const group = findGroup(CAMERA_BONE);
		if (!group || !group.mesh) return null;

		const offset = animatedOffset(group);
		if (offset.x === 0 && offset.y === 0 && offset.z === 0) return null;

		const roll = settings.roll ? fpCameraRoll() : 0;
		const cos = Math.cos(roll);
		const sin = Math.sin(roll);
		// The camera rotation is (0, 0, roll), so its local axes are the world ones rolled around Z,
		// and Minecraft's forward is the negative local Z.
		return {
			x: offset.x * cos - offset.y * sin,
			y: offset.x * sin + offset.y * cos,
			z: -offset.z,
		};
	}

	/**
	 * Moves the rig with the camera, so a camera translation slides the world behind the arms the way
	 * it does in game instead of dragging the arms along.
	 */
	function applyRigCompensation() {
		const offset = fpCameraOffset();
		const group = findGroup(RIG_ROOT);
		if (!offset || !group || !group.mesh) return function () { };

		const mesh = group.mesh;
		const undo = saveTransform(mesh);
		mesh.position.set(
			mesh.position.x + offset.x,
			mesh.position.y + offset.y,
			mesh.position.z + offset.z
		);
		mesh.updateMatrix();
		return undo;
	}

	/**
	 * Applies every model-side transform the game applies for one frame and returns a function that
	 * puts the model back. The camera itself is handled by {@link applyTo}.
	 */
	function wrapRender() {
		const undos = [];
		try {
			if (settings.game_axes) undos.push(applyGameAxes());
			if (settings.camera_move) undos.push(applyRigCompensation());
		} catch (error) {
			warn('Could not apply the in-game model transforms', error);
		}
		return function undo() {
			for (let i = undos.length - 1; i >= 0; i--) {
				try {
					undos[i]();
				} catch (error) {
					warn('Could not restore the model transforms', error);
				}
			}
		};
	}


	// MARK: Enable / disable

	function onRenderFrame() {
		if (!enabled) return;

		const preview = selectedPreview();
		if (!preview) return;

		// Another preview tab became active: hand the camera of the previous one back first.
		if (preview !== active_preview) {
			restorePreviewState(saved_preview_state);
			active_preview = preview;
			saved_preview_state = savePreviewState(preview);
			lockPreview(preview, saved_preview_state);
		}

		// The model-side transforms live for one frame only: the animator rewrites the bone
		// transforms on the next one, and restoring them keeps a paused timeline correct too.
		const undo = wrapRender();
		try {
			applyTo(preview);
		} finally {
			undo();
		}
	}

	function enable() {
		if (enabled) return;

		const preview = selectedPreview();
		if (!preview) {
			toast('First person camera: no preview to place the camera in');
			return;
		}

		active_preview = preview;
		saved_preview_state = savePreviewState(preview);
		lockPreview(preview, saved_preview_state);
		enabled = true;

		try {
			Blockbench.removeListener('render_frame', onRenderFrame);
		} catch (error) {
			// Nothing registered yet.
		}
		Blockbench.on('render_frame', onRenderFrame);

		applyTo(preview);
		toast('First person camera: on');
	}

	function disable() {
		if (!enabled) return;

		enabled = false;
		try {
			Blockbench.removeListener('render_frame', onRenderFrame);
		} catch (error) {
			warn('Could not remove the render listener', error);
		}

		restorePreviewState(saved_preview_state);
		active_preview = null;
		saved_preview_state = null;
		toast('First person camera: off');
	}

	function toggle() {
		if (enabled) disable();
		else enable();
	}


	// MARK: Dialog

	function buildForm() {
		const form = {};
		for (const key in FIELDS) {
			form[key] = Object.assign({}, FIELDS[key], { value: settings[key] });
		}
		return form;
	}

	function applyForm(result) {
		if (!result) return;
		for (const key in FIELDS) {
			if (FIELDS[key].type === 'toggle') {
				settings[key] = !!result[key];
			} else {
				settings[key] = clamp(result[key], FIELDS[key].min, FIELDS[key].max, DEFAULTS[key]);
			}
		}
		saveSettings();
		if (enabled) applyTo(active_preview);
	}

	function showSettings() {
		if (!settings_dialog) {
			settings_dialog = new Dialog({
				id: PLUGIN_ID + '_dialog',
				title: 'First Person Camera',
				icon: 'photo_camera',
				width: 640,
				form: buildForm(),
				buttons: ['Apply', 'Reset to defaults', 'Cancel'],
				// BB5 uses onConfirm, older versions use onFormSubmit. Both are idempotent.
				onConfirm(result) {
					applyForm(result);
				},
				onFormSubmit(result) {
					applyForm(result);
				},
				onButton(index) {
					if (index !== 1) return;
					settings = Object.assign({}, DEFAULTS);
					saveSettings();
					if (enabled) applyTo(active_preview);
					toast('First person camera: Minecraft defaults restored');
				}
			});
		} else if (typeof settings_dialog.setFormValues === 'function') {
			const values = {};
			for (const key in FIELDS) values[key] = settings[key];
			settings_dialog.setFormValues(values);
		}

		settings_dialog.show();
	}

	// MARK: Menu

	function addToMenu(action) {
		try {
			if (typeof MenuBar === 'undefined' || !MenuBar) return;
			if (MenuBar.menus && MenuBar.menus.view && typeof MenuBar.menus.view.addAction === 'function') {
				MenuBar.menus.view.addAction(action);
			} else if (typeof MenuBar.addAction === 'function') {
				MenuBar.addAction(action, 'view');
			}
		} catch (error) {
			warn('Could not add the menu entry', error);
		}
	}

	// MARK: Plugin

	Plugin.register(PLUGIN_ID, {
		title: 'First Person Camera',
		icon: 'photo_camera',
		author: 'RazorPlay01',
		description: 'Places the viewport camera where Minecraft\'s first-person camera is and applies the in-game first-person transforms (camera bone, held items), so first-person arm rigs can be animated from the same view as in game.',
		tags: ['Animation', 'Minecraft'],
		version: '1.1.0',
		min_version: '4.9.0',
		variant: 'both',

		onload() {
			loadSettings();

			toggle_action = new Action(PLUGIN_ID + '_toggle', {
				name: 'First Person Camera',
				description: 'Place the viewport camera where Minecraft\'s first-person camera is',
				icon: 'photo_camera',
				click() {
					toggle();
				}
			});
			addToMenu(toggle_action);

			settings_action = new Action(PLUGIN_ID + '_settings', {
				name: 'First Person Camera Settings...',
				description: 'Tune the first-person anchor, FOV and display transform',
				icon: 'settings',
				click() {
					showSettings();
				}
			});
			addToMenu(settings_action);
		},

		onunload() {
			disable();
			if (toggle_action) toggle_action.delete();
			if (settings_action) settings_action.delete();
			toggle_action = null;
			settings_action = null;
			settings_dialog = null;
		}
	});
})();

