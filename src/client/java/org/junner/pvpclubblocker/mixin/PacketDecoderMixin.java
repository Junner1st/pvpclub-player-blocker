package org.junner.pvpclubblocker.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import org.junner.pvpclubblocker.PvpClubBlockerClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PacketDecoder.class)
public abstract class PacketDecoderMixin {
	@Shadow @Final private ProtocolInfo<?> protocolInfo;

	@Inject(method = "decode", at = @At("HEAD"))
	private void pvpclubBlocker$captureInbound(ChannelHandlerContext context, ByteBuf buffer,
			List<Object> output, CallbackInfo ci) {
		PvpClubBlockerClient.onRawPacket(protocolInfo, buffer);
	}
}
