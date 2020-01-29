/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.schematic;

import java.util.List;
import baritone.api.schematic.AbstractSchematic;
import baritone.api.schematic.IStaticSchematic;
import net.minecraft.block.BlockState;

/**
 * Default implementation of {@link IStaticSchematic}
 *
 * @author Brady
 * @since 12/23/2019
 */
public class StaticSchematic extends AbstractSchematic implements IStaticSchematic {

	protected BlockState[][][] states;

	@Override
	public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
		return states[x][z][y];
	}

	@Override
	public BlockState[] getColumn(int x, int z) {
		return states[x][z];
	}

	@Override
	public BlockState getDirect(int x, int y, int z) {
		return states[x][z][y];
	}
}