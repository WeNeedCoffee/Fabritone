/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.utils;

import java.util.Optional;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * @author Brady
 * @since 9/25/2018
 */
public final class BRotationUtils {

	/**
	 * Constant that a degree value is multiplied by to get the equivalent radian value
	 */
	public static final double DEG_TO_RAD = Math.PI / 180.0;

	/**
	 * Constant that a radian value is multiplied by to get the equivalent degree value
	 */
	public static final double RAD_TO_DEG = 180.0 / Math.PI;

	/**
	 * Offsets from the root block position to the center of each side.
	 */
	private static final Vec3d[] BLOCK_SIDE_MULTIPLIERS = new Vec3d[] { new Vec3d(0.5, 0, 0.5), // Down
			new Vec3d(0.5, 1, 0.5), // Up
			new Vec3d(0.5, 0.5, 0), // North
			new Vec3d(0.5, 0.5, 1), // South
			new Vec3d(0, 0.5, 0.5), // West
			new Vec3d(1, 0.5, 0.5)  // East
	};

	/**
	 * Calculates the rotation from BlockPos<sub>dest</sub> to BlockPos<sub>orig</sub>
	 *
	 * @param orig The origin position
	 * @param dest The destination position
	 * @return The rotation from the origin to the destination
	 */
	public static Rotation calcRotationFromCoords(BlockPos orig, BlockPos dest) {
		return calcRotationFromVec3d(new Vec3d(orig), new Vec3d(dest));
	}

	/**
	 * Calculates the rotation from Vec<sub>dest</sub> to Vec<sub>orig</sub>
	 *
	 * @param orig The origin position
	 * @param dest The destination position
	 * @return The rotation from the origin to the destination
	 */
	private static Rotation calcRotationFromVec3d(Vec3d orig, Vec3d dest) {
		double[] delta = { orig.x - dest.x, orig.y - dest.y, orig.z - dest.z };
		double yaw = MathHelper.atan2(delta[0], -delta[2]);
		double dist = Math.sqrt(delta[0] * delta[0] + delta[2] * delta[2]);
		double pitch = MathHelper.atan2(delta[1], dist);
		return new Rotation((float) (yaw * RAD_TO_DEG), (float) (pitch * RAD_TO_DEG));
	}

	/**
	 * Calculates the rotation from Vec<sub>dest</sub> to Vec<sub>orig</sub> and makes the return value relative to the specified current rotations.
	 *
	 * @param orig    The origin position
	 * @param dest    The destination position
	 * @param current The current rotations
	 * @return The rotation from the origin to the destination
	 * @see #wrapAnglesToRelative(Rotation, Rotation)
	 */
	public static Rotation calcRotationFromVec3d(Vec3d orig, Vec3d dest, Rotation current) {
		return wrapAnglesToRelative(current, calcRotationFromVec3d(orig, dest));
	}

	/**
	 * Calculates the look vector for the specified yaw/pitch rotations.
	 *
	 * @param rotation The input rotation
	 * @return Look vector for the rotation
	 */
	public static Vec3d calcVec3dFromRotation(Rotation rotation) {
		float f = MathHelper.cos(-rotation.getYaw() * (float) DEG_TO_RAD - (float) Math.PI);
		float f1 = MathHelper.sin(-rotation.getYaw() * (float) DEG_TO_RAD - (float) Math.PI);
		float f2 = -MathHelper.cos(-rotation.getPitch() * (float) DEG_TO_RAD);
		float f3 = MathHelper.sin(-rotation.getPitch() * (float) DEG_TO_RAD);
		return new Vec3d(f1 * f2, f3, f * f2);
	}

