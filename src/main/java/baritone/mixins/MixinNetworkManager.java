/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.mixins;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.type.EventState;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Brady
 * @since 8/6/2018
 */
@Mixin(ClientConnection.class)
public class MixinNetworkManager {

    @Shadow
    private Channel channel;

    @Shadow
    @Final
    private NetworkSide side;

    @Inject(
            method = "send",
            at = @At("HEAD")
    )
    private void preDispatchPacket(Packet<?> inPacket, final GenericFutureListener<? extends Future<? super Void>> futureListeners, CallbackInfo ci) {
        if (this.side != NetworkSide.CLIENTBOUND) {
            return;
        }

        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone.getPlayerContext().player() != null && ibaritone.getPlayerContext().player().networkHandler.getConnection() == (ClientConnection) (Object) this) {
                ibaritone.getGameEventHandler().onSendPacket(new PacketEvent((ClientConnection) (Object) this, EventState.PRE, inPacket));
            }
        }
    }

    @Inject(
            method = "send",
            at = @At("RETURN")
    )
    private void postDispatchPacket(Packet<?> inPacket, final GenericFutureListener<? extends Future<? super Void>> futureListeners, CallbackInfo ci) {
        if (this.side != NetworkSide.CLIENTBOUND) {
            return;
        }

        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone.getPlayerContext().player() != null && ibaritone.getPlayerContext().player().networkHandler.getConnection() == (ClientConnection) (Object) this) {
                ibaritone.getGameEventHandler().onSendPacket(new PacketEvent((ClientConnection) (Object) this, EventState.POST, inPacket));
            }
        }
    }

    @Inject(
            method = "method_10770",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/network/ClientConnection.handlePacket(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;)V"
            )
    )
    private void preProcessPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (this.side != NetworkSide.CLIENTBOUND) {
            return;
        }
        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone.getPlayerContext().player() != null && ibaritone.getPlayerContext().player().networkHandler.getConnection() == (ClientConnection) (Object) this) {
                ibaritone.getGameEventHandler().onReceivePacket(new PacketEvent((ClientConnection) (Object) this, EventState.PRE, packet));
            }
        }
    }

    @Inject(
            method = "method_10770",
            at = @At("RETURN")
    )
    private void postProcessPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (!this.channel.isOpen() || this.side != NetworkSide.CLIENTBOUND) {
            return;
        }
        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone.getPlayerContext().player() != null && ibaritone.getPlayerContext().player().networkHandler.getConnection() == (ClientConnection) (Object) this) {
                ibaritone.getGameEventHandler().onReceivePacket(new PacketEvent((ClientConnection) (Object) this, EventState.POST, packet));
            }
        }
    }
}
