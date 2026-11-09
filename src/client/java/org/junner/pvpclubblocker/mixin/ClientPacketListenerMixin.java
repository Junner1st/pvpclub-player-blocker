package org.junner.pvpclubblocker.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import org.junner.pvpclubblocker.PvpClubBlockerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleLogin", at = @At("TAIL"))
	private void pvpclubBlocker$login(ClientboundLoginPacket packet, CallbackInfo ci) {
		PvpClubBlockerClient.onLoginPacket((ClientPacketListener) (Object) this, packet);
	}

	@Inject(method = "handleRespawn", at = @At("TAIL"))
	private void pvpclubBlocker$respawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
		PvpClubBlockerClient.onRespawnPacket((ClientPacketListener) (Object) this, packet);
	}

	@Inject(method = "handleAddObjective", at = @At("TAIL"))
	private void pvpclubBlocker$objective(ClientboundSetObjectivePacket packet, CallbackInfo ci) {
		PvpClubBlockerClient.onObjectivePacket(packet);
	}

	@Inject(method = "handleSetDisplayObjective", at = @At("TAIL"))
	private void pvpclubBlocker$displayObjective(ClientboundSetDisplayObjectivePacket packet, CallbackInfo ci) {
		PvpClubBlockerClient.onDisplayObjectivePacket(packet);
	}

	@Inject(method = "handleGameEvent", at = @At("TAIL"))
	private void pvpclubBlocker$gameEvent(ClientboundGameEventPacket packet, CallbackInfo ci) {
		PvpClubBlockerClient.onGameEventPacket(packet);
	}
}
