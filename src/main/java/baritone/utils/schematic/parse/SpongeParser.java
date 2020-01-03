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
import baritone.api.utils.BlockOptionalMeta;
import baritone.utils.schematic.format.SchematicFormat;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.PacketByteBuf;
import net.minecraft.util.registry.Registry;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of {@link ISchematicParser} for {@link SchematicFormat#SPONGE}
 *
 * @author Brady
 * @since 12/16/2019
 */
public enum SpongeParser implements ISchematicParser {
    INSTANCE;

    @Override
    public ISchematic parse(InputStream input) throws IOException {
        CompoundTag nbt = NbtIo.readCompressed(input);
        int version = nbt.getInt("Version");
        switch (version) {
            case 1:
            case 2:
                return new SpongeSchematic(nbt);
            default:
                throw new UnsupportedOperationException("Unsupported Version of the a Sponge Schematic");
        }
    }

    /**
     * Implementation of the Sponge Schematic Format supporting both V1 and V2. (For the current
     * use case, there is no difference between reading a V1 and V2 schematic).
     */
    private static final class SpongeSchematic extends AbstractSchematic {

        /**
         * Block states for this schematic stored in [x, z, y] indexing order
         */
        private BlockState[][][] states;

        SpongeSchematic(CompoundTag nbt) {
            this.x = nbt.getInt("Width");
            this.y = nbt.getInt("Height");
            this.z = nbt.getInt("Length");
            this.states = new BlockState[this.x][this.z][this.y];

            Int2ObjectArrayMap<BlockState> palette = new Int2ObjectArrayMap<>();
            CompoundTag paletteTag = nbt.getCompound("Palette");
            for (String tag : paletteTag.getKeys()) {
                int index = paletteTag.getInt(tag);

                SerializedBlockState serializedState = SerializedBlockState.getFromString(tag);
                if (serializedState == null) {
                    throw new IllegalArgumentException("Unable to parse palette tag");
                }

                BlockState state = serializedState.deserialize();
                if (state == null) {
                    throw new IllegalArgumentException("Unable to deserialize palette tag");
                }

                palette.put(index, state);
            }

            // BlockData is stored as an NBT byte[], however, the actual data that is represented is a varint[].
            // This is kind of a hacky approach but it works /shrug
            byte[] rawBlockData = nbt.getByteArray("BlockData");
            int[] blockData = new int[this.x * this.y * this.z];
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.wrappedBuffer(rawBlockData));
            for (int i = 0; i < blockData.length; i++) {
                if (buffer.readableBytes() > 0) {
                    blockData[i] = buffer.readVarInt();
                } else {
                    throw new IllegalArgumentException("Buffer has no remaining bytes");
                }
            }

            for (int y = 0; y < this.y; y++) {
                for (int z = 0; z < this.z; z++) {
                    for (int x = 0; x < this.x; x++) {
                        int index = (y * this.z + z) * this.x + x;
                        BlockState state = palette.get(blockData[index]);
                        if (state == null) {
                            throw new IllegalArgumentException("Invalid Palette Index " + index);
                        }

                        this.states[x][z][y] = state;
                    }
                }
            }
        }

        @Override
        public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
            return this.states[x][z][y];
        }
    }

    private static final class SerializedBlockState {

        private static final Pattern REGEX = Pattern.compile("(?<location>(\\w+:)?\\w+)(\\[(?<properties>(\\w+=\\w+,?)+)])?");

        private final Identifier resourceLocation;
        private final Map<String, String> properties;
        private BlockState blockState;

        private SerializedBlockState(Identifier resourceLocation, Map<String, String> properties) {
            this.resourceLocation = resourceLocation;
            this.properties = properties;
        }

        Identifier getResourceLocation() {
            return this.resourceLocation;
        }

        Map<String, String> getProperties() {
            return this.properties;
        }

        BlockState deserialize() {
            if (this.blockState == null) {
                // Get the base state for the block specified
                this.blockState = Registry.BLOCK.get(this.resourceLocation).getDefaultState();

                // AFAIK it is best to order the property keys so that Minecraft caches the Block States ideally
                this.properties.keySet().stream().sorted(String::compareTo).forEachOrdered(key -> {
                    // getProperty(String) when lol
                    Property<?> property = null;

                    for (Property<?> p : this.blockState.getProperties()) {
                        if (p.getName().equals(key)) {
                            property = p;
                            break;
                        }
                    }

                    if (property != null) {
                        Optional<?> value = property.getValue(this.properties.get(key));
                        if (value.isPresent()) {
                            this.blockState = this.blockState.with(
                                    BlockOptionalMeta.castToIProperty(property),
                                    BlockOptionalMeta.castToIPropertyValue(property, value)
                            );
                        } else {
                            throw new IllegalArgumentException();
                        }
                    } else {
                        throw new IllegalArgumentException();
                    }
                });
            }
            return this.blockState;
        }

        static SerializedBlockState getFromString(String s) {
            Matcher m = REGEX.matcher(s);
            if (!m.matches()) {
                return null;
            }

            try {
                String location = m.group("location");
                String properties = m.group("properties");

                Identifier resourceLocation = new Identifier(location);
                Map<String, String> propertiesMap = new HashMap<>();
                if (properties != null) {
                    for (String property : properties.split(",")) {
                        String[] split = property.split("=");
                        propertiesMap.put(split[0], split[1]);
                    }
                }

                return new SerializedBlockState(resourceLocation, propertiesMap);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}