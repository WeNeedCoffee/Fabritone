/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.events.type.EventState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.packet.ChunkDataS2CPacket;
import net.minecraft.client.network.packet.CombatEventS2CPacket;
import net.minecraft.client.network.packet.UnloadChunkS2CPacket;

/**
 * @author Brady
 * @since 8/3/2018
 */
@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetHandler {

	// unused lol
	/*
	 * @Inject( method = "handleChunkData", at = @At( value = "INVOKE", target = "net/minecraft/client/multiplayer/ChunkProviderClient.func_212474_a(IILnet/minecraft/network/PacketBuffer;IZ)Lnet/minecraft/world/chunk/Chunk;" ) ) private void preRead(SPacketChunkData packetIn, CallbackInfo ci) { for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) { if (ibaritone.getPlayerContext().player().connection == (NetHandlerPlayClient) (Object) this) { ibaritone.getGameEventHandler().onChunkEvent( new ChunkEvent( EventState.PRE, packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL : ChunkEvent.Type.POPULATE_PARTIAL, packetIn.getChunkX(), packetIn.getChunkZ() ) ); } } }
	 */

	@Inject(method = "onCombatEvent", at = @At(value = "INVOKE", target = "net/minecraft/client/MinecraftClient.openScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
	private void onPlayerDeath(CombatEventS2CPacket packetIn, CallbackInfo ci) {
		for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
			if (ibaritone.getPlayerContext().player().networkHandler == (ClientPlayNetworkHandler) (Object) this) {
				ibaritone.getGameEventHandler().onPlayerDeath();
			}
		}
	}

	@Inject(method = "onUnloadChunk", at = @At("RETURN"))
	private void postChunkUnload(UnloadChunkS2CPacket packet, CallbackInfo ci) {
		for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
			if (ibaritone.getPlayerContext().player().networkHandler == (ClientPlayNetworkHandler) (Object) this) {
				ibaritone.getGameEventHandler().onChunkEvent(new ChunkEvent(EventState.POST, ChunkEvent.Type.UNLOAD, packet.getX(), packet.getZ()));
			}
		}
	}

	@Inject(method = "onChunkData", at = @At("RETURN"))
	private void postHandleChunkData(ChunkDataS2CPacket packetIn, CallbackInfo ci) {
		for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
			if (ibaritone.getPlayerContext().player().networkHandler == (ClientPlayNetworkHandler) (Object) this) {
				ibaritone.getGameEventHandler().onChunkEvent(new ChunkEvent(EventState.POST, packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL : ChunkEvent.Type.POPULATE_PARTIAL, packetIn.getX(), packetIn.getZ()));
			}
		}
	}

	@Inject(method = "onUnloadChunk", at = @At("HEAD"))
	private void preChunkUnload(UnloadChunkS2CPacket packet, CallbackInfo ci) {
		for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
			if (ibaritone.getPlayerContext().player().networkHandler == (ClientPlayNetworkHandler) (Object) this) {
				ibaritone.getGameEventHandler().onChunkEvent(new ChunkEvent(EventState.PRE, ChunkEvent.Type.UNLOAD, packet.getX(), packet.getZ()));
			}
		}
	}
}
