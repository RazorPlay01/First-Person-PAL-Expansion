/**
 * Validation harness for blockbench/pal_fp_camera.js.
 *
 * Implements the small slice of three.js the plugin uses (Vector3, Quaternion, Euler with the real
 * three.js Euler-order conventions) plus Blockbench's plugin globals, then drives one frame of the
 * preview and checks the camera, the rig compensation, the item chain and the axis conversion.
 *
 * three.js conventions implemented here (verified against the formulas in Quaternion.setFromEuler):
 *   order 'XYZ' -> q = qx * qy * qz  -> matrix Rx * Ry * Rz
 *   order 'ZYX' -> q = qz * qy * qx  -> matrix Rz * Ry * Rx  (what joml's Quaternionf.rotationXYZ
 *                                                            produces, which is what vanilla's
 *                                                            ItemTransform#apply uses)
 */
'use strict';
const assert = require('assert');

// MARK: three.js slice

class Vector3 {
	constructor(x = 0, y = 0, z = 0) { this.x = x; this.y = y; this.z = z; }
	clone() { return new Vector3(this.x, this.y, this.z); }
	copy(v) { this.x = v.x; this.y = v.y; this.z = v.z; return this; }
	set(x, y, z) { this.x = x; this.y = y; this.z = z; return this; }
	add(v) { this.x += v.x; this.y += v.y; this.z += v.z; return this; }
	multiply(v) { this.x *= v.x; this.y *= v.y; this.z *= v.z; return this; }
	multiplyScalar(s) { this.x *= s; this.y *= s; this.z *= s; return this; }
	applyQuaternion(q) {
		const x = this.x, y = this.y, z = this.z;
		const qx = q.x, qy = q.y, qz = q.z, qw = q.w;
		const ix = qw * x + qy * z - qz * y;
		const iy = qw * y + qz * x - qx * z;
		const iz = qw * z + qx * y - qy * x;
		const iw = -qx * x - qy * y - qz * z;
		this.x = ix * qw + iw * -qx + iy * -qz - iz * -qy;
		this.y = iy * qw + iw * -qy + iz * -qx - ix * -qz;
		this.z = iz * qw + iw * -qz + ix * -qy - iy * -qx;
		return this;
	}
}

class Quaternion {
	constructor(x = 0, y = 0, z = 0, w = 1) { this.x = x; this.y = y; this.z = z; this.w = w; this._onChange = null; }
	clone() { return new Quaternion(this.x, this.y, this.z, this.w); }
	copy(q, update = true) {
		this.x = q.x; this.y = q.y; this.z = q.z; this.w = q.w;
		if (update && this._onChange) this._onChange();
		return this;
	}
	multiply(q, update = true) {
		const ax = this.x, ay = this.y, az = this.z, aw = this.w;
		const bx = q.x, by = q.y, bz = q.z, bw = q.w;
		this.x = ax * bw + aw * bx + ay * bz - az * by;
		this.y = ay * bw + aw * by + az * bx - ax * bz;
		this.z = az * bw + aw * bz + ax * by - ay * bx;
		this.w = aw * bw - ax * bx - ay * by - az * bz;
		if (update && this._onChange) this._onChange();
		return this;
	}
	setFromEuler(e, update = true) {
		const c1 = Math.cos(e.x / 2), c2 = Math.cos(e.y / 2), c3 = Math.cos(e.z / 2);
		const s1 = Math.sin(e.x / 2), s2 = Math.sin(e.y / 2), s3 = Math.sin(e.z / 2);
		switch (e.order) {
			case 'XYZ':
				this.x = s1 * c2 * c3 + c1 * s2 * s3;
				this.y = c1 * s2 * c3 - s1 * c2 * s3;
				this.z = c1 * c2 * s3 + s1 * s2 * c3;
				this.w = c1 * c2 * c3 - s1 * s2 * s3;
				break;
			case 'ZYX':
				this.x = s1 * c2 * c3 - c1 * s2 * s3;
				this.y = c1 * s2 * c3 + s1 * c2 * s3;
				this.z = c1 * c2 * s3 - s1 * s2 * c3;
				this.w = c1 * c2 * c3 + s1 * s2 * s3;
				break;
			default:
				throw new Error('unsupported Euler order ' + e.order);
		}
		if (update && this._onChange) this._onChange();
		return this;
	}
}

