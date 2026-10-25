package com.github.razorplay01.fp_pal.mixin.firstPerson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.github.razorplay01.fp_pal.firstperson.FpArmRenderUtil;
import com.github.razorplay01.fp_pal.firstperson.FpModelRenderer;
import com.github.razorplay01.fp_pal.firstperson.FpRenderBuffer;
import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
//? if >=26.3 {
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//?} else {
//import net.minecraft.client.renderer.ItemInHandRenderer;
//?}
//? if <1.21.11 {
//import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.LivingEntity;
//? if >=26.3 {
import net.minecraft.world.InteractionHand;
//?}
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla bare arm with the animated first-person arms while a first-person animation is
 * playing (see {@code FpAnimationController#isPlayerArmMode()}), and applies the animation's item bone
 * pose to the held items.
 * <p>
 * Arm rendering is a 1:1 port of AzureLib's {@code AzItemArmRenderUtil}. By default the rig is
 * rendered in a camera-centred frame (the {@code player_arms_import.bbmodel} convention, arms at
 * {@code x = ±6}); a clip with {@code fpDisplayAnchor: "hand"} keeps the AzureLib hand frame instead.
 * Only the arms the active clip animates are drawn.
 * <p>
 * The held item is deliberately <b>not</b> drawn here: it keeps using vanilla's item renderer, and
 * this mixin only applies the {@code fp_r_item} / {@code fp_l_item} bone pose around that call after
 * rewinding to the frame the vanilla hand pass started from.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FpArmRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private boolean playerAnimLib$fpItemPushed;

    /**
     * Vanilla only renders the arm model when the hand is empty, so cancelling this hides the vanilla
     * arm without affecting held items.
     */
    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void playerAnimLib$hideVanillaArm(PoseStack poseStack,
                                              //? if >=1.21.11 {
                                              SubmitNodeCollector buffer,
                                              //?} else {
                                              //MultiBufferSource buffer,
                                              //?}
                                              int combinedLight, float equippedProgress, float swingProgress, HumanoidArm arm,
                                              //? if >=26.3 {
                                              PlayerRenderState playerState,
                                              //?}
                                              CallbackInfo ci) {
        boolean playerAnimLib$playerArmMode = FpRenderState.isPlayerArmMode(this.minecraft.player);
        if (playerAnimLib$playerArmMode) {
            ci.cancel();
        }
    }

    /**
     * Draws the animated first-person rig, and remembers the frame the vanilla hand pass starts from so
     * the held item's pass can rewind to it.
     * <p>
     * The HEAD injection is on purpose: from 1.21.9 a submitted model part is rasterised by the
     * collector at the end of {@code renderHandsWithItems}, so drawing at the TAIL handed the rig to
     * the next frame's flush, outside the first-person pass.
     */
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void playerAnimLib$renderFpArms(float partialTicks, PoseStack poseStack,
                                            //? if >=1.21.11 {
                                            SubmitNodeCollector buffer,
                                            //?} else {
                                            //MultiBufferSource.BufferSource buffer,
                                            //?}
                                            //? if >=26.3 {
                                            PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                            //?} else {
                                            //LocalPlayer localPlayer, int combinedLight,
                                            //?}
                                            CallbackInfo ci) {
        // Captured before any early return below can skip it: the item pass rewinds to this frame.
        FpRenderState.captureItemFrame(poseStack);

        //? if >=26.3 {
        // 26.3 has no player or packed light parameter: both live in the player render state.
        LocalPlayer localPlayer = this.minecraft.player;
        AvatarRenderState avatarRenderState = playerState.avatarRenderState;
        if (avatarRenderState == null) return;
        int combinedLight = avatarRenderState.lightCoords;
        //?}
        if (!FpRenderState.isPlayerArmMode(localPlayer)) return;
        if (!this.minecraft.options.getCameraType().isFirstPerson()) return;

        FirstPersonConfiguration config = FpRenderState.firstPersonConfiguration(localPlayer);
        boolean rightArmBone = FpRenderState.hasRightArmBone();
        boolean leftArmBone = FpRenderState.hasLeftArmBone();

        AvatarRenderer playerRenderer = (AvatarRenderer) this.minecraft.getEntityRenderDispatcher().getRenderer(localPlayer);
        //? if >=1.21.11 {
        PlayerModel playerModel = (PlayerModel) playerRenderer.getModel();
        //?} else {
        //PlayerModel<?> playerModel = (PlayerModel<?>) playerRenderer.getModel();
        //?}
        //? if >=1.21.11 {
        Identifier skin = localPlayer.getSkin().body().texturePath();
        //?} else {
        //Identifier skin = localPlayer.getSkin().texture();
        //?}
        // Hides the MultiBufferSource / SubmitNodeCollector difference between the render pipelines.
        FpRenderBuffer render = new FpRenderBuffer(buffer);
        boolean leftHandAnchor = localPlayer.getMainArm() == HumanoidArm.LEFT;

        // The parts are drawn directly (not through AvatarRenderer#renderHand), so the shared model
        // still has the visibility flags of the last player it rendered; refresh them.
        playerModel.leftArm.visible = true;
        playerModel.rightArm.visible = true;
        playerModel.leftSleeve.visible = localPlayer.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        playerModel.rightSleeve.visible = localPlayer.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);

        poseStack.pushPose();
        // fp_camera is applied to the real Camera by CameraMixin; applying it here too would double it.

        // Back to the frame the vanilla hand pass started from: vanilla's hand sway is not animated and
        // would make the rig drift while the camera turns.
        FpRenderState.restoreItemFrame(poseStack);

        // The rig frame: camera-centred by default, or the AzureLib hand frame with fpDisplayAnchor "hand".
        if (FpRenderState.isHandAnchored()) {
            FpArmRenderUtil.applyHandTransform(poseStack, leftHandAnchor);
        } else {
            FpArmRenderUtil.applyCameraAnchor(poseStack);
        }
        FpArmRenderUtil.applyArmDisplayTransform(poseStack, FpRenderState.ARM_DISPLAY_TRANSLATION, FpRenderState.ARM_DISPLAY_SCALE);
        FpArmRenderUtil.applyRootTransform(poseStack, FpRenderState.fpPlayerArms, FpRenderState.fpRootPivot);

        // Optional preview of the AzureLib item model that owns the rig (see /fp_anim model), rendered
        // in the same frame as the arms. Arms the clip does not animate are skipped completely.
        if (config.isShowRightArm() && rightArmBone) {
            FpArmRenderUtil.renderArmForBone(poseStack, FpRenderState.fpRightArm, FpArmRenderUtil.RIGHT_ARM_PIVOT, playerModel, skin, combinedLight, render);
        }

        if (config.isShowLeftArm() && leftArmBone) {
            FpArmRenderUtil.renderArmForBone(poseStack, FpRenderState.fpLeftArm, FpArmRenderUtil.LEFT_ARM_PIVOT, playerModel, skin, combinedLight, render);
        }

        FpModelRenderer.render(poseStack, render, combinedLight);

        poseStack.popPose();
    }

    /**
     * Poses the held item with the animation's {@code fp_*_item} bone (and the animated model root) and
     * nothing else. Vanilla's item transform chain (hand sway, swing, equip progress, anchor, use pose)
     * is dropped at once by rewinding to the frame the vanilla pass started from; the anchor the arms
     * use is re-applied so the item stays in the same frame as the hands.
     */
    @Unique
    private void playerAnimLib$pushFpItem(Entity entity, boolean right, PoseStack poseStack) {
        if (!FpRenderState.isPlayerArmMode(entity) || !this.minecraft.options.getCameraType().isFirstPerson()) return;

        // An unanimated hand keeps vanilla's item transform, so there is nothing to apply here.
        if (!FpRenderState.hasArmBone(right) && !FpRenderState.hasItemBone(right)) {
            return;
        }
        // A hidden item's render is cancelled by ItemInHandRendererMixin, so do not push a pose that
        // will never be popped.
        FirstPersonConfiguration config = FpRenderState.firstPersonConfiguration(entity);
        if (right ? !config.isShowRightItem() : !config.isShowLeftItem()) return;

        poseStack.pushPose();
        // Drop every vanilla transform applied before this point (sway, equip progress, swing, use pose).
        FpRenderState.restoreItemFrame(poseStack);
        // The anchor the arm pass uses, so the item and the hands share one frame.
        if (FpRenderState.isHandAnchored()) {
            boolean leftHandAnchor = entity instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT;
            FpArmRenderUtil.applyHandTransform(poseStack, leftHandAnchor);
        } else {
            FpArmRenderUtil.applyCameraAnchor(poseStack);
        }
        // Same display transform as the arm pass, so hands and items move together.
        FpArmRenderUtil.applyArmDisplayTransform(poseStack, FpRenderState.ARM_DISPLAY_TRANSLATION, FpRenderState.ARM_DISPLAY_SCALE);
        FpArmRenderUtil.applyRootTransform(poseStack, FpRenderState.fpPlayerArms, FpRenderState.fpRootPivot);
        // In this convention the item bone is a child of the arm bone, so the arm drags the item with it.
        // The hand-anchored convention keeps the item on the model origin instead.
        if (!FpRenderState.isHandAnchored()) {
            FpArmRenderUtil.applyArmTransform(poseStack, right ? FpRenderState.fpRightArm : FpRenderState.fpLeftArm,
                    right ? FpArmRenderUtil.RIGHT_ARM_PIVOT : FpArmRenderUtil.LEFT_ARM_PIVOT);
        }
        FpArmRenderUtil.applyItemTransform(poseStack, right ? FpRenderState.fpRightItem : FpRenderState.fpLeftItem,
                FpRenderState.isHandAnchored() ? FpArmRenderUtil.NO_PIVOT
                        : (right ? FpArmRenderUtil.RIGHT_ITEM_PIVOT : FpArmRenderUtil.LEFT_ITEM_PIVOT));
        //? if >=1.21.11 {
        // 1.21.1's ItemRenderer#renderStatic centered the item model with translate(-0.5,-0.5,-0.5);
        // the 1.21.11 item pipeline bakes the quads and no longer does that, so apply it here to keep
        // the held item aligned with Blockbench.
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        //?}
        this.playerAnimLib$fpItemPushed = true;
        FpRenderState.isFpItemPass = true;
    }

    //? if >=26.3 {
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", shift = At.Shift.BEFORE))
    private void playerAnimLib$pushFpItemTransform(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                                   float equipProgress, float swingProgress, InteractionHand hand,
                                                   float swapScale, ItemStack itemStack, float height, PoseStack poseStack,
                                                   SubmitNodeCollector buffer, int combinedLight, CallbackInfo ci) {
        this.playerAnimLib$pushFpItem(this.minecraft.getCameraEntity(),
                playerAnimLib$isRightHand(playerState, hand), poseStack);
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", shift = At.Shift.AFTER))
    private void playerAnimLib$popFpItemTransform(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                                  float equipProgress, float swingProgress, InteractionHand hand,
                                                  float swapScale, ItemStack itemStack, float height, PoseStack poseStack,
                                                  SubmitNodeCollector buffer, int combinedLight, CallbackInfo ci) {
        this.playerAnimLib$popFpItem(poseStack);
    }

    /**
     * Mirrors how {@code submitArmWithItem} resolves the arm of a hand: the main hand item belongs to
     * the avatar's main arm and the off hand item to the opposite one. 26.3 dropped the
     * {@code ItemDisplayContext} the older item pass used to identify the hand with.
     */
    @Unique
    private static boolean playerAnimLib$isRightHand(PlayerRenderState playerState, InteractionHand hand) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        HumanoidArm mainArm = avatar != null ? avatar.mainArm : HumanoidArm.RIGHT;
        return (hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite()) == HumanoidArm.RIGHT;
    }
    //?}

    //? if >=1.21.11 && <26.3 {
    /*// HEAD/RETURN of renderItem on purpose: on 1.21.11 renderItem first bakes the item through
    // ItemModelResolver#updateForTopItem, where the display transform is applied, so injecting around
    // submit() would leave that bake outside the animated frame. Wrapping the whole method keeps the
    // pose balanced even if submit() is skipped.
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void playerAnimLib$pushFpItemTransform(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                                   PoseStack poseStack, SubmitNodeCollector buffer, int seed, CallbackInfo ci) {
        this.playerAnimLib$pushFpItem(entity, displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND, poseStack);
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void playerAnimLib$popFpItemTransform(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                                  PoseStack poseStack, SubmitNodeCollector buffer, int seed, CallbackInfo ci) {
        this.playerAnimLib$popFpItem(poseStack);
    }
    *///?}

    //? if <1.21.11 {
    /*@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V", shift = At.Shift.BEFORE))
    private void playerAnimLib$pushFpItemTransform(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                                   boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int seed,
                                                   CallbackInfo ci) {
        this.playerAnimLib$pushFpItem(entity, displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND, poseStack);
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V", shift = At.Shift.AFTER))
    private void playerAnimLib$popFpItemTransform(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                                  boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int seed,
                                                  CallbackInfo ci) {
        this.playerAnimLib$popFpItem(poseStack);
    }
    *///?}

    @Unique
    private void playerAnimLib$popFpItem(PoseStack poseStack) {
        if (this.playerAnimLib$fpItemPushed) {
            this.playerAnimLib$fpItemPushed = false;
            FpRenderState.isFpItemPass = false;
            poseStack.popPose();
        }
    }
}
