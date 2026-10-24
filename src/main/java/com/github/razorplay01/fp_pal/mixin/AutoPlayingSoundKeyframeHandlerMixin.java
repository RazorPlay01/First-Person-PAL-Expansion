package com.github.razorplay01.fp_pal.mixin;

import com.zigythebird.playeranim.animation.keyframe.event.builtin.AutoPlayingSoundKeyframeHandler;
import com.zigythebird.playeranim.util.ClientUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
//? if >=1.21.11 {
/*import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
*///?} else {
import net.minecraft.world.entity.player.Player;
//?}
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes sound keyframes of first-person animations audible.
 * <p>
 * {@code ClientLevel} only plays a sound it is handed locally when the excluded entity is the local
 * player (1.21.11 and newer return early from {@code playSeededSound} unless
 * {@code except == minecraft.player}), because that parameter means "this sound belongs to the local
 * player". PAL passes {@code null}, which silently drops every keyframe sound. This redirect passes
 * the local player instead.
 * <p>
 * The target descriptor is version dependent and has to be re-checked with {@code javap -c} on the
 * remapped PAL jar whenever the PAL version is bumped, because a mismatch is a mixin apply failure at
 * startup, not a silent no-op:
 * <ul>
 *     <li>up to 1.21.1: {@code Level.playSound(Player, DDD, SoundEvent, SoundSource, FF)}</li>
 *     <li>1.21.11 and newer: {@code Level.playSound(Entity, DDD, Holder<SoundEvent>, SoundSource, FF)},
 *     so the handler takes a {@code Holder} and re-invokes the very same overload</li>
 * </ul>
 */
@Mixin(AutoPlayingSoundKeyframeHandler.class)
public class AutoPlayingSoundKeyframeHandlerMixin {

	//? if >=1.21.11 {
	/*@Redirect(method = "handle(FLcom/zigythebird/playeranimcore/animation/AnimationController;Lcom/zigythebird/playeranimcore/animation/keyframe/event/data/SoundKeyframeData;Lcom/zigythebird/playeranimcore/animation/AnimationData;)Lcom/zigythebird/playeranimcore/event/EventResult;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FF)V"))
	private void fpPal$playSoundForLocalPlayer(Level level, Entity except, double x, double y, double z,
											   Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
		LocalPlayer localPlayer = ClientUtil.getClientPlayer();
		if (localPlayer == null) return;
		level.playSound(localPlayer, x, y, z, sound, source, volume, pitch);
	}
	*///?} else {
	@Redirect(method = "handle(FLcom/zigythebird/playeranimcore/animation/AnimationController;Lcom/zigythebird/playeranimcore/animation/keyframe/event/data/SoundKeyframeData;Lcom/zigythebird/playeranimcore/animation/AnimationData;)Lcom/zigythebird/playeranimcore/event/EventResult;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
	private void fpPal$playSoundForLocalPlayer(Level level, Player except, double x, double y, double z,
											   SoundEvent sound, SoundSource source, float volume, float pitch) {
		LocalPlayer localPlayer = ClientUtil.getClientPlayer();
		if (localPlayer == null) return;
		level.playSound(localPlayer, x, y, z, sound, source, volume, pitch);
	}
	//?}
}
