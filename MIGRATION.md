# Migración: implementación de primera persona de PAL → este addon

Este documento describe cómo se mueve la implementación de **first person arm animation** (que hoy
vive dentro de `PlayerAnimationLibrary`) a este addon, y qué superficie mínima tiene que quedarse en
PAL.

## Arquitectura objetivo

* **PAL** aporta el framework de animación (`playeranimcore` + el módulo `minecraft`) y una API
  mínima de primera persona: el modo `FirstPersonMode.PLAYER_ARM` y los dos *one-liners* que dependen
  de él.
* **Este addon** aporta toda la implementación: carga de animaciones FP, controlador, estado de
  render, render de brazos/items, cámara, mixins, comandos y recursos.

## Inventario: lo que se mueve

Tamaño actual (líneas): 3.301 en 16 ficheros Java.

| Origen en PAL | Destino en el addon |
| --- | --- |
| `animation/firstperson/FpArmRenderUtil.java` (419) | `firstperson/FpArmRenderUtil.java` |
| `animation/firstperson/FpRenderState.java` (430) | `firstperson/FpRenderState.java` |
| `animation/firstperson/FpModelRenderer.java` (407) | `firstperson/FpModelRenderer.java` |
| `animation/firstperson/FpGeoModel.java` (413) | `firstperson/FpGeoModel.java` |
| `animation/firstperson/FpAnimationSystem.java` (331) | `firstperson/FpAnimationSystem.java` |
| `animation/firstperson/FpAnimationController.java` (252) | `firstperson/FpAnimationController.java` |
| `animation/firstperson/FpAnimationResources.java` (141) | `firstperson/FpAnimationResources.java` |
| `animation/firstperson/FpSyncRegistry.java` (67) | `firstperson/FpSyncRegistry.java` |
| `animation/firstperson/FpCameraUtil.java` (65) | `firstperson/FpCameraUtil.java` |
| `commands/FpPlayerAnimCommands.java` (396) | `command/FpPlayerAnimCommands.java` |
| `commands/FpAnimationArgumentProvider.java` (28) | `command/FpAnimationArgumentProvider.java` |
| `mixin/firstPerson/FpArmRendererMixin.java` (171) | `mixin/FpArmRendererMixin.java` |
| `mixin/firstPerson/ItemInHandRendererMixin.java` (118) | `mixin/ItemInHandRendererMixin.java` |
| `mixin/CameraMixin.java` (36) | `mixin/CameraMixin.java` |
| `mixin/CameraAccessor.java` (19) | `mixin/CameraAccessor.java` |
| `fabric/.../client/IdentifiableFpAnimationResources.java` (12) | `platform/fabric/client/IdentifiableFpAnimationResources.java` |
| `assets/player_animation_library/fp_animations/fp_controls.animation.json` | `assets/<modid>/fp_animations/` |
| `assets/player_animation_library/fp_animations/peacemaker.animation.json` | `assets/<modid>/fp_animations/` |
| `assets/player_animation_library/fp_models/peacemaker.geo.json` | `assets/<modid>/fp_models/` |
| `assets/player_animation_library/textures/fp/*` | `assets/<modid>/textures/fp/` |
| `player_arms_import.bbmodel` (raíz de PAL) | raíz del addon |
| `blockbench/` (plugin + README) | raíz del addon |
| `tools/test_fp_camera.js` (arnés del plugin) | raíz del addon |

## Adaptaciones necesarias al mover

Estos puntos son los únicos que referencian internals de PAL y hay que resolverlos en el addon:

1. **`FpAnimationResources extends PlayerAnimResources`.** `PlayerAnimResources` es público, pero
   cargar `fp_animations` desde el addon es más limpio con un reload listener propio. Si se mantiene
   la herencia, hay que comprobar que el `ResourceManagerReloadListener` de PAL no esté cerrado a su
   propio namespace.
2. **`IAnimatedPlayer#playerAnimLib$getFpAnimationController()` no se puede añadir desde el addon**
   (es una interfaz de PAL). Solución: el addon define su propia interfaz de acceso
   (`IFpAnimatedPlayer`) y su mixin sobre `AbstractClientPlayer`, que ya implementa `IAnimatedPlayer`
   y expone `playerAnimLib$getAnimManager()`.
3. **`AbstractClientPlayerMixin` (PAL)** → mixin propio del addon sobre `AbstractClientPlayer`:
   capturar el `FpAnimationController` de la capa y llamar a `FpAnimationSystem.updateRenderState`
   en `tick`.
4. **`PlayerAnimLibMod.LOGGER`** → logger propio del addon.
5. **Entrypoints** (`PlayerAnimLibModFabric` / `PlayerAnimLibModNeo`) → inicializadores del addon:
   registrar el reload listener de animaciones FP y los comandos.
6. **`player_animation_library.mixins.json`** → `fppal.mixins.json` (config del addon) con sus
   propios mixins; los mixins FP dejan de listarse en PAL.
7. **`PlayerAnimBone`, `Animation`, `AnimationController`, `RawAnimation`, `FirstPersonConfiguration`,
   `PlayState`, `State`, `UniversalAnimLoader`, `Vec3f`, `AnimationBinary`, `LegacyAnimationBinary`,
   `PlayerAnimBone`, `PlayerAnimManager`, `PlayerAnimationController`, `PlayerAnimResources`,
   `PlayerAnimationAccess`, `PlayerAnimationFactory`, `RenderUtil`** son API pública de PAL: se
   importan sin cambios.

