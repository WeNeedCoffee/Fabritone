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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import baritone.api.BaritoneAPI;
import baritone.api.event.events.RotationMoveEvent;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

@Mixin(Entity.class)
public class MixinEntity {

	@Shadow
	private float yaw;

	float yawRestore;

	@Inject(method = "updateVelocity", at = @At("HEAD"))
	private void moveRelativeHead(CallbackInfo info) {
		yawRestore = yaw;
		// noinspection ConstantConditions
		if (!ClientPlayerEntity.class.isInstance(this) || BaritoneAPI.getProvider().getBaritoneForPlayer((ClientPlayerEntity) (Object) this) == null)
			return;
		RotationMoveEvent motionUpdateRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE, yaw);
		BaritoneAPI.getProvider().getBaritoneForPlayer((ClientPlayerEntity) (Object) this).getGameEventHandler().onPlayerRotationMove(motionUpdateRotationEvent);
		yaw = motionUpdateRotationEvent.getYaw();
	}

	@Inject(method = "updateVelocity", at = @At("RETURN"))
	private void moveRelativeReturn(CallbackInfo info) {
		yaw = yawRestore;
	}
}
