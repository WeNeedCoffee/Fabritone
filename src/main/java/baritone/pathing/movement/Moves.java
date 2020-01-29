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

import baritone.api.utils.BetterBlockPos;
import baritone.pathing.movement.movements.MovementAscend;
import baritone.pathing.movement.movements.MovementDescend;
import baritone.pathing.movement.movements.MovementDiagonal;
import baritone.pathing.movement.movements.MovementDownward;
import baritone.pathing.movement.movements.MovementFall;
import baritone.pathing.movement.movements.MovementParkour;
import baritone.pathing.movement.movements.MovementPillar;
import baritone.pathing.movement.movements.MovementTraverse;
import baritone.utils.pathing.MutableMoveResult;
import net.minecraft.util.math.Direction;

/**
 * An enum of all possible movements attached to all possible directions they could be taken in
 *
 * @author leijurv
 */
public enum Moves {
	DOWNWARD(0, -1, 0) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementDownward(context.getBaritone(), src, src.down());
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementDownward.cost(context, x, y, z);
		}
	},

	PILLAR(0, +1, 0) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementPillar(context.getBaritone(), src, src.up());
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementPillar.cost(context, x, y, z);
		}
	},

	TRAVERSE_NORTH(0, 0, -1) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementTraverse(context.getBaritone(), src, src.north());
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementTraverse.cost(context, x, y, z, x, z - 1);
		}
	},

	TRAVERSE_SOUTH(0, 0, +1) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementTraverse(context.getBaritone(), src, src.south());
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementTraverse.cost(context, x, y, z, x, z + 1);
		}
	},

	TRAVERSE_EAST(+1, 0, 0) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementTraverse(context.getBaritone(), src, src.east());
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementTraverse.cost(context, x, y, z, x + 1, z);
		}
	},

	TRAVERSE_WEST(-1, 0, 0) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementTraverse(context.getBaritone(), src, src.west());
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementTraverse.cost(context, x, y, z, x - 1, z);
		}
	},

	ASCEND_NORTH(0, +1, -1) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x, src.y + 1, src.z - 1));
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementAscend.cost(context, x, y, z, x, z - 1);
		}
	},

	ASCEND_SOUTH(0, +1, +1) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x, src.y + 1, src.z + 1));
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementAscend.cost(context, x, y, z, x, z + 1);
		}
	},

	ASCEND_EAST(+1, +1, 0) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x + 1, src.y + 1, src.z));
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementAscend.cost(context, x, y, z, x + 1, z);
		}
	},

	ASCEND_WEST(-1, +1, 0) {
		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x - 1, src.y + 1, src.z));
		}

		@Override
		public double cost(CalculationContext context, int x, int y, int z) {
			return MovementAscend.cost(context, x, y, z, x - 1, z);
		}
	},

	DESCEND_EAST(+1, -1, 0, false, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementDescend.cost(context, x, y, z, x + 1, z, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			MutableMoveResult res = new MutableMoveResult();
			apply(context, src.x, src.y, src.z, res);
			if (res.y == src.y - 1)
				return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
			else
				return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
		}
	},

	DESCEND_WEST(-1, -1, 0, false, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementDescend.cost(context, x, y, z, x - 1, z, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			MutableMoveResult res = new MutableMoveResult();
			apply(context, src.x, src.y, src.z, res);
			if (res.y == src.y - 1)
				return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
			else
				return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
		}
	},

	DESCEND_NORTH(0, -1, -1, false, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementDescend.cost(context, x, y, z, x, z - 1, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			MutableMoveResult res = new MutableMoveResult();
			apply(context, src.x, src.y, src.z, res);
			if (res.y == src.y - 1)
				return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
			else
				return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
		}
	},

	DESCEND_SOUTH(0, -1, +1, false, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementDescend.cost(context, x, y, z, x, z + 1, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			MutableMoveResult res = new MutableMoveResult();
			apply(context, src.x, src.y, src.z, res);
			if (res.y == src.y - 1)
				return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
			else
				return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
		}
	},

	DIAGONAL_NORTHEAST(+1, 0, -1, false, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementDiagonal.cost(context, x, y, z, x + 1, z - 1, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			MutableMoveResult res = new MutableMoveResult();
			apply(context, src.x, src.y, src.z, res);
			return new MovementDiagonal(context.getBaritone(), src, Direction.NORTH, Direction.EAST, res.y - src.y);
		}
	},

	DIAGONAL_NORTHWEST(-1, 0, -1, false, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementDiagonal.cost(context, x, y, z, x - 1, z - 1, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			MutableMoveResult res = new MutableMoveResult();
			apply(context, src.x, src.y, src.z, res);
			return new MovementDiagonal(context.getBaritone(), src, Direction.NORTH, Direction.WEST, res.y - src.y);
		}
	},

	DIAGONAL_SOUTHEAST(+1, 0, +1, false, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementDiagonal.cost(context, x, y, z, x + 1, z + 1, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			MutableMoveResult res = new MutableMoveResult();
			apply(context, src.x, src.y, src.z, res);
			return new MovementDiagonal(context.getBaritone(), src, Direction.SOUTH, Direction.EAST, res.y - src.y);
		}
	},

	DIAGONAL_SOUTHWEST(-1, 0, +1, false, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementDiagonal.cost(context, x, y, z, x - 1, z + 1, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			MutableMoveResult res = new MutableMoveResult();
			apply(context, src.x, src.y, src.z, res);
			return new MovementDiagonal(context.getBaritone(), src, Direction.SOUTH, Direction.WEST, res.y - src.y);
		}
	},

	PARKOUR_NORTH(0, 0, -4, true, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementParkour.cost(context, x, y, z, Direction.NORTH, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return MovementParkour.cost(context, src, Direction.NORTH);
		}
	},

	PARKOUR_SOUTH(0, 0, +4, true, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementParkour.cost(context, x, y, z, Direction.SOUTH, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return MovementParkour.cost(context, src, Direction.SOUTH);
		}
	},

	PARKOUR_EAST(+4, 0, 0, true, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementParkour.cost(context, x, y, z, Direction.EAST, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return MovementParkour.cost(context, src, Direction.EAST);
		}
	},

	PARKOUR_WEST(-4, 0, 0, true, true) {
		@Override
		public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
			MovementParkour.cost(context, x, y, z, Direction.WEST, result);
		}

		@Override
		public Movement apply0(CalculationContext context, BetterBlockPos src) {
			return MovementParkour.cost(context, src, Direction.WEST);
		}
	};

	public final boolean dynamicXZ;
	public final boolean dynamicY;

	public final int xOffset;
	public final int yOffset;
	public final int zOffset;

	Moves(int x, int y, int z) {
		this(x, y, z, false, false);
	}

	Moves(int x, int y, int z, boolean dynamicXZ, boolean dynamicY) {
		xOffset = x;
		yOffset = y;
		zOffset = z;
		this.dynamicXZ = dynamicXZ;
		this.dynamicY = dynamicY;
	}

	public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
		if (dynamicXZ || dynamicY)
			throw new UnsupportedOperationException();
		result.x = x + xOffset;
		result.y = y + yOffset;
		result.z = z + zOffset;
		result.cost = cost(context, x, y, z);
	}

	public abstract Movement apply0(CalculationContext context, BetterBlockPos src);

	public double cost(CalculationContext context, int x, int y, int z) {
		throw new UnsupportedOperationException();
	}
}