## Lo que se suple desde el addon (PAL no se toca)

Restricción: **PAL queda intacto**, así que los cambios que la feature necesitaba en PAL se hacen
desde el addon con mixins. La buena noticia es que, con el diseño correcto, casi no hacen falta.

| Cambio que hacía PAL | Cómo lo suple el addon |
| --- | --- |
| `FirstPersonMode.PLAYER_ARM` (constante nueva del enum) | **No hace falta**: el `FpAnimationController` del addon reporta `FirstPersonMode.NONE`. Ese modo ya produce exactamente el comportamiento que la feature necesita en PAL: no desengancha la cámara (solo `THIRD_PERSON_MODEL` lo hace), el fade de la primera persona vanilla apunta a `0.0` (la primera persona vanilla se queda visible) y no se renderiza el modelo de tercera persona. El addon es quien decide y dibuja todo lo FP. |
| `AnimationStack#getFirstPersonTransitionLength` / `PlayerAnimManager` con `isThirdPersonModel()` | Innecesario: son *one-liners* que solo existen para que un modo nuevo no desactive el fade; con `NONE` el código original de PAL ya hace lo correcto. |
| `AutoPlayingSoundKeyframeHandler` (pasar el `LocalPlayer` a `playSound`) | Mixin del addon sobre la clase de PAL: `@Inject`/`@Redirect` en el `playSound` del handler. Sin esto, los keyframes de sonido de una animación FP no se oyen en el cliente. |
| `IAnimatedPlayer#playerAnimLib$getFpAnimationController()` | El addon define su propia interfaz (`IFpAnimatedPlayer`) y su mixin sobre `AbstractClientPlayer`, que ya implementa `IAnimatedPlayer` y expone `playerAnimLib$getAnimManager()`. |
| `AbstractClientPlayerMixin` (captura del controlador FP + `FpAnimationSystem.updateRenderState` en `tick`) | Mixin del addon sobre `AbstractClientPlayer` (`@Inject` en `tick`, TAIL). Puede convivir con el mixin de PAL en el mismo método. |
| `PlayerAnimLibMod` (init del sistema FP) | `ModTemplate.onInitializeClient()` / los entrypoints del addon. |
| Entrypoints fabric/neoforge (reload listener FP + comandos FP) | `FabricClientEntrypoint` / `NeoforgeClientEventSubscriber` del addon. |
| `player_animation_library.mixins.json` | `modtemplate.mixins.json` del addon. |
| Accessor de `Camera` | El addon tiene su propia copia (`com.example.modtemplate.mixin.CameraAccessor`), que es además lo que ya hacía `FpCameraUtil`. |

Única limitación conocida: `FirstPersonMode` es un `enum` del core de PAL, así que si en el futuro se
quiere que *otras* capas de PAL (no solo el addon) entiendan el modo, habría que ampliarlo en PAL.
Con el diseño actual no es necesario.

## Matriz de versiones

El código actual está escrito para **1.21.1** (`ItemDisplayContext`, `Camera#getPosition`,
`PlayerModel#render`, `ItemInHandRenderer#renderItem`, `ResourceLocation`, …), así que la migración
va por fases:

1. `1.21.1-fabric` y `1.21.1-neoforge` (código existente, solo renombrado de paquete).
2. `1.21.11`, `26.1.2`, `26.2`, `26.3` (adaptaciones con Stonecutter).

Adaptaciones por versión a esperar (todas con comentarios `//? if`):

* `ResourceLocation` → `Identifier` y `location()` → `identifier()` (ya hay *replacements* en los
  build scripts para ≥ 1.21.11).
* `ItemDisplayContext` / `ItemTransform` (1.21.1) frente a sus equivalentes en 26.x.
* El accessor de la posición de `Camera` (`CameraAccessor`) cambia de nombre de campo entre
  versiones.
* `PlayerModel` / `ModelPart`: `render` → `renderToBuffer`, `setPos` / `setRotation`.
* Firmas de `ItemInHandRenderer#renderItem` / `ItemRenderer#renderStatic` y el tipo de
  `MultiBufferSource` / `RenderType`.
* Los tipos de PAL (`PlayerAnimBone`, `AnimationController`, …) mantienen el paquete entre versiones.

## Estado

* **PAL: intacto.** `git status` limpio, sin un solo cambio. El código FP se movió al addon y los
  ficheros de PAL que estaban modificados se revirtieron.
* **Addon: copia hecha y verificada** (16 ficheros Java, 3.305 líneas) en
  `com.example.modtemplate.{firstperson, command, mixin, mixin.firstPerson, platform.fabric.client}`,
  los recursos en `assets/modtemplate/{fp_animations, fp_models, textures/fp}`, y
  `player_arms_import.bbmodel`, `blockbench/` y `tools/` en la raíz.
* **Referencia en `migration/`**: `pal-fp-backup.zip` (copia de seguridad de todo lo que había en PAL),
  `pal-fp-changes.diff` (los diffs de los ficheros de PAL que estaban modificados: es la lista
  exacta de lo que hay que suplir con mixins), `pal-status-before.txt` y `pal-pistol.animation.json`
  (la animación FP antigua, sustituida por `peacemaker`).
* **Adaptación hecha y compilando**: `./gradlew :1.21.1-fabric:compileJava` termina en
  `BUILD SUCCESSFUL` contra PAL `1.1.4+mc.1.21.1` publicado en Maven. Es decir, la superficie de API FP
  que usa el addon (`FirstPersonMode`, `PlayerAnimationController`, `PlayerAnimResources`,
  `AnimationStack`, …) **ya está en la versión publicada**: para 1.21.1 no hace falta publicar PAL en
  local.
* **Validado en cliente (`1.21.1-fabric`)**: `runActiveClient` arranca hasta el menú (`Sound engine
  started`) y en el mundo todos los mixins se aplican sin errores (`AbstractClientPlayerFpMixin`,
  `AutoPlayingSoundKeyframeHandlerMixin`, `CameraMixin`, `CameraAccessor`), los comandos responden y los
  recursos cargan: `Using FP model modtemplate:peacemaker for animation modtemplate:aim`,
  `Playing FP animation: modtemplate:aim` y `modtemplate:fp_camera_test`.
* **Lección del mixin de sonido**: el `@At`/`@Redirect` tiene que coincidir con el bytecode *remapeado*
  de PAL, y un desajuste **no** es silencioso: tira el arranque con `InvalidInjectionException`. En
  1.21.1 el invoke es
  `Level.playSound:(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V`
  (owner `Level`, entidad excluida `Player`, y `SoundEvent` — ni `Entity` ni `Holder<SoundEvent>`). Se
  verifica con:
  `javap -c -cp .gradle/loom-cache/remapped_mods/remapped/com/zigythebird/playeranim/*/1.1.4+mc.1.21.1/PlayerAnimationLibFabric-*-1.1.4+mc.1.21.1.jar com.zigythebird.playeranim.animation.keyframe.event.builtin.AutoPlayingSoundKeyframeHandler`.
* **Fix de los brazos invisibles**: `FirstPersonConfiguration` de PAL tiene por defecto
  `showRightArm = false` / `showLeftArm = false` (los ítems, en cambio, `showRightItem/LeftItem = true`),
  y `AnimationStack#getFirstPersonConfiguration` solo resuelve la configuración de la capa que reporta
  un `FirstPersonMode` distinto de `NONE`. Como este addon reporta `NONE` a propósito, los mixins
  leían los defaults de PAL y se saltaban el dibujado de los brazos, mientras los ítems y los huesos
  extra seguían renderizándose (síntoma exacto del reporte). Ahora
  `FpRenderState#firstPersonConfiguration(Entity)` lee la config del controlador del addon
  (`DEFAULT_FP_CONFIGURATION = (true, true, true, true)`, o la que declare la animación) y los tres
  puntos que la consultaban (`FpArmRendererMixin` ×2 y `ItemInHandRendererMixin`) usan ese helper. Con
  esto el comportamiento vuelve a ser el del prototipo, donde el controlador reportaba `PLAYER_ARM` y
  PAL sí devolvía su configuración.
* **`1.21.1-neoforge`**: `./gradlew :1.21.1-neoforge:compileJava` también termina en `BUILD SUCCESSFUL`
  (incluye `stonecutterGenerate`, así que el `NeoforgeClientEventSubscriber` sin comentar compila contra
  NeoForge 21.1). El manifiesto de NeoForge declara el mixin config y el access transformer
  (`Loader.kt`), y el código FP no tiene imports de loader: el mismo código sirve para los dos loaders.
  Validado en cliente de neoforge: mixins aplicados (`AbstractClientPlayerFpMixin`,
  `FpArmRendererMixin`, `ItemInHandRendererMixin`, `CameraMixin`/`CameraAccessor`,
  `AutoPlayingSoundKeyframeHandlerMixin`), init de cliente con `FpAnimationSystem.init()` y
  `Sound engine started`, sin errores.
* **Pendiente**: el resto de versiones (`1.21.11`, `26.1.2`, `26.2`, `26.3`).

## Port multi-versión (fase 2: 1.21.11, 26.1.2, 26.2, 26.3)

Stonecutter declara las cinco versiones objetivo (cada una con su loader) y `stonecutter.properties.toml`
ya trae la dependencia de PAL por versión (`1.1.6+mc.1.21.11`, `1.2.2+mc.26.1`, `1.2.4+mc.26.2`,
`1.2.7+mc.26.3`). El código FP se escribe una sola vez y las diferencias se resuelven en dos capas:

1. **Reemplazos declarativos de Stonecutter** (`build.fabric.gradle.kts` / `build.neoforge.gradle.kts`).
   Los renombrados puros se resuelven como texto, sin ensuciar el código compartido:
   * vanilla 1.21.11: `ResourceLocation` → `Identifier`, `PlayerModel` →
     `net.minecraft.client.model.player.PlayerModel`, `RenderType` → `...renderer.rendertype.RenderType`
     con las *factories* movidas a `RenderTypes`, `PlayerRenderer` → `AvatarRenderer`, `PlayerModel<?>` →
     `PlayerModel` (deja de ser genérico).
   * PAL 1.1.6: `IAnimatedPlayer` → `IAnimatedAvatar`, `PlayerAnimManager` → `AvatarAnimManager` y el campo
     del controlador `this.player` → `this.avatar`.
2. **Bloques `//? if >=1.21.11`** para lo que no es un renombrado: firmas de métodos de mixin, targets de
   inyección y el pipeline de render.

### `FpRenderBuffer`: el pipeline de render en un solo sitio

