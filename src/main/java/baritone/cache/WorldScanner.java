/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import baritone.api.cache.ICachedWorld;
import baritone.api.cache.IWorldScanner;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.IPlayerContext;
import baritone.utils.accessor.IBlockStateContainer;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

public enum WorldScanner implements IWorldScanner {

	INSTANCE;

	private static final int[] DEFAULT_COORDINATE_ITERATION_ORDER = IntStream.range(0, 16).toArray();

	@Override
	public int repack(IPlayerContext ctx) {
		return this.repack(ctx, 40);
	}

	@Override
	public int repack(IPlayerContext ctx, int range) {
		ClientChunkManager chunkProvider = (ClientChunkManager) ctx.world().getChunkManager();
		ICachedWorld cachedWorld = ctx.worldData().getCachedWorld();

		BetterBlockPos playerPos = ctx.playerFeet();

		int playerChunkX = playerPos.getX() >> 4;
		int playerChunkZ = playerPos.getZ() >> 4;

		int x1 = playerChunkX - range;
		int z1 = playerChunkZ - range;
		int x2 = playerChunkX + range;
		int z2 = playerChunkZ + range;

		int queued = 0;
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				WorldChunk chunk = chunkProvider.getChunk(x, z, null, false);

				if (chunk != null && !chunk.isEmpty()) {
					queued++;
					cachedWorld.queueForPacking(chunk);
				}
			}
		}

		return queued;
	}

	@Override
	public List<BlockPos> scanChunk(IPlayerContext ctx, BlockOptionalMetaLookup filter, ChunkPos pos, int max, int yLevelThreshold) {
		if (filter.blocks().isEmpty())
			return Collections.emptyList();

		ClientChunkManager chunkProvider = (ClientChunkManager) ctx.world().getChunkManager();
		WorldChunk chunk = chunkProvider.getChunk(pos.x, pos.z, null, false);
		int playerY = ctx.playerFeet().getY();

		if (chunk == null || chunk.isEmpty())
			return Collections.emptyList();

		ArrayList<BlockPos> res = new ArrayList<>();
		scanChunkInto(pos.x << 4, pos.z << 4, chunk, filter, res, max, yLevelThreshold, playerY, DEFAULT_COORDINATE_ITERATION_ORDER);
		return res;
	}

	private boolean scanChunkInto(int chunkX, int chunkZ, WorldChunk chunk, BlockOptionalMetaLookup filter, Collection<BlockPos> result, int max, int yLevelThreshold, int playerY, int[] coordinateIterationOrder) {
		ChunkSection[] chunkInternalStorageArray = chunk.getSectionArray();
		boolean foundWithinY = false;
		for (int yIndex = 0; yIndex < 16; yIndex++) {
			int y0 = coordinateIterationOrder[yIndex];
			ChunkSection section = chunkInternalStorageArray[y0];
			if (section == null || ChunkSection.isEmpty(section)) {
				continue;
			}
			int yReal = y0 << 4;
			IBlockStateContainer bsc = (IBlockStateContainer) section.getContainer();
			// storageArray uses an optimized algorithm that's faster than getAt
			// creating this array and then using getAtPalette is faster than even getFast(int index)
			int[] storage = bsc.storageArray();
			final int imax = 1 << 12;
			for (int i = 0; i < imax; i++) {
				BlockState state = bsc.getAtPalette(storage[i]);
				if (filter.has(state)) {
					int y = yReal | i >> 8 & 15;
					if (result.size() >= max) {
						if (Math.abs(y - playerY) < yLevelThreshold) {
							foundWithinY = true;
						} else {
							if (foundWithinY)
								// have found within Y in this chunk, so don't need to consider outside Y
								// TODO continue iteration to one more sorted Y coordinate block
								return true;
						}
					}
					result.add(new BlockPos(chunkX | i & 15, y, chunkZ | i >> 4 & 15));
				}
			}
		}
		return foundWithinY;
	}

	@Override
	public List<BlockPos> scanChunkRadius(IPlayerContext ctx, BlockOptionalMetaLookup filter, int max, int yLevelThreshold, int maxSearchRadius) {
		ArrayList<BlockPos> res = new ArrayList<>();

		if (filter.blocks().isEmpty())
			return res;
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
					WorldChunk chunk = chunkProvider.getChunk(chunkX, chunkZ, null, false);
					if (chunk == null) {
						continue;
					}
					allUnloaded = false;
					if (scanChunkInto(chunkX << 4, chunkZ << 4, chunk, filter, res, max, yLevelThreshold, playerY, coordinateIterationOrder)) {
						foundWithinY = true;
					}
				}
			}
			if (allUnloaded && foundChunks || res.size() >= max && (searchRadiusSq > maxSearchRadiusSq || searchRadiusSq > 1 && foundWithinY))
				return res;
			searchRadiusSq++;
		}
	}
}
