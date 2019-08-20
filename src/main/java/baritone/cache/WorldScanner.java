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

package baritone.cache;

import baritone.api.cache.IWorldScanner;
import baritone.api.utils.IPlayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.stream.IntStream;

public enum WorldScanner implements IWorldScanner {

    INSTANCE;

    private static final int[] DEFAULT_COORDINATE_ITERATION_ORDER = IntStream.range(0, 16).toArray();

    @Override
    public List<BlockPos> scanChunkRadius(IPlayerContext ctx, List<Block> blocks, int max, int yLevelThreshold, int maxSearchRadius) {
        if (blocks.contains(null)) {
            throw new IllegalStateException("Invalid block name should have been caught earlier: " + blocks.toString());
        }
        ArrayList<BlockPos> res = new ArrayList<>();
        if (blocks.isEmpty()) {
            return res;
        }
        ClientChunkManager chunkProvider = (ClientChunkManager) ctx.world().getChunkManager();

        int maxSearchRadiusSq = maxSearchRadius * maxSearchRadius;
        int playerChunkX = ctx.playerFeet().getX() >> 4;
        int playerChunkZ = ctx.playerFeet().getZ() >> 4;
        int playerY = ctx.playerFeet().getY();

        int playerYBlockStateContainerIndex = playerY >> 4;
        int[] coordinateIterationOrder = IntStream.range(0, 16).boxed().sorted(Comparator.comparingInt(y -> Math.abs(y - playerYBlockStateContainerIndex))).mapToInt(x -> x).toArray();

        int searchRadiusSq = 0;
        boolean foundWithinY = false;
        while (true) {
            boolean allUnloaded = true;
            boolean foundChunks = false;
            for (int xoff = -searchRadiusSq; xoff <= searchRadiusSq; xoff++) {
                for (int zoff = -searchRadiusSq; zoff <= searchRadiusSq; zoff++) {
                    int distance = xoff * xoff + zoff * zoff;
                    if (distance != searchRadiusSq) {
                        continue;
                    }
                    foundChunks = true;
                    int chunkX = xoff + playerChunkX;
                    int chunkZ = zoff + playerChunkZ;
                    WorldChunk chunk = chunkProvider.method_2857(chunkX, chunkZ, null, false);
                    if (chunk == null) {
                        continue;
                    }
                    allUnloaded = false;
                    if (scanChunkInto(chunkX << 4, chunkZ << 4, chunk, blocks, res, max, yLevelThreshold, playerY, coordinateIterationOrder)) {
                        foundWithinY = true;
                    }
                }
            }
            if ((allUnloaded && foundChunks)
                    || (res.size() >= max
                    && (searchRadiusSq > maxSearchRadiusSq || (searchRadiusSq > 1 && foundWithinY)))
            ) {
                return res;
            }
            searchRadiusSq++;
        }
    }

    @Override
    public List<BlockPos> scanChunk(IPlayerContext ctx, List<Block> blocks, ChunkPos pos, int max, int yLevelThreshold) {
        if (blocks.isEmpty()) {
            return Collections.emptyList();
        }

        ClientChunkManager chunkProvider = (ClientChunkManager) ctx.world().getChunkManager();
        WorldChunk chunk = chunkProvider.method_2857(pos.x, pos.z, null, false);
        int playerY = ctx.playerFeet().getY();

        if (chunk == null || chunk.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<BlockPos> res = new ArrayList<>();
        scanChunkInto(pos.x << 4, pos.z << 4, chunk, blocks, res, max, yLevelThreshold, playerY, DEFAULT_COORDINATE_ITERATION_ORDER);
        return res;
    }

    private boolean scanChunkInto(int chunkX, int chunkZ, WorldChunk chunk, List<Block> search, Collection<BlockPos> result, int max, int yLevelThreshold, int playerY, int[] coordinateIterationOrder) {
        ChunkSection[] chunkInternalStorageArray = chunk.getSectionArray();
        boolean foundWithinY = false;
        for (int yIndex = 0; yIndex < 16; yIndex++) {
            int y0 = coordinateIterationOrder[yIndex];
            ChunkSection section = chunkInternalStorageArray[y0];
            if (section == null || ChunkSection.isEmpty(section)) {
                continue;
            }
            int yReal = y0 << 4;
            PalettedContainer<BlockState> bsc = section.getContainer();
            // the mapping of BlockStateContainer.getIndex from xyz to index is y << 8 | z << 4 | x;
            // for better cache locality, iterate in that order
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = bsc.get(x, y, z);
                        if (search.contains(state.getBlock())) {
                            int yy = yReal | y;
                            if (result.size() >= max) {
                                if (Math.abs(yy - playerY) < yLevelThreshold) {
                                    foundWithinY = true;
                                } else {
                                    if (foundWithinY) {
                                        // have found within Y in this chunk, so don't need to consider outside Y
                                        // TODO continue iteration to one more sorted Y coordinate block
                                        return true;
                                    }
                                }
                            }
                            result.add(new BlockPos(chunkX | x, yy, chunkZ | z));
                        }
                    }
                }
            }
        }
        return foundWithinY;
    }
}