Hasta 1.21.1 vanilla dibuja con `MultiBufferSource` (geometría → `VertexConsumer` de un `RenderType`,
volcado al cerrar el batch). Desde 1.21.9 (1.21.11 y 26.x) vanilla **colecciona** geometría con
`SubmitNodeCollector`, y el `VertexConsumer` solo existe dentro del callback de
`submitCustomGeometry`. En vez de repartir `//?` por todo el código de render, la diferencia se
encapsula en `FpRenderBuffer`:

| Operación | ≤1.21.1 | ≥1.21.11 |
| --- | --- | --- |
| brazo/manga (`ModelPart`) | `part.render(poseStack, buffer.getBuffer(rt), light, overlay)` | `collector.submitModelPart(part, poseStack, rt, light, overlay, null)` |
| geo model (`fp_models`) | escritura directa + `endBatch(rt)` | `collector.submitCustomGeometry(poseStack, rt, (pose, consumer) -> ...)` |

`FpArmRenderUtil`, `FpModelRenderer` y los mixins ya no conocen el pipeline: solo llaman a
`drawModelPart` / `drawGeometry`. Como consecuencia, los cuerpos de los métodos de inyección son
idénticos en las cinco versiones y solo cambian sus **firmas** (`MultiBufferSource` →
`SubmitNodeCollector`, y en 1.21.11 `renderItem` pierde el `boolean leftHand`).

### Cambios de API de PAL entre 1.1.4 y 1.1.6

* `IAnimatedPlayer` → `IAnimatedAvatar`; `PlayerAnimManager` → `AvatarAnimManager`.
* `PlayerAnimationController` pasa de `AbstractClientPlayer` a `Avatar` (nueva superclase de `Player`), y
  su campo pasa de `player` a `avatar`. El constructor de `FpAnimationController` acepta el tipo de cada
  versión con `//?`.
* `FirstPersonMode.isFirstPersonPass()` desaparece: el equivalente es
  `ClientUtil.shouldBeFirstPersonPass()`.
* `PreparableReloadListener#reload` cambia de firma (`SharedState`); como
  `ResourceManagerReloadListener` ya trae una implementación por defecto, el `reload` propio solo se
  compila en `< 1.21.11`.

### Cambios de vanilla en 1.21.11

* `Camera#setup` recibe `Level` en vez de `BlockGetter`; `getPosition()` → `position()`; los vectores de
  dirección pasan a ser `Vector3fc` **inmutables** (`forwardVector()` / `upVector()` / `leftVector()`), así
  que el roll animado se escribe a través de `@Accessor` sobre los campos `forwards` / `up` / `left`
  (`CameraAccessor`). `setDetached` ya no existe (queda solo para `< 1.21.11`).
* `Player#sendSystemMessage` desaparece en el cliente → `displayClientMessage(component, false)`.
* `PlayerSkin#texture()` → `getSkin().body().texturePath()`.
* El ítem en primera persona se envía con `ItemStackRenderState#submit(PoseStack, SubmitNodeCollector, int, int, int)`,
  que es el nuevo target de los mixins de `renderItem` (`ItemRenderer#renderStatic` en 1.21.1).

### Estado del port

| Versión | fabric | neoforge |
| --- | --- | --- |
| 1.21.1 | ✅ build + cliente 7/7 | ✅ build + cliente 7/7 |
| 1.21.11 | ✅ build + cliente 7/7 | ✅ build + cliente 7/7 |
| 26.1.2 | ✅ build + cliente 7/7 | ✅ build + cliente 7/7 |
| 26.2 | ✅ build + cliente 7/7 | ✅ build + cliente 7/7 |
| 26.3 | ✅ build + cliente 7/7 | ✅ build + cliente 7/7 |

Las diez variantes se compilan y empaquetan con `./gradlew :<version>-<loader>:buildAndCollect`, que
deja el jar, el `sources` y el `javadoc` en `build/libs/<version>/`. "cliente 7/7" es `runClient` en el
entorno de desarrollo llegando a la pantalla de título (`Sound engine started` en el log) con los siete
mixins del addon aplicados a los targets de esa versión; las diez están comprobadas así.

### Cambios de PAL 1.2.x (26.x)

* `PlayerAnimBone` guarda la transformación en **vectores públicos de JOML** (`position`, `rotation`,
  `scale`) y ya no tiene `getPosX()` / `setRotX(...)`. Los *getters* se resuelven con reemplazos de
  texto (`getPosX()` → `position.x`, ...) y los dos únicos sitios que escribían por eje
  (`FpRenderState#copy` / `#lerp` y `FpGeoModel.Bone#animatedTransform`) usan `//?` con
  `position.set(...)`.
* `AnimationStack#get3DTransform` **rellena** el bone recibido en vez de devolver uno nuevo. Como el
  nombre del bone es la clave de búsqueda, `FpRenderState#get3DTransform(manager, name)` centraliza la
  diferencia y el resto del código no cambia.
* `get3DTransformRaw` sigue devolviendo el bone, así que `FpAnimationController` no cambia.

### Cambios de vanilla en 26.x

* `MultiBufferSource` **desaparece en 26.2** → los imports solo se emiten para `<1.21.11` (el
  `SubmitNodeCollector` es el único pipeline desde 1.21.11).
* `Camera#setup` ya no existe: la cámara se vuelca a `CameraRenderState` con
  `extractRenderState(CameraRenderState, float)` (26.1.2/26.2) o
  `extractRenderState(CameraRenderState, DeltaTracker)` (26.3), así que `CameraMixin` inyecta ahí a
  HEAD y el resto de `FpCameraUtil` (posiciones y vectores del `Camera`) sigue igual.
* `RenderTypes.entityCutoutNoCull` → `RenderTypes.entityCutout` (el antiguo `entityCutout` pasó a
  `entityCutoutCull`).
* `Player#sendSystemMessage` vuelve (1.21.11 lo había renombrado a `displayClientMessage`), así que la
  rama de chat es `>=1.21.11 && <26.1.2`.
* `PoseStack#mulPose(Quaternionfc)` → `rotate(Quaternionfc)` en 26.3 (reemplazo de texto; el código FP
  solo pasa cuaterniones).
* NeoForge 21.11 sustituye `RegisterClientReloadListenersEvent` por
  `AddClientReloadListenersEvent#addListener(Identifier, listener)`.

### Cambios de vanilla en 26.3

26.3 reemplaza `ItemInHandRenderer` por **`FirstPersonHandsAndItemsRenderer`** y mueve todo el pase a
render states:

| 1.21.11 – 26.2 | 26.3 |
| --- | --- |
| `renderHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int)` | `submitHandsWithItems(float, PoseStack, SubmitNodeCollector, PlayerRenderState, FirstPersonHandsAndItemsRenderState)` |
| `renderItem(LivingEntity, ItemStack, ItemDisplayContext, PoseStack, SubmitNodeCollector, int)` | `submitArmWithItem(PlayerRenderState, FirstPersonHandsAndItemsRenderState, float, float, InteractionHand, float, ItemStack, float, PoseStack, SubmitNodeCollector, int)` |
| `renderPlayerArm(..., HumanoidArm)` | `renderPlayerArm(..., HumanoidArm, PlayerRenderState)` |
| `tick()` (alturas de las manos) | no existe: viven en `FirstPersonHandsAndItemsRenderState` |
| `Camera#extractRenderState(CameraRenderState, float)` | `Camera#extractRenderState(CameraRenderState, DeltaTracker)` |

El port resuelve cada punto así:

* **`@Mixin` y nombres de método**: reemplazos declarativos (`ItemInHandRenderer.class` →
  `FirstPersonHandsAndItemsRenderer.class`, `method = "renderHandsWithItems"` →
  `method = "submitHandsWithItems"`, `method = "renderItem"` → `method = "submitArmWithItem"`). El
  target del ítem (`ItemStackRenderState#submit`) no cambia, así que las anotaciones `@At` se comparten.
* **Firmas**: cada pipeline tiene sus propios handlers (`//? if >=26.3`, `>=1.21.11 && <26.3`,
  `<1.21.11`) que delegan en helpers `@Unique` compartidos (`playerAnimLib$pushFpItem` /
  `playerAnimLib$popFpItem`), de modo que el cuerpo del render se escribe una sola vez.
* **Mano derecha/izquierda**: `submitArmWithItem` ya no recibe el `ItemDisplayContext`; el handler
  replica cómo vanilla resuelve el brazo (`hand == MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite()`).
* **Luz**: sale de `PlayerRenderState#avatarRenderState.lightCoords` (antes era un parámetro).
* **Ocultar un ítem**: cancelar `submitArmWithItem` saltaría su `popPose`, así que en 26.3
  `ItemInHandRendererMixin` vacía el `ItemStackRenderState` de la mano afectada en el HEAD de
  `submitHandsWithItems` (`clear()` deja el render state sin geometría y el pose stack balanceado).
* **`tick`**: la rama `<26.3` se queda como estaba; en 26.3 el pase se cancela entero en el HEAD de
  `submitHandsWithItems`, que es lo que el `tick` preparaba.
* **`PoseStack#mulPose(Quaternionfc)`** → `rotate(Quaternionfc)`, y la cámara pasa a recibir un
  `DeltaTracker` (`//? if >=26.3`), del que se extrae el partial tick con
  `getGameTimeDeltaPartialTick(true)`.

### The held item and the transforms vanilla applies to it

`FpArmRendererMixin` keeps vanilla's item pass and applies the animation's `fp_player_arms` and
`fp_r_item` / `fp_l_item` bones around it, so the held item follows the clip without a second,
hand-rolled item render (which produced a black item). Vanilla builds a whole chain of transforms for a
first-person item, none of which belongs to the clip, so the addon rewinds the pose to the frame the
vanilla hand pass started from (`FpRenderState#captureItemFrame` / `#restoreItemFrame`, taken at the
head of `ItemInHandRenderer#renderHandsWithItems`) and re-applies only the anchor the arms use:

| Transform | Where it comes from | What the addon does |
| --- | --- | --- |
| hand sway `rotateX((viewXRot - xBob) * 0.1)` / `rotateY((viewYRot - yBob) * 0.1)` | `ItemInHandRenderer#renderHandsWithItems`, before the per-hand passes | dropped by the rewind, so the rig stays rigid to the camera like the preview |
| equip progress (`-0.52F + equip * -0.6F`) and the lateral hand anchor `±0.56` | `ItemInHandRenderer#applyItemArmTransform`, pushed **before** `renderItem` | dropped by the rewind; the camera anchor `(0, -0.52, -0.72)` (or the hand anchor with `fpDisplayAnchor: "hand"`) is applied instead, the same frame the arms use |
| swing (`translate(i * s, r, l)` and `applyItemArmAttackTransform`) | `ItemInHandRenderer#renderArmWithItem` | dropped by the rewind; the clip animates the swing itself |
| use-item poses (bow, crossbow, trident, spear, food, shield, brush, spyglass) | `ItemInHandRenderer#renderArmWithItem` | dropped by the rewind (a held map keeps its own renderer, which bypasses `renderItem`) |
| item display transform `rotationXYZ(0, -90, 25)`, `scale(0.68)`, `translate(i * 1.13, 3.2, 1.13)` | `ItemTransform#apply` for the `firstperson_righthand` / `firstperson_lefthand` context, applied **after** the injected bone pose | skipped by `ItemTransformMixin` while the item bone pose is up |

