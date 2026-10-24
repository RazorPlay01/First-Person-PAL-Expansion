/*
 * MIT License
 *
 * Copyright (c) 2022 KosmX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.razorplay01.fp_pal.mixin.firstPerson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.util.ClientUtil;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.entity.Entity;
//? if >=26.3 {
//import net.minecraft.world.entity.HumanoidArm;
//?}
import net.minecraft.world.entity.LivingEntity;
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
 * Controls which vanilla first-person item passes are cancelled while a first-person animation is
 * playing. Arms are replaced by the {@code fp_*} rig in {@link FpArmRendererMixin}, and the held items
 * are still drawn by vanilla's own item renderer (only the frame they are drawn in comes from the
 * animation): the only item this mixin hides is one that {@code FirstPersonConfiguration} explicitly
 * asks to hide. In particular, an item in a hand that the active clip does not animate keeps its
 * vanilla transform and stays visible.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    //? if <26.3 {
    @Shadow
    private float mainHandHeight;
    @Shadow
    private float offHandHeight;
    //?}

    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * PAL's own {@code THIRD_PERSON_MODEL} mode (reachable with {@code /fp_anim mode
     * third_person_model}) draws the arms and the items from the third-person model, so the vanilla
     * first-person pass has to be skipped. The addon's own player-arm mode keeps the vanilla item
     * pass: only the arm mesh is replaced there, by {@link FpArmRendererMixin}.
     * <p>
     * PAL only exposes the mode transition through {@code getFirstPersonTransitionProgress()}, which
     * the released PAL API does not have, so the switch is immediate instead of faded.
     */
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void disableDefaultItemIfNeeded(float partialTicks, PoseStack poseStack,
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
        //LocalPlayer localPlayer = this.minecraft.player;
        //?}
        if (localPlayer instanceof IAnimatedPlayer player
                && player.playerAnimLib$getAnimManager().getFirstPersonMode() == FirstPersonMode.THIRD_PERSON_MODEL) {
            ci.cancel();
            return;
        }
        //? if >=26.3 {
        //playerAnimLib$hideConfiguredItems(playerState, handsState);
        //?}
    }

    //? if >=26.3 {
    /*/^*
     * 26.3 builds the held items into render states before the pass runs and has no per-item hook any
     * more: cancelling {@code submitArmWithItem} would also skip the {@code popPose} that closes the
     * pose it pushed. Emptying the render state of a hand the configuration hides submits no geometry
     * for that hand and leaves the pose stack balanced.
     ^/
    @Unique
    private static void playerAnimLib$hideConfiguredItems(PlayerRenderState playerState,
                                                          FirstPersonHandsAndItemsRenderState handsState) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar == null) return;
        Entity entity = Minecraft.getInstance().getCameraEntity();
        // PAL's own mode already cancelled the whole pass above. What is left is PAL's first-person
        // pass and the addon's own player-arm mode, which PAL does not know about.
        if (!ClientUtil.shouldBeFirstPersonPass() && !FpRenderState.isPlayerArmMode(entity)) return;
        FirstPersonConfiguration config = FpRenderState.firstPersonConfiguration(entity);
        // The main hand render state belongs to the avatar's main arm and the off hand one to the
        // opposite arm, so the configuration's right/left hand has to be mapped through it.
        boolean mainHandIsRight = avatar.mainArm == HumanoidArm.RIGHT;
        if (!(mainHandIsRight ? config.isShowRightItem() : config.isShowLeftItem())) {
            handsState.mainHandRenderState.clear();
        }
        if (!(mainHandIsRight ? config.isShowLeftItem() : config.isShowRightItem())) {
            handsState.offHandRenderState.clear();
        }
    }
    *///?}

    //? if <26.3 {
    @Inject(method = "tick", at = @At("TAIL"))
    private void disableDefaultItemIfNeeded(CallbackInfo ci) {
        LocalPlayer localPlayer = this.minecraft.player;
        if (localPlayer instanceof IAnimatedPlayer player) {
            if (player.playerAnimLib$getAnimManager().getFirstPersonMode() == FirstPersonMode.THIRD_PERSON_MODEL) {
                // Hand height doesn't have to be 0 for it to be off-screen: the hands are off-screen
                // at around 0.85, which is where PAL's own transition ends up. 26.3 cancels the whole
                // pass instead, because its renderer has no tick any more.
                this.mainHandHeight *= 0.4f;
                this.offHandHeight *= 0.4f;
            }
        }
    }
    //?}

    //? if >=1.21.11 && <26.3 {
    /*@Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"), cancellable = true)
    private void cancelItemRender(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                  PoseStack poseStack, SubmitNodeCollector buffer, int seed, CallbackInfo ci) {
        if (playerAnimLib$shouldHideItem(entity, displayContext)) {
            ci.cancel();
        }
    }
    *///?}

    //? if <1.21.11 {
    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V"), cancellable = true)
    private void cancelItemRender(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                  boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int seed,
                                  CallbackInfo ci) {
        if (playerAnimLib$shouldHideItem(entity, displayContext)) {
            ci.cancel();
        }
    }
    //?}

    /**
     * Items of a hand that the active clip does not animate keep their vanilla render. Only the arm
     * mesh is replaced by the fp_* rig; hiding the item of an unanimated hand made the offhand item
     * disappear whenever a clip only animated the main hand. A hand with an item bone still gets that
     * bone's pose applied around the vanilla item pass by {@code FpArmRendererMixin}.
     */
    //? if <26.3 {
    @Unique
    private static boolean playerAnimLib$shouldHideItem(Entity entity, ItemDisplayContext displayContext) {
        if (entity != Minecraft.getInstance().getCameraEntity()) {
            return false;
        }
        if (!(entity instanceof IAnimatedPlayer)) {
            return false;
        }
        // THIRD_PERSON_MODEL is the only PAL mode that creates a first-person render pass. The
        // addon's own player-arm mode is checked through FpRenderState, because PAL does not know
        // about it. Any other mode leaves the held items untouched.
        //? if >=1.21.11 {
        /*// PAL 1.1.6 dropped FirstPersonMode#isFirstPersonPass and moved the same check to ClientUtil,
        // which is what PAL's own first-person code uses on this version.
        if (!ClientUtil.shouldBeFirstPersonPass() && !FpRenderState.isPlayerArmMode(entity)) {
            return false;
        }
        *///?} else {
        if (!FirstPersonMode.isFirstPersonPass() && !FpRenderState.isPlayerArmMode(entity)) {
            return false;
        }
        //?}
        FirstPersonConfiguration config = FpRenderState.firstPersonConfiguration(entity);
        boolean rightHand = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        return rightHand ? !config.isShowRightItem() : !config.isShowLeftItem();
    }
    //?}
}
