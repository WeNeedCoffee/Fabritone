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

package baritone.mixins;

import baritone.utils.accessor.IChunkArray;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(targets = "net.minecraft.client.world.ClientChunkManager$ClientChunkMap")
public abstract class MixinChunkArray implements IChunkArray {

    @Shadow
    private AtomicReferenceArray<Chunk> chunks;
    @Shadow
    private int loadDistance;
    @Shadow
    private int loadDiameter;
    @Shadow
    private int centerChunkX;
    @Shadow
    private int centerChunkZ;
    @Shadow
    private int field_19143;

    @Shadow
    protected abstract boolean hasChunk(int x, int z);

    @Shadow
    protected abstract int index(int x, int z);

    @Shadow
    protected abstract void replace(int index, Chunk chunk);

    @Override
    public int centerX() {
        return centerChunkX;
    }

    @Override
    public int centerZ() {
        return centerChunkZ;
    }

    @Override
    public int viewDistance() {
        return loadDistance;
    }

    @Override
    public AtomicReferenceArray<Chunk> getChunks() {
        return chunks;
    }

    @Override
    public void copyFrom(IChunkArray other) {
        centerChunkX = other.centerX();
        centerChunkZ = other.centerZ();

        AtomicReferenceArray<Chunk> copyingFrom = other.getChunks();
        for (int k = 0; k < copyingFrom.length(); ++k) {
            Chunk chunk = copyingFrom.get(k);
            if (chunk != null) {
                ChunkPos chunkpos = chunk.getPos();
                if (hasChunk(chunkpos.x, chunkpos.z)) {
                    int index = index(chunkpos.x, chunkpos.z);
                    if (chunks.get(index) != null) {
                        throw new IllegalStateException("Doing this would mutate the client's REAL loaded chunks?!");
                    }
                    replace(index, chunk);
                }
            }
        }
    }
}
