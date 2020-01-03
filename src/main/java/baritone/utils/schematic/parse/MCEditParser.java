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

package baritone.utils.schematic.parse;

import baritone.api.schematic.AbstractSchematic;
import baritone.api.schematic.ISchematic;
import baritone.utils.schematic.format.SchematicFormat;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.registry.Registry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * An implementation of {@link ISchematicParser} for {@link SchematicFormat#MCEDIT}
 *
 * @author Brady
 * @since 12/16/2019
 */
public enum MCEditParser implements ISchematicParser {
    INSTANCE;

    @Override
    public ISchematic parse(InputStream input) throws IOException {
        return new MCEditSchematic(NbtIo.readCompressed(input));
    }

    private static final class MCEditSchematic extends AbstractSchematic {

        private final BlockState[][][] states;

        MCEditSchematic(CompoundTag schematic) {
            String type = schematic.getString("Materials");
            if (!type.equals("Alpha")) {
                throw new IllegalStateException("bad schematic " + type);
            }
            this.x = schematic.getInt("Width");
            this.y = schematic.getInt("Height");
            this.z = schematic.getInt("Length");
            byte[] blocks = schematic.getByteArray("Blocks");
            byte[] metadata = schematic.getByteArray("Data");

            byte[] additional = null;
            if (schematic.containsKey("AddBlocks")) {
                byte[] addBlocks = schematic.getByteArray("AddBlocks");
                additional = new byte[addBlocks.length * 2];
                for (int i = 0; i < addBlocks.length; i++) {
                    additional[i * 2 + 0] = (byte) ((addBlocks[i] >> 4) & 0xF); // lower nibble
                    additional[i * 2 + 1] = (byte) ((addBlocks[i] >> 0) & 0xF); // upper nibble
                }
            }
            this.states = new BlockState[this.x][this.z][this.y];
            for (int y = 0; y < this.y; y++) {
                for (int z = 0; z < this.z; z++) {
                    for (int x = 0; x < this.x; x++) {
                        int blockInd = (y * this.z + z) * this.x + x;

                        int blockID = blocks[blockInd] & 0xFF;
                        if (additional != null) {
                            // additional is 0 through 15 inclusive since it's & 0xF above
                            blockID |= additional[blockInd] << 8;
                        }
                        Block block = Registry.BLOCK.get(blockID);
                        int meta = metadata[blockInd] & 0xFF;

                        // TODO: Find a way to keep using metadata to get a proper BlockState (Fabritone)
                        this.states[x][z][y] = block.getDefaultState();
                    }
                }
            }
        }

        @Override
        public final BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
            return this.states[x][z][y];
        }
    }
}