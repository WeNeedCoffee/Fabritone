/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.pathing;

import net.minecraft.world.border.WorldBorder;

/**
 * Essentially, a "rule" for the path finder, prevents proposed movements from attempting to venture into the world border, and prevents actual movements from placing blocks in the world border.
 */
public class BetterWorldBorder {

	private final double x1;
	private final double x2;
	private final double z1;
	private final double z2;

	public BetterWorldBorder(WorldBorder border) {
		x1 = border.getBoundWest();
		x2 = border.getBoundEast();
		z1 = border.getBoundNorth();
		z2 = border.getBoundSouth();
	}

	public boolean canPlaceAt(int x, int z) {
		// move it in 1 block on all sides
		// because we can't place a block at the very edge against a block outside the border
		// it won't let us right click it
		return x > x1 && x + 1 < x2 && z > z1 && z + 1 < z2;
	}

	public boolean entirelyContains(int x, int z) {
		return x + 1 > x1 && x < x2 && z + 1 > z1 && z < z2;
	}
}
