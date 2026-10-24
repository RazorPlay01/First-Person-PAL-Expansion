package com.github.razorplay01.fp_pal.mixin.firstPerson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.github.razorplay01.fp_pal.firstperson.FpArmRenderUtil;
import com.github.razorplay01.fp_pal.firstperson.FpModelRenderer;
import com.github.razorplay01.fp_pal.firstperson.FpRenderBuffer;
import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
//? if >=26.3 {
/*import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
*///?} else {
import net.minecraft.client.renderer.ItemInHandRenderer;
//?}
//? if <1.21.11 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.11 {
//import net.minecraft.client.renderer.SubmitNodeCollector;
//?}
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.LivingEntity;
//? if >=26.3 {
//import net.minecraft.world.InteractionHand;
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
 * Replaces the vanilla bare arm with the animated first-person arms while a first-person animation
 * is playing (see {@code FpAnimationController#isPlayerArmMode()}), and applies the animation's item
 * and camera bones to the held items and the real view.
 * <p>
 * Arm rendering is a 1:1 port of AzureLib's {@code AzItemArmRenderUtil}: the {@code leftArm} /
 * {@code rightArm} bones of the animation replace the vanilla arm mesh, so a Blockbench rig previews
 * the same way in game. By default the rig is rendered in a camera-centred frame, which is the frame
 * {@code player_arms_import.bbmodel} is authored in (arms at {@code x = ±6} around the model origin),
 * so both hands stay centred on screen instead of being pushed to the right hand. A clip that sets
 * {@code fpDisplayAnchor} to {@code "hand"} keeps the AzureLib item hand frame instead, for gun rigs
 * whose arms are authored around the held item. Only the arms that the active
 * clip actually animates are drawn; a clip that only animates one hand never shows the other hand in
 * its idle pose.
 * <p>
 * The held item is deliberately <b>not</b> drawn here. It keeps using vanilla's item renderer (with
 * its correct lighting, model display transform, foil and glint); this mixin only applies the
 * {@code fp_r_item} / {@code fp_l_item} bone pose around that call. Drawing the item a second time
 * with {@code ItemRenderer#renderStatic} produced a fully black item, because that manual call ran
 * outside the render state vanilla's item pass sets up.
 */
@Mixin(ItemInHandRenderer.class)
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
                                              //SubmitNodeCollector buffer,
                                              //?} else {
                                              MultiBufferSource buffer,
                                              //?}
                                              int combinedLight, float equippedProgress, float swingProgress, HumanoidArm arm,
                                              //? if >=26.3 {
                                              //PlayerRenderState playerState,
                                              //?}
                                              CallbackInfo ci) {
        if (FpRenderState.isPlayerArmMode(this.minecraft.player)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void playerAnimLib$renderFpArms(float partialTicks, PoseStack poseStack,
                                            //? if >=1.21.11 {
                                            //SubmitNodeCollector buffer,
                                            //?} else {
                                            MultiBufferSource.BufferSource buffer,
                                            //?}
                                            //? if >=26.3 {
                                            //PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                            //?} else {
                                            LocalPlayer localPlayer, int combinedLight,
                                            //?}
                                            CallbackInfo ci) {
        //? if >=26.3 {
        /*// 26.3 has neither the player nor the packed light as a parameter: both live in the player
        // render state the first-person pass is built from. submitHandsWithItems already bailed out
        // when there is no avatar render state, so the null check is just belt and braces.
        LocalPlayer localPlayer = this.minecraft.player;
        AvatarRenderState avatarRenderState = playerState.avatarRenderState;
        if (avatarRenderState == null) return;
        int combinedLight = avatarRenderState.lightCoords;
        *///?}
        if (!FpRenderState.isPlayerArmMode(localPlayer)) return;
        if (!this.minecraft.options.getCameraType().isFirstPerson()) return;

        FirstPersonConfiguration config = FpRenderState.firstPersonConfiguration(localPlayer);
        boolean rightArmBone = FpRenderState.hasRightArmBone();
        boolean leftArmBone = FpRenderState.hasLeftArmBone();

        PlayerRenderer playerRenderer = (PlayerRenderer) this.minecraft.getEntityRenderDispatcher().getRenderer(localPlayer);
        //? if >=1.21.11 {
        //PlayerModel playerModel = (PlayerModel) playerRenderer.getModel();
        //?} else {
        PlayerModel<?> playerModel = (PlayerModel<?>) playerRenderer.getModel();
        //?}
        //? if >=1.21.11 {
        //ResourceLocation skin = localPlayer.getSkin().body().texturePath();
        //?} else {
        ResourceLocation skin = localPlayer.getSkin().texture();
        //?}
        // The vanilla arm meshes and the geo model are submitted through this wrapper, which hides the
        // MultiBufferSource / SubmitNodeCollector difference between the two render pipelines.
        FpRenderBuffer render = new FpRenderBuffer(buffer);
        boolean leftHandAnchor = localPlayer.getMainArm() == HumanoidArm.LEFT;

        // We render the parts directly instead of going through PlayerRenderer#renderHand, so the
        // shared model still has the visibility flags of the last player it rendered. Refresh them
        // for the local player before drawing (sleeves depend on the skin parts).
        playerModel.leftArm.visible = true;
        playerModel.rightArm.visible = true;
        playerModel.leftSleeve.visible = localPlayer.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        playerModel.rightSleeve.visible = localPlayer.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);

        poseStack.pushPose();
        // fp_camera is applied to the real Camera by CameraMixin. Applying it here as well would move
        // the arms twice relative to the animated view.

        // The frame of the rig: camera-centred by default, so a rig authored around the model origin
        // (arms at x = ±6) renders centred like in Blockbench. {@code fpDisplayAnchor: "hand"} keeps
        // the AzureLib item hand frame instead, for gun rigs whose arms sit around the item.
        if (FpRenderState.isHandAnchored()) {
            FpArmRenderUtil.applyHandTransform(poseStack, leftHandAnchor);
        } else {
            FpArmRenderUtil.applyCameraAnchor(poseStack);
        }
        FpArmRenderUtil.applyArmDisplayTransform(poseStack, FpRenderState.ARM_DISPLAY_TRANSLATION, FpRenderState.ARM_DISPLAY_SCALE);
        FpArmRenderUtil.applyRootTransform(poseStack, FpRenderState.fpPlayerArms, FpRenderState.fpRootPivot);

        // Optional test preview of the AzureLib item model that owns the rig. It is rendered in the
        // same frame as the arms, so /fp_anim model <id> can be used to compare extra bones (mag,
        // slide, flash...) against Blockbench without a separate item mod.
        // Arms that the active clip does not animate are skipped completely: drawing them here would
        // show the idle pose of a hand that the animation never mentions.
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
     * Makes the held item follow the arm that holds it together with the fp_*_item offset, while the
     * rest of the vanilla first-person item rendering (swing, equip, use animations, maps, crossbows,
     * spyglass...) is left untouched.
     */
    @Unique
    private void playerAnimLib$pushFpItem(Entity entity, boolean right, PoseStack poseStack) {
        if (!FpRenderState.isPlayerArmMode(entity) || !this.minecraft.options.getCameraType().isFirstPerson()) return;

        // An unanimated hand keeps vanilla's item transform, so there is nothing to apply here.
        if (!FpRenderState.hasArmBone(right) && !FpRenderState.hasItemBone(right)) return;
        // If the first-person configuration hides this hand's item, ItemInHandRendererMixin cancels
        // the render. Do not push a pose that the AFTER injection will never pop.
        FirstPersonConfiguration config = FpRenderState.firstPersonConfiguration(entity);
        if (right ? !config.isShowRightItem() : !config.isShowLeftItem()) return;

        poseStack.pushPose();
        // In camera mode the arms are centred on the view axis, so the item has to leave the vanilla
        // right/left hand anchor as well; otherwise it stays in the bottom-right corner while the
        // animated hands are centred. Y and Z are kept, since that is what puts the rig in front of
        // the camera.
        if (!FpRenderState.isHandAnchored()) {
            poseStack.translate(right ? -0.56F : 0.56F, 0.0F, 0.0F);
        }
        // The swing/equip transforms are already part of this frame, so only the animated model root
        // and the item bone are applied. Vanilla's display transform for the item is skipped while this
        // pose is up (see ItemTransformMixin), so the item ends up where the bone puts it.
        FpArmRenderUtil.applyRootTransform(poseStack, FpRenderState.fpPlayerArms, FpRenderState.fpRootPivot);
        FpArmRenderUtil.applyItemTransform(poseStack, right ? FpRenderState.fpRightItem : FpRenderState.fpLeftItem,
                FpRenderState.isHandAnchored() ? FpArmRenderUtil.NO_PIVOT
                        : (right ? FpArmRenderUtil.RIGHT_ITEM_PIVOT : FpArmRenderUtil.LEFT_ITEM_PIVOT));
        this.playerAnimLib$fpItemPushed = true;
        FpRenderState.isFpItemPass = true;
    }

    //? if >=26.3 {
    /*@Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", shift = At.Shift.BEFORE))
    private void playerAnimLib$pushFpItemTransform(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                                   float equipProgress, float swingProgress, InteractionHand hand,
                                                   float swapScale, ItemStack itemStack, float height, PoseStack poseStack,
                                                   SubmitNodeCollector buffer, int combinedLight, CallbackInfo ci) {
        this.playerAnimLib$pushFpItem(this.minecraft.getCameraEntity(),
                playerAnimLib$isRightHand(playerState, hand), poseStack);
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", shift = At.Shift.AFTER))
    private void playerAnimLib$popFpItemTransform(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                                  float equipProgress, float swingProgress, InteractionHand hand,
                                                  float swapScale, ItemStack itemStack, float height, PoseStack poseStack,
                                                  SubmitNodeCollector buffer, int combinedLight, CallbackInfo ci) {
        this.playerAnimLib$popFpItem(poseStack);
    }

    /^*
     * Mirrors how {@code submitArmWithItem} resolves the arm of a hand: the main hand item belongs to
     * the avatar's main arm and the off hand item to the opposite one. 26.3 dropped the
     * {@code ItemDisplayContext} the older item pass used to identify the hand with.
     ^/
    @Unique
    private static boolean playerAnimLib$isRightHand(PlayerRenderState playerState, InteractionHand hand) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        HumanoidArm mainArm = avatar != null ? avatar.mainArm : HumanoidArm.RIGHT;
        return (hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite()) == HumanoidArm.RIGHT;
    }
    *///?}

    //? if >=1.21.11 && <26.3 {
    /*@Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", shift = At.Shift.BEFORE))
    private void playerAnimLib$pushFpItemTransform(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                                   PoseStack poseStack, SubmitNodeCollector buffer, int seed, CallbackInfo ci) {
        this.playerAnimLib$pushFpItem(entity, displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND, poseStack);
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", shift = At.Shift.AFTER))
    private void playerAnimLib$popFpItemTransform(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                                  PoseStack poseStack, SubmitNodeCollector buffer, int seed, CallbackInfo ci) {
        this.playerAnimLib$popFpItem(poseStack);
    }
    *///?}

    //? if <1.21.11 {
    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V", shift = At.Shift.BEFORE))
    private void playerAnimLib$pushFpItemTransform(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                                   boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int seed,
                                                   CallbackInfo ci) {
        this.playerAnimLib$pushFpItem(entity, displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND, poseStack);
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V", shift = At.Shift.AFTER))
    private void playerAnimLib$popFpItemTransform(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                                  boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int seed,
                                                  CallbackInfo ci) {
        this.playerAnimLib$popFpItem(poseStack);
    }
    //?}

    @Unique
    private void playerAnimLib$popFpItem(PoseStack poseStack) {
        if (this.playerAnimLib$fpItemPushed) {
            this.playerAnimLib$fpItemPushed = false;
            FpRenderState.isFpItemPass = false;
            poseStack.popPose();
        }
    }
}