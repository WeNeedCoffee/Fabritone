/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import baritone.api.utils.input.Input;

public class PlayerMovementInput extends net.minecraft.client.input.Input {
	private final InputOverrideHandler handler;

	PlayerMovementInput(InputOverrideHandler handler) {
		this.handler = handler;
	}

	@Override
	public void tick(boolean p_217607_1_) {
		movementSideways = 0.0F;
		movementForward = 0.0F;

		jumping = handler.isInputForcedDown(Input.JUMP); // oppa gangnam style

		if (pressingForward = handler.isInputForcedDown(Input.MOVE_FORWARD)) {
			movementForward++;
		}

		if (pressingBack = handler.isInputForcedDown(Input.MOVE_BACK)) {
			movementForward--;
		}

		if (pressingLeft = handler.isInputForcedDown(Input.MOVE_LEFT)) {
			movementSideways++;
		}

		if (pressingRight = handler.isInputForcedDown(Input.MOVE_RIGHT)) {
			movementSideways--;
		}

		if (sneaking = handler.isInputForcedDown(Input.SNEAK)) {
			movementSideways *= 0.3D;
			movementForward *= 0.3D;
		}
	}
}
