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

package baritone.api.utils;

import baritone.api.cache.IWorldData;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Brady
 * @since 11/12/2018
 */
public interface IPlayerContext {

    ClientPlayerEntity player();

    IPlayerController playerController();

    World world();

    default Iterable<Entity> entities() {
        return ((ClientWorld) world()).getEntities();
    }

    default Stream<Entity> entitiesStream() {
        return StreamSupport.stream(entities().spliterator(), false);
    }


    IWorldData worldData();

    HitResult objectMouseOver();

    default BetterBlockPos playerFeet() {
        // TODO find a better way to deal with soul sand!!!!!
        BetterBlockPos feet = new BetterBlockPos(player().x, player().y + 0.1251, player().z);
        if (world().getBlockState(feet).getBlock() instanceof SlabBlock) {
            return feet.up();
        }
        return feet;
    }

    default Vec3d playerFeetAsVec() {
        return new Vec3d(player().x, player().y, player().z);
    }

    default Vec3d playerHead() {
        return new Vec3d(player().x, player().y + player().getEyeHeight(EntityPose.STANDING), player().z);
    }

    default Rotation playerRotations() {
        return new Rotation(player().yaw, player().pitch);
    }

    /**
     * Returns the block that the crosshair is currently placed over. Updated once per tick.
     *
     * @return The position of the highlighted block
     */
    default Optional<BlockPos> getSelectedBlock() {
        HitResult result = objectMouseOver();
        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            return Optional.of(((BlockHitResult) result).getBlockPos());
        }
        return Optional.empty();
    }

    default boolean isLookingAt(BlockPos pos) {
        return getSelectedBlock().equals(Optional.of(pos));
    }

    /**
     * Returns the entity that the crosshair is currently placed over. Updated once per tick.
     *
     * @return The entity
     */
    default Optional<Entity> getSelectedEntity() {
        HitResult result = objectMouseOver();
        if (result != null && result.getType() == HitResult.Type.ENTITY) {
            return Optional.of(((EntityHitResult) result).getEntity());
        }
        return Optional.empty();
    }
}
