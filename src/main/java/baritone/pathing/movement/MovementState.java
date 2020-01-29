/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.pathing.movement;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;

public class MovementState {

	public static class MovementTarget {

		/**
		 * Yaw and pitch angles that must be matched
		 */
		public Rotation rotation;

		/**
		 * Whether or not this target must force rotations.
		 * <p>
		 * {@code true} if we're trying to place or break blocks, {@code false} if we're trying to look at the movement location
		 */
		private boolean forceRotations;

		public MovementTarget() {
			this(null, false);
		}

		public MovementTarget(Rotation rotation, boolean forceRotations) {
			this.rotation = rotation;
			this.forceRotations = forceRotations;
		}

		public final Optional<Rotation> getRotation() {
			return Optional.ofNullable(rotation);
		}

		public boolean hasToForceRotations() {
			return forceRotations;
		}
	}

	private MovementStatus status;
	private MovementTarget target = new MovementTarget();

	private final Map<Input, Boolean> inputState = new HashMap<>();

	public Map<Input, Boolean> getInputStates() {
		return inputState;
	}

	public MovementStatus getStatus() {
		return status;
	}

	public MovementTarget getTarget() {
		return target;
	}

	public MovementState setInput(Input input, boolean forced) {
		inputState.put(input, forced);
		return this;
	}

	public MovementState setStatus(MovementStatus status) {
		this.status = status;
		return this;
	}

	public MovementState setTarget(MovementTarget target) {
		this.target = target;
		return this;
	}
}