function matrixFromQuaternion(q) {
	const x = q.x, y = q.y, z = q.z, w = q.w;
	const x2 = x + x, y2 = y + y, z2 = z + z;
	const xx = x * x2, xy = x * y2, xz = x * z2;
	const yy = y * y2, yz = y * z2, zz = z * z2;
	const wx = w * x2, wy = w * y2, wz = w * z2;
	return [
		1 - (yy + zz), xy - wz, xz + wy,
		xy + wz, 1 - (xx + zz), yz - wx,
		xz - wy, yz + wx, 1 - (xx + yy),
	];
}

function clamp(value) { return Math.max(-1, Math.min(1, value)); }

class Euler {
	constructor(x = 0, y = 0, z = 0, order = 'XYZ') {
		this.x = x; this.y = y; this.z = z; this.order = order; this._onChange = null;
	}
	clone() { return new Euler(this.x, this.y, this.z, this.order); }
	copy(e, update = true) {
		this.x = e.x; this.y = e.y; this.z = e.z; this.order = e.order;
		if (update && this._onChange) this._onChange();
		return this;
	}
	set(x, y, z, order) {
		this.x = x; this.y = y; this.z = z;
		if (order !== undefined) this.order = order;
		if (this._onChange) this._onChange();
		return this;
	}
	setFromQuaternion(q, order, update = true) {
		order = order || this.order;
		const m = matrixFromQuaternion(q);
		if (order === 'XYZ') {
			this.y = Math.asin(clamp(m[2]));
			if (Math.abs(m[2]) < 0.9999999) {
				this.x = Math.atan2(-m[5], m[8]);
				this.z = Math.atan2(-m[1], m[0]);
			} else {
				this.x = Math.atan2(m[7], m[4]);
				this.z = 0;
			}
		} else if (order === 'ZYX') {
			this.y = Math.asin(clamp(-m[6]));
			if (Math.abs(m[6]) < 0.9999999) {
				this.x = Math.atan2(m[7], m[8]);
				this.z = Math.atan2(m[3], m[0]);
			} else {
				this.x = Math.atan2(-m[5], m[4]);
				this.z = 0;
			}
		} else {
			throw new Error('unsupported Euler order ' + order);
		}
		this.order = order;
		if (update && this._onChange) this._onChange();
		return this;
	}
}

class Object3D {
	constructor(name) {
		this.name = name;
		this.position = new Vector3();
		this.rotation = new Euler();
		this.quaternion = new Quaternion();
		this.scale = new Vector3(1, 1, 1);
		this.matrix = null;
		// three.js keeps rotation and quaternion in sync through these callbacks.
		this.rotation._onChange = () => this.quaternion.setFromEuler(this.rotation, false);
		this.quaternion._onChange = () => this.rotation.setFromQuaternion(this.quaternion, undefined, false);
	}
	updateMatrix() {
		this.matrix = { position: this.position.clone(), quaternion: this.quaternion.clone(), scale: this.scale.clone() };
	}
}

module.exports = { Vector3, Quaternion, Euler, Object3D, matrixFromQuaternion };

// MARK: Blockbench stubs

const groups = [];
let snapshot = null;
let frameListener = null;
let pluginDef = null;
const actions = [];

function makeGroup(name, origin, rotation) {
	const mesh = new Object3D(name);
	mesh.position.set(origin[0], origin[1], origin[2]);
	mesh.quaternion.setFromEuler(mesh.rotation);
	mesh.updateMatrix();
	const group = { name: name, origin: origin.slice(), rotation: (rotation || [0, 0, 0]).slice(), mesh: mesh };
	groups.push(group);
	return group;
}

/** Reproduces what Blockbench's animator does: mesh = rest pose + animation value. */
function animate(group, position, rotation) {
	const mesh = group.mesh;
	mesh.position.set(
		group.origin[0] + (position ? position[0] : 0),
		group.origin[1] + (position ? position[1] : 0),
		group.origin[2] + (position ? position[2] : 0)
	);
	mesh.rotation.order = 'XYZ';
	mesh.rotation.set(
		(group.rotation[0] + (rotation ? rotation[0] : 0)) * Math.PI / 180,
		(group.rotation[1] + (rotation ? rotation[1] : 0)) * Math.PI / 180,
		(group.rotation[2] + (rotation ? rotation[2] : 0)) * Math.PI / 180
	);
	mesh.quaternion.setFromEuler(mesh.rotation);
	mesh.scale.set(1, 1, 1);
	mesh.updateMatrix();
}

const fpRoot = makeGroup('fp_player_arms', [0, 0, 0]);
const rightArm = makeGroup('rightArm', [6, 6, 6]);
const leftArm = makeGroup('leftArm', [-6, 6, 6]);
const rightItem = makeGroup('fp_r_item', [6, 6, 6]);
const leftItem = makeGroup('fp_l_item', [-6, 6, 6]);
const fpCamera = makeGroup('fp_camera', [0, 0, 0]);

const camera = {
	position: new Vector3(),
	quaternion: new Quaternion(),
	rotation: new Euler(),
	fov: 70,
	near: 0.1,
	updateProjectionMatrix() { },
};
const originalSet = camera.position.set.bind(camera.position);
let lastCameraPosition = null;
camera.position.set = function (x, y, z) {
	originalSet(x, y, z);
	lastCameraPosition = { x: x, y: y, z: z };
};

// applyTo sets the position first and the rotation right after, and the model-side transforms are
// still applied at that point (the undo runs after), so the rotation setter is where the rendered
// frame can be observed.
const originalRotationSet = camera.rotation.set.bind(camera.rotation);
camera.rotation.set = function (x, y, z, order) {
	originalRotationSet(x, y, z, order);
	snapshot = {
		camera: { x: lastCameraPosition.x, y: lastCameraPosition.y, z: lastCameraPosition.z, roll: camera.rotation.z },
		root: fpRoot.mesh.position.clone(),
		rightArm: { position: rightArm.mesh.position.clone(), quaternion: rightArm.mesh.quaternion.clone() },
		rightItem: {
			position: rightItem.mesh.position.clone(),
			quaternion: rightItem.mesh.quaternion.clone(),
			scale: rightItem.mesh.scale.clone(),
		},
		leftItem: {
			position: leftItem.mesh.position.clone(),
			quaternion: leftItem.mesh.quaternion.clone(),
			scale: leftItem.mesh.scale.clone(),
		},
	};
};

const preview = {
	camPers: camera,
	camera: camera,
	isOrtho: false,
	controls: { update() { }, enabled: true, target: new Vector3() },
	setProjectionMode() { },
};

global.localStorage = { getItem() { return null; }, setItem() { } };
global.Blockbench = {
	showQuickMessage() { },
	on(event, listener) { if (event === 'render_frame') frameListener = listener; },
	removeListener() { frameListener = null; },
};
global.Plugin = { register(id, definition) { pluginDef = definition; } };
global.Action = class Action {
	constructor(id, options) { this.id = id; this.options = options; actions.push(this); }
	click() { this.options.click(); }
	delete() { }
};
global.Dialog = class Dialog { constructor() { } show() { } };
global.Group = { all: groups };
global.Preview = { selected: preview };

// MARK: Tests

// PAL's first-person renderer always converts the authored values (FpArmRenderUtil negates X on
// positions and X/Y on rotations and composes in the Z, Y, X order), so the plugin converts by
// default too. FP_TEST_GAME_AXES=0 exercises the raw Blockbench values instead.
const GAME_AXES = process.env.FP_TEST_GAME_AXES !== '0';
global.localStorage.getItem = function () { return JSON.stringify({ game_axes: GAME_AXES }); };

function close(actual, expected, message) {
	assert.ok(Math.abs(actual - expected) < 1e-6, message + ': expected ' + expected + ', got ' + actual);
}

function closeVector(actual, expected, message) {
	close(actual.x, expected[0], message + '.x');
	close(actual.y, expected[1], message + '.y');
	close(actual.z, expected[2], message + '.z');
}

function closeQuaternion(actual, expected, message) {
	const same = Math.abs(actual.x - expected.x) < 1e-6 && Math.abs(actual.y - expected.y) < 1e-6
		&& Math.abs(actual.z - expected.z) < 1e-6 && Math.abs(actual.w - expected.w) < 1e-6;
	const flipped = Math.abs(actual.x + expected.x) < 1e-6 && Math.abs(actual.y + expected.y) < 1e-6
		&& Math.abs(actual.z + expected.z) < 1e-6 && Math.abs(actual.w + expected.w) < 1e-6;
	assert.ok(same || flipped, message + ': got ' + JSON.stringify(actual) + ', expected ' + JSON.stringify(expected));
}

function eulerQuaternion(x, y, z, order) {
	return new Quaternion().setFromEuler(new Euler(
		x * Math.PI / 180, y * Math.PI / 180, z * Math.PI / 180, order));
}

function frame() {
	frameListener();
}

function resetAnimation() {
	animate(fpRoot, [0, 0, 0], [0, 0, 0]);
	animate(rightArm, [0, 0, 0], [0, 0, 0]);
	animate(leftArm, [0, 0, 0], [0, 0, 0]);
	animate(rightItem, [0, 0, 0], [0, 0, 0]);
	animate(leftItem, [0, 0, 0], [0, 0, 0]);
	animate(fpCamera, [0, 0, 0], [0, 0, 0]);
}

// Load the plugin (an IIFE that registers itself) and turn it on.
require(require('path').join(__dirname, '..', 'blockbench', 'pal_fp_camera.js'));
assert.ok(pluginDef, 'the plugin registered itself');
pluginDef.onload();
assert.ok(actions.length >= 1, 'the toggle action was created');
actions[0].click();
assert.ok(frameListener, 'the render listener was registered');

// 1. Rest pose: the camera sits on the configured anchor and the rig is untouched.
resetAnimation();
frame();
closeVector(snapshot.camera, [0, 0.52 * 16, 0.72 * 16], 'rest camera');
closeVector(snapshot.root, [0, 0, 0], 'rest rig root');

// 2. fp_camera X translation: the camera moves right and the rig follows it, so the arms stay put.
animate(fpCamera, [4, 0, 0], [0, 0, 0]);
frame();
closeVector(snapshot.camera, [4, 0.52 * 16, 0.72 * 16], 'camera moved by fp_camera X');
closeVector(snapshot.root, [4, 0, 0], 'rig compensated fp_camera X');

// 3. fp_camera Z translation: Minecraft's forward is -Z, so a positive value moves the camera in.
animate(fpCamera, [0, 0, 4], [0, 0, 0]);
frame();
closeVector(snapshot.camera, [0, 0.52 * 16, 0.72 * 16 - 4], 'camera moved by fp_camera Z');
closeVector(snapshot.root, [0, 0, -4], 'rig compensated fp_camera Z');

// 4. fp_camera Y translation goes up, and the roll rotates the offset together with the camera.
animate(fpCamera, [0, 3, 0], [0, 0, 0]);
frame();
closeVector(snapshot.camera, [0, 0.52 * 16 + 3, 0.72 * 16], 'camera moved by fp_camera Y');

animate(fpCamera, [4, 0, 0], [0, 0, 90]);
frame();
close(snapshot.camera.roll, Math.PI / 2, 'camera roll');
closeVector(snapshot.camera, [0, 0.52 * 16 + 4, 0.72 * 16], 'camera offset rotated by the roll');
closeVector(snapshot.root, [0, 4, 0], 'rig compensation rotated by the roll');

// 5. Arms: Blockbench applies the authored values, PAL converts them (toggle).
resetAnimation();
animate(rightArm, [1.5, -6.3, -0.9], [90, 0, -180]);
frame();
if (GAME_AXES) {
	closeVector(snapshot.rightArm.position, [6 - 1.5, 6 - 6.3, 6 - 0.9], 'arm X position negated');
	closeQuaternion(snapshot.rightArm.quaternion, eulerQuaternion(-90, 0, -180, 'ZYX'), 'arm rotation negated and reordered');
} else {
	closeVector(snapshot.rightArm.position, [7.5, -0.3, 5.1], 'arm keeps the authored position');
	closeQuaternion(snapshot.rightArm.quaternion, eulerQuaternion(90, 0, -180, 'XYZ'), 'arm keeps the authored rotation');
}

// 6. The model is restored after the frame, so a paused timeline is not corrupted.
animate(rightItem, [1.5, -4, -6], [10, 15, 5]);
frame();
closeVector(rightArm.mesh.position, [7.5, -0.3, 5.1], 'arm restored after the frame');
closeVector(rightItem.mesh.position, [7.5, 2, 0], 'item restored after the frame');
close(rightItem.mesh.scale.x, 1, 'item scale restored after the frame');
assert.strictEqual(rightItem.mesh.rotation.order, 'XYZ', 'Euler order restored after the frame');

console.log(GAME_AXES
	? 'PAL axis conversion: OK'
	: 'raw Blockbench values: OK');