	/**
	 * Determines if the specified entity is able to reach the center of any of the sides of the specified block. It first checks if the block center is reachable, and if so, that rotation will be returned. If not, it will return the first center of a given side that is reachable. The return type will be {@link Optional#empty()} if the entity is unable to reach any of the sides of the block.
	 *
	 * @param entity             The viewing entity
	 * @param pos                The target block position
	 * @param blockReachDistance The block reach distance of the entity
	 * @return The optional rotation
	 */
	public static Optional<Rotation> reachable(ClientPlayerEntity entity, BlockPos pos, double blockReachDistance) {
		IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(entity);
		if (baritone.getPlayerContext().isLookingAt(pos))
			/*
			 * why add 0.0001? to indicate that we actually have a desired pitch the way we indicate that the pitch can be whatever and we only care about the yaw is by setting the desired pitch to the current pitch setting the desired pitch to the current pitch + 0.0001 means that we do have a desired pitch, it's just what it currently is
			 *
			 * or if you're a normal person literally all this does it ensure that we don't nudge the pitch to a normal level
			 */
			return Optional.of(new Rotation(entity.yaw, entity.pitch + 0.0001F));
		Optional<Rotation> possibleRotation = reachableCenter(entity, pos, blockReachDistance);
		//System.out.println("center: " + possibleRotation);
		if (possibleRotation.isPresent())
			return possibleRotation;

		BlockState state = entity.world.getBlockState(pos);
		VoxelShape shape = state.getOutlineShape(entity.world, pos);
		if (shape.isEmpty()) {
			shape = VoxelShapes.fullCube();
		}
		for (Vec3d sideOffset : BLOCK_SIDE_MULTIPLIERS) {
			double xDiff = shape.getMinimum(Direction.Axis.X) * sideOffset.x + shape.getMaximum(Direction.Axis.X) * (1 - sideOffset.x);
			double yDiff = shape.getMinimum(Direction.Axis.Y) * sideOffset.y + shape.getMaximum(Direction.Axis.Y) * (1 - sideOffset.y);
			double zDiff = shape.getMinimum(Direction.Axis.Z) * sideOffset.z + shape.getMaximum(Direction.Axis.Z) * (1 - sideOffset.z);
			possibleRotation = reachableOffset(entity, pos, new Vec3d(pos).add(xDiff, yDiff, zDiff), blockReachDistance);
			if (possibleRotation.isPresent())
				return possibleRotation;
		}
		return Optional.empty();
	}

	/**
	 * @param ctx Context for the viewing entity
	 * @param pos The target block position
	 * @return The optional rotation
	 * @see #reachable(ClientPlayerEntity, BlockPos, double)
	 */
	public static Optional<Rotation> reachable(IPlayerContext ctx, BlockPos pos) {
		return reachable(ctx.player(), pos, ctx.playerController().getBlockReachDistance());
	}

	/**
	 * Determines if the specified entity is able to reach the specified block where it is looking at the direct center of it's hitbox.
	 *
	 * @param entity             The viewing entity
	 * @param pos                The target block position
	 * @param blockReachDistance The block reach distance of the entity
	 * @return The optional rotation
	 */
	public static Optional<Rotation> reachableCenter(Entity entity, BlockPos pos, double blockReachDistance) {
		return reachableOffset(entity, pos, VecUtils.calculateBlockCenter(entity.world, pos), blockReachDistance);
	}

	/**
	 * Determines if the specified entity is able to reach the specified block with the given offsetted position. The return type will be {@link Optional#empty()} if the entity is unable to reach the block with the offset applied.
	 *
	 * @param entity             The viewing entity
	 * @param pos                The target block position
	 * @param offsetPos          The position of the block with the offset applied.
	 * @param blockReachDistance The block reach distance of the entity
	 * @return The optional rotation
	 */
	public static Optional<Rotation> reachableOffset(Entity entity, BlockPos pos, Vec3d offsetPos, double blockReachDistance) {
		Rotation rotation = calcRotationFromVec3d(entity.getCameraPosVec(1.0F), offsetPos, new Rotation(entity.yaw, entity.pitch));
		HitResult result = RayTraceUtils.rayTraceTowards(entity, rotation, blockReachDistance);
		//System.out.println(result);
		if (result != null && result.getType() == HitResult.Type.BLOCK) {
			if (((BlockHitResult) result).getBlockPos().equals(pos))
				return Optional.of(rotation);
			if (entity.world.getBlockState(pos).getBlock() instanceof FireBlock && ((BlockHitResult) result).getBlockPos().equals(pos.down()))
				return Optional.of(rotation);
		}
		return Optional.empty();
	}
	
	public static Rotation reachableOffsetB(Entity entity, BlockPos pos) {
		return calcRotationFromVec3d(entity.getCameraPosVec(1.0F), VecUtils.calculateBlockCenter(entity.world, pos), new Rotation(entity.yaw, entity.pitch));		
	}
	

	/**
	 * Wraps the target angles to a relative value from the current angles. This is done by subtracting the current from the target, normalizing it, and then adding the current angles back to it.
	 *
	 * @param current The current angles
	 * @param target  The target angles
	 * @return The wrapped angles
	 */
	public static Rotation wrapAnglesToRelative(Rotation current, Rotation target) {
		if (current.yawIsReallyClose(target))
			return new Rotation(current.getYaw(), target.getPitch());
		return target.subtract(current).normalize().add(current);
	}

	private BRotationUtils() {
	}
}