With the chain gone the item lands exactly where the `fp_*_item` bone puts it, in the same frame as the
arms, which is what Blockbench previews. The item model's `1×1×1` box is centred on the bone origin
(vanilla's `translate(-0.5, -0.5, -0.5)` inside `ItemRenderer#render`), so a Blockbench placeholder for
the item has to be centred on the bone origin as well. The arms get there by being drawn on the bone
pivot, which `applyBoneTransform`'s un-pivot maps onto the bone origin; the item model is drawn around
the frame origin instead, so `applyItemTransform` translates it to the pivot before rotating. A rig with
`fpDisplayAnchor: "hand"` keeps the model-origin convention (its item bone passes `NO_PIVOT`), because
there the item *is* the model origin.

In the model-origin convention the item bone is a child of the arm bone, in the model and in the
renderer:

```
fp_player_arms
  rightArm  -> cube rightArm, fp_r_item -> cube rightItem
  leftArm   -> cube leftArm,  fp_l_item -> cube leftItem
fp_camera
```

`FpArmRendererMixin` applies the arm bone before the item bone in the item pass, so rotating the hand
drags the item with it, exactly like the Blockbench outliner shows. The hand-anchored convention keeps
the item bone independent, because there the item is the rig centre and the arms are placed around it.

`ItemTransformMixin` cancels `ItemTransform#apply` only for the frames in which
`FpArmRendererMixin` actually pushed an item bone pose (`FpRenderState.isFpItemPass`), so the
inventory, dropped items, other players and the hand of a clip that does not animate that item all
keep vanilla's transform. The mixin targets `ItemTransform` in
`net.minecraft.client.renderer.block.model` up to 1.21.11 and in
`net.minecraft.client.resources.model.cuboid` from 26.1.2 on, and the `apply` signature changed from
`(boolean, PoseStack)` to `(boolean, PoseStack.Pose)` one version earlier, in 1.21.11. Both are `//?`
blocks, one per thing, because the package and the signature do not move together.

### Los targets de mixin solo se validan en tiempo de ejecución

Compilar no valida un mixin. Un `@Mixin` de una clase que existe compila igual aunque el método, el
descriptor de una llamada o el tipo de un parámetro del handler no existan en esa versión: el error
sale al arrancar el cliente. Y como Mixin aplica los mixins en orden y aborta en el primero que falla,
**solo se ve un error por arranque**, así que hay que arreglar y volver a arrancar.

Tres reglas que costaron un arranque cada una:

1. **Los parámetros del handler de `@Inject` se comparan por tipo exacto.** Un supertipo no vale:
   `CallbackInjector$Callback#checkDescriptor` exige igualdad de tipos o `@Coerce`, así que
   `BlockGetter` no acepta un target cuyo parámetro es `Level`. Por eso `CameraMixin` declara `Level`
   desde 1.21.11 y `BlockGetter` hasta 1.21.1. También vale para el caso contrario: un handler que
   solo declara `(CallbackInfo)` sí es válido para cualquier target (Mixin lo compara con
   `getSimpleCallbackDescriptor`), y de ahí que las inyecciones de `tick` funcionen.
2. **Un bloque `//?` activo descomenta los `//` que hay dentro.** Es lo que hace que la rama activa
   salga limpia, pero significa que un comentario `//` dentro de la rama activa acaba en el fuente
   generado y rompe la compilación (`Camera#setup` → `illegal character: '#'`). Para comentar dentro
   de una rama activa hay que usar `/^ ... ^/`.
3. **Un `@At` INVOKE necesita el descriptor exacto de la llamada**, con los genéricos ya borrados
   (`Holder`, no `Holder<SoundEvent>`) y el tipo real del receptor.

Targets que cambian según la versión:

| Elemento | ≤1.21.1 | 1.21.11 | 26.1.2 | 26.2 | 26.3 |
| --- | --- | --- | --- | --- | --- |
| `Level.playSound` de los keyframes de sonido | `(Player, DDD, SoundEvent, SoundSource, FF)` | `(Entity, DDD, Holder, SoundSource, FF)` | = | = | = |
| `ItemTransform` | `client.renderer.block.model`, `apply(boolean, PoseStack)` | mismo paquete, `apply(boolean, PoseStack.Pose)` | `client.resources.model.cuboid`, `apply(boolean, PoseStack.Pose)` | = | = |
| `Camera` | `setup(BlockGetter, Entity, boolean, boolean, float)` | `setup(Level, Entity, boolean, boolean, float)` | `extractRenderState(CameraRenderState, float)` | = | `extractRenderState(CameraRenderState, DeltaTracker)` |
| entrada de la pasada first-person | `ItemInHandRenderer#renderHandsWithItems` | = | = | `ItemInHandRenderer#submitHandsWithItems` | `FirstPersonHandsAndItemsRenderer#submitHandsWithItems` |
| entrada por ítem | `ItemInHandRenderer#renderItem` + `ItemRenderer#renderStatic` | `renderItem` + `ItemStackRenderState#submit` | = | = | `submitArmWithItem` + `ItemStackRenderState#submit` (dos veces) |

Los renombrados de 26.2 y 26.3 se aplican con dos `replacements.string` separados
(`>= "26.2"` para `renderHandsWithItems` → `submitHandsWithItems`, `>= "26.3"` para la clase y
`renderItem` → `submitArmWithItem`), porque 26.2 renombra la entrada de la pasada pero mantiene
`renderItem` como entrada pública por ítem.

`replacements.string` de Stonecutter se aplica en **ambos sentidos**: cuando la condición de la versión
no se cumple, el par se usa al revés. Un `replace("PlayerModel<?>", "PlayerModel")` pensado para
≥1.21.11 se convierte en `PlayerModel` → `PlayerModel<?>` al generar para 1.21.1, y eso reescribe
también `PlayerModelPart`, produciendo `PlayerModel<?>Part`. Por eso el genérico se cambia con un
bloque `//?` en el código y no con un reemplazo.

### El cliente de NeoForge ≥1.21.11 moría antes de los mixins

`build.neoforge.gradle.kts` fijaba las librerías del classpath de desarrollo con `force(...)`
(`gson:2.10`, `netty-buffer`/`netty-common:4.1.118.Final`, `fastutil:8.5.12`, `slf4j-api:2.0.13`,
`joml:1.10.8`). En 1.21.1 esos pines arreglan conflictos reales, pero desde 1.21.11 Minecraft declara
sus librerías con `strictly` (gson 2.13.2, netty 4.2.7.Final, fastutil 8.5.18, slf4j-api 2.0.17) y un
`force` gana sobre `strictly`: el juego arrancaba con gson 2.10, que no tiene
`com.google.gson.Strictness` (llegó en gson 2.11).

```
java.lang.NoClassDefFoundError: com/google/gson/Strictness
    at net.minecraft.util.GsonHelper.fromJson(GsonHelper.java:426)
    at net.minecraft.DetectedVersion.tryDetectVersion(DetectedVersion.java:56)
    at net.minecraft.SharedConstants.tryDetectVersion(SharedConstants.java:193)
```

En el servidor (`runServer`) se ve la excepción y el proceso sale con código 1. En el cliente la
excepción se pierde detrás de la pantalla de carga de FML: el log se queda en `Closing FML Loader` /
`Clearing ModLoader`, la ventana dice "Minecraft: NeoForge Loading..." y el proceso termina saliendo
con código 1 sin ningún error. La pista está en que **no se aplica ni un mixin** (solo
`Preparing modtemplate.mixins.json (14)`): el fallo es anterior a la fase de mixins, así que ningún
`@Mixin` puede ser la causa.

Los pines ahora solo se aplican a `< 1.21.11`, que es donde hacen falta:

```kotlin
if (stonecutter.current.parsed < "1.21.11") {
	force("com.google.code.gson:gson:2.10")
	force("io.netty:netty-buffer:4.1.118.Final")
	force("io.netty:netty-common:4.1.118.Final")
	force("it.unimi.dsi:fastutil:8.5.12")
	force("org.slf4j:slf4j-api:2.0.13")
	force("org.joml:joml:1.10.8")
}
```

Con eso la resolución de 26.1.2 queda en `gson:2.10 -> 2.13.2`, `netty-buffer:{strictly 4.2.7.Final} ->
4.2.7.Final`, `fastutil:8.5.18` y `slf4j-api:2.0.17`, y el cliente arranca. Para comprobarlo:

```bash
./gradlew :<version>-neoforge:dependencies --configuration runtimeClasspath | grep -E 'gson|netty|fastutil|slf4j'
```

Quitar los pines no basta en 26.2: PAL 1.2.4 pide `slf4j-api:2.0.18` y `joml:1.10.9`, que no encajan con
las versiones `strictly` de esa versión de Minecraft, y el **build** falla con "Cannot find a version of
'org.slf4j:slf4j-api' that satisfies the version constraints". Como el juego ya trae esas librerías, se
excluyen del grafo de PAL (solo en el classpath de desarrollo; el jar publicado sigue igual):

```kotlin
implementation("com.zigythebird.playeranim:PlayerAnimationLibNeo:...") {
	if (stonecutter.current.parsed >= "1.21.11") {
		exclude(group = "com.google.code.gson")
		exclude(group = "io.netty")
		exclude(group = "it.unimi.dsi")
		exclude(group = "org.slf4j")
		exclude(group = "org.joml")
	}
}
```

Así gana siempre la versión de Minecraft sin fijar ninguna a mano: 26.1.2/1.21.11 usan gson 2.13.2 y
26.2 usa gson 2.14.0.

Fabric no tenía este problema porque `build.fabric.gradle.kts` solo fuerza
`net.fabricmc:fabric-loader`, y de ahí que las cinco variantes de fabric arrancaran desde el principio.

### El proyecto activo y el árbol generado

`stonecutter { create(rootProject) { vcsVersion = "1.21.1-fabric" } }` hace que la versión **activa**
compile el `src/main/java` del repositorio **directamente**, sin pasar por `//?`. Con código
condicional eso significa compilar las ramas de todas las versiones a la vez, y falla en cuanto una
rama importa una clase que la versión activa no tiene. Por eso los dos build scripts apuntan el
source set `main` al árbol generado:

```kotlin
sourceSets["main"].java.setSrcDirs(
    listOf(layout.buildDirectory.dir("generated/stonecutter/main/java").get().asFile)
)
```

Todas las versiones (incluida la activa) compilan así su `versions/<v>/build/generated/stonecutter`.
Si los errores de compilación vuelven a señalar `src\main\java\...` en lugar de
`versions\<v>\build\generated\stonecutter\...`, es que esa línea se ha perdido.

Para comprobar qué árbol está usando cada proyecto, `tools/diag-srcset.gradle` es un *init script* que
imprime los `srcDirs` de todas las variantes sin tocar el proyecto:

```bash
./gradlew --init-script tools/diag-srcset.gradle :1.21.1-fabric:help
```

### Javadoc multi-versión

`buildAndCollect` depende de `build`, así que el `javadoc` de cada variante tiene que pasar. Los
`{@link}` que apuntan a clases que solo existen en algunas versiones (`Camera#setup`,
`MultiBufferSource`, `RenderType#entitySolid(ResourceLocation)`) fallan con `reference not found` en
las demás, y los `<algo>` sueltos en la prosa se leen como etiquetas HTML (`unknown tag`). Se
arreglaron usando `{@code}` en esos casos en lugar de desactivar `doclint`, de modo que la
documentación se sigue validando.

## Orden de ejecución

1. **Configuración del addon** — ✅ hecho: dependencia de PAL en los dos build scripts (`mavenLocal()`
   incluido), la dep que faltaba de `[fabric."26.2"]`, y versión activa `1.21.1-fabric`.
2. **Renombrado** — ❌ descartado a propósito: el id y el paquete se quedan como están
   (`modtemplate` / `com.example.modtemplate`), y los ficheros copiados viven bajo ese paquete.
3. **Copia** — ✅ hecho y verificado.
4. **Adaptación** — ✅ hecho (los 8 puntos aplicados; el detalle, en la sección de estado):
   1. `modtemplate.mixins.json`: registrar `mixin.firstPerson.FpArmRendererMixin`,
      `mixin.firstPerson.ItemInHandRendererMixin`, `mixin.CameraMixin`, `mixin.CameraAccessor` y los
      mixins nuevos (`AbstractClientPlayerFpMixin`, `AutoPlayingSoundKeyframeHandlerMixin`), con
      `//? fabric` / `//? neoforge` para los específicos de loader.
   2. `FpAnimationController`: reportar `FirstPersonMode.NONE` en lugar de `PLAYER_ARM` y usar un flag
      propio (`isPlayerArmMode()`) para los chequeos internos.
   3. Sustituir `PlayerAnimLibMod.LOGGER` por `ModTemplate.LOGGER`.
   4. Interfaz `IFpAnimatedPlayer` + mixin sobre `AbstractClientPlayer` (captura del controlador FP y
      `FpAnimationSystem.updateRenderState` en `tick`).
   5. Mixin sobre `AutoPlayingSoundKeyframeHandler` (pasar el `LocalPlayer` a `playSound`).
   6. Init del sistema FP + registro del reload listener y de los comandos en los entrypoints del
      addon.
   7. Revisar `FpArmRendererMixin` / `ItemInHandRendererMixin`: quitar lo que ya hace PAL y dejar solo
      lo específico del modo brazo en primera persona.
   8. `FpAnimationResources`: comprobar que carga desde el namespace del addon
      (`assets/modtemplate/fp_animations`) sin tocar PAL.
5. **Compilar** `1.21.1-fabric` (`./gradlew :1.21.1-fabric:build`) y probar en el cliente (`/fp_anim`).
6. **Repetir** para `1.21.1-neoforge`.
7. **Portar** al resto de versiones con Stonecutter (fase 2).


## Notas de build

* **Si `compileJava` empieza a dar errores de símbolos que deberían existir** (p. ej.
  `cannot find symbol: class Avatar` o `package net.minecraft.client.renderer.rendertype does not
  exist` en 1.21.1), mira la ruta del error: si apunta a `src\main\java\...` en lugar de
  `versions\<version>\build\generated\stonecutter\...`, Gradle está compilando el **fuente crudo**
  (con los `//?` sin procesar) porque el estado de `stonecutterGenerate` quedó inconsistente. Se
  arregla borrando `build/` de la raíz y los `build/` de las versiones y volviendo a compilar (o
  `./gradlew <version>:stonecutterGenerate --rerun-tasks`). Los bloques `//?` inactivos están
  comentados, así que el fuente crudo equivale a "todas las ramas activas a la vez" y solo compila por
  casualidad en las versiones nuevas.
* Verificado: la API FP **sí está en PAL publicado** para 1.21.1 (`1.1.4+mc.1.21.1` compila el código
  del addon sin errores). Solo si se toca el API de PAL hará falta publicarlo en local:

  ```bash
  cd PlayerAnimationLibrary && ./gradlew publishToMavenLocal
  ```

  y apuntar `deps.player_animation_library` de la versión en curso a la versión local
  (`1.1.6+mc.1.21.1` según `mod_version` en el `gradle.properties` de PAL). `mavenLocal()` ya está
  añadido a los dos build scripts de este addon.
* `com.zigythebird.playeranim:PlayerAnimationLibFabric` y `:PlayerAnimationLibNeo` son los
  artefactos publicados en `https://repo.redlance.org/public`, ya presente en los repositorios.
