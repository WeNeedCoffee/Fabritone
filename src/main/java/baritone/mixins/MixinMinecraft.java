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
import baritone.api.event.events.BlockInteractEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.utils.BaritoneAutoTest;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.BiFunction;

/**
 * @author Brady
 * @since 7/31/2018
 */
@Mixin(MinecraftClient.class)
public class MixinMinecraft {

    @Shadow
    public ClientPlayerEntity player;
    @Shadow
    public ClientWorld world;

    @Inject(
            method = "init",
            at = @At("RETURN")
    )
    private void postInit(CallbackInfo ci) {
        BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    @Inject(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/MinecraftClient.startTimerHackThread()V"
            )
    )
    private void preInit(CallbackInfo ci) {
        BaritoneAutoTest.INSTANCE.onPreInit();
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    opcode = 180,
                    target = "net/minecraft/client/MinecraftClient.currentScreen:Lnet/minecraft/client/gui/screen/Screen;",
                    ordinal = 5,
                    shift = At.Shift.BY,
                    by = -3
            )
    )
    private void runTick(CallbackInfo ci) {
        final BiFunction<EventState, TickEvent.Type, TickEvent> tickProvider = TickEvent.createNextProvider();

        for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {

            TickEvent.Type type = baritone.getPlayerContext().player() != null && baritone.getPlayerContext().world() != null
                    ? TickEvent.Type.IN
                    : TickEvent.Type.OUT;

            baritone.getGameEventHandler().onTick(tickProvider.apply(EventState.PRE, type));
        }

    }

    @Inject(
            method = "joinWorld(Lnet/minecraft/client/world/ClientWorld;)V",
            at = @At("HEAD")
    )
    private void preLoadWorld(ClientWorld world, CallbackInfo ci) {
        // If we're unloading the world but one doesn't exist, ignore it
        if (this.world == null && world == null) {
            return;
        }

        // mc.world changing is only the primary baritone

        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onWorldEvent(
                new WorldEvent(
                        world,
                        EventState.PRE
                )
        );
    }

    @Inject(
            method = "joinWorld(Lnet/minecraft/client/world/ClientWorld;)V",
            at = @At("RETURN")
    )
    private void postLoadWorld(ClientWorld world, CallbackInfo ci) {
        // still fire event for both null, as that means we've just finished exiting a world

        // mc.world changing is only the primary baritone
        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onWorldEvent(
                new WorldEvent(
                        world,
                        EventState.POST
                )
        );
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    opcode = 180,
                    target = "net/minecraft/client/gui/screen/Screen.passEvents:Z"
            )
    )
    private boolean isAllowUserInput(Screen screen) {
        // allow user input is only the primary baritone
        return (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getCurrent() != null && player != null) || screen.passEvents;
    }

    @Inject(
            method = "doItemUse",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/network/ClientPlayerEntity.swingHand(Lnet/minecraft/util/Hand;)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onBlockUse(CallbackInfo ci, Hand var1[], int var2, int var3, Hand enumhand, ItemStack itemstack, BlockHitResult raytrace, int i, ActionResult enumactionresult) {
        // rightClickMouse is only for the main player
        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onBlockInteract(new BlockInteractEvent(raytrace.getBlockPos(), BlockInteractEvent.Type.USE));
    }


}
