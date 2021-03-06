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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BRotationUtils;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.behavior.PathingBehavior;
import baritone.utils.BlockStateInterface;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public abstract class Movement implements IMovement, MovementHelper {

	public static final Direction[] HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN };

	protected final IBaritone baritone;
	protected final IPlayerContext ctx;

	private MovementState currentState = new MovementState().setStatus(MovementStatus.PREPPING);

	protected final BetterBlockPos src;

	protected final BetterBlockPos dest;

	/**
	 * The positions that need to be broken before this movement can ensue
	 */
	protected final BetterBlockPos[] positionsToBreak;

	/**
	 * The position where we need to place a block before this movement can ensue
	 */
	protected final BetterBlockPos positionToPlace;

	private Double cost;

	public List<BlockPos> toBreakCached = null;
	public List<BlockPos> toPlaceCached = null;
	public List<BlockPos> toWalkIntoCached = null;

	private Set<BetterBlockPos> validPositionsCached = null;

	private Boolean calculatedWhileLoaded;

	protected Movement(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest, BetterBlockPos[] toBreak) {
		this(baritone, src, dest, toBreak, null);
	}

	protected Movement(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest, BetterBlockPos[] toBreak, BetterBlockPos toPlace) {
		this.baritone = baritone;
		ctx = baritone.getPlayerContext();
		this.src = src;
		this.dest = dest;
		positionsToBreak = toBreak;
		positionToPlace = toPlace;
	}

	public abstract double calculateCost(CalculationContext context);

	@Override
	public boolean calculatedWhileLoaded() {
		return calculatedWhileLoaded;
	}

	protected abstract Set<BetterBlockPos> calculateValidPositions();

	public void checkLoadedChunk(CalculationContext context) {
		calculatedWhileLoaded = context.bsi.worldContainsLoadedChunk(dest.x, dest.z);
	}

	@Override
	public double getCost() throws NullPointerException {
		return cost;
	}

	public double getCost(CalculationContext context) {
		if (cost == null) {
			cost = calculateCost(context);
		}
		return cost;
	}

	@Override
	public BetterBlockPos getDest() {
		return dest;
	}

	@Override
	public BlockPos getDirection() {
		return getDest().subtract(getSrc());
	}

	@Override
	public BetterBlockPos getSrc() {
		return src;
	}

	public Set<BetterBlockPos> getValidPositions() {
		if (validPositionsCached == null) {
			validPositionsCached = calculateValidPositions();
			Objects.requireNonNull(validPositionsCached);
		}
		return validPositionsCached;
	}

	public void override(double cost) {
		this.cost = cost;
	}

	protected boolean playerInValidPosition() {
		return getValidPositions().contains(ctx.playerFeet()) || getValidPositions().contains(((PathingBehavior) baritone.getPathingBehavior()).pathStart());
	}

	protected boolean prepared(MovementState state) {
		if (state.getStatus() == MovementStatus.WAITING)
			return true;
		boolean somethingInTheWay = false;
		for (BetterBlockPos blockPos : positionsToBreak) {
			if (!ctx.world().getEntities(FallingBlockEntity.class, new Box(0, 0, 0, 1, 1.1, 1).offset(blockPos), FallingBlockEntity::collides).isEmpty() && Baritone.settings().pauseMiningForFallingBlocks.value)
				return false;
			if (!MovementHelper.canWalkThrough(ctx, blockPos)) { // can't break air, so don't try
				somethingInTheWay = true;
				MovementHelper.switchToBestToolFor(ctx, BlockStateInterface.get(ctx, blockPos));
				Optional<Rotation> reachable = BRotationUtils.reachable(ctx.player(), blockPos, ctx.playerController().getBlockReachDistance());
				if (reachable.isPresent()) {
					Rotation rotTowardsBlock = reachable.get();
					state.setTarget(new MovementState.MovementTarget(rotTowardsBlock, true));
					if (ctx.isLookingAt(blockPos) || ctx.playerRotations().isReallyCloseTo(rotTowardsBlock)) {
						state.setInput(Input.CLICK_LEFT, true);
					}
					return false;
				}
				//get rekt minecraft
				//i'm doing it anyway
				//i dont care if theres snow in the way!!!!!!!
				//you dont own me!!!!
				state.setTarget(new MovementState.MovementTarget(BRotationUtils.calcRotationFromVec3d(ctx.player().getCameraPosVec(1.0F), VecUtils.getBlockPosCenter(blockPos), ctx.playerRotations()), true));
				// don't check selectedblock on this one, this is a fallback when we can't see any face directly, it's intended to be breaking the "incorrect" block
				state.setInput(Input.CLICK_LEFT, true);
				return false;
			}
		}
		if (somethingInTheWay) {
			// There's a block or blocks that we can't walk through, but we have no target rotation to reach any
			// So don't return true, actually set state to unreachable
			state.setStatus(MovementStatus.UNREACHABLE);
			return true;
		}
		return true;
	}

	public double recalculateCost(CalculationContext context) {
		cost = null;
		return getCost(context);
	}

	@Override
	public void reset() {
		currentState = new MovementState().setStatus(MovementStatus.PREPPING);
	}

	@Override
	public void resetBlockCache() {
		toBreakCached = null;
		toPlaceCached = null;
		toWalkIntoCached = null;
	}

	@Override
	public boolean safeToCancel() {
		return safeToCancel(currentState);
	}

	protected boolean safeToCancel(MovementState currentState) {
		return true;
	}

	public List<BlockPos> toBreak(BlockStateInterface bsi) {
		if (toBreakCached != null)
			return toBreakCached;
		List<BlockPos> result = new ArrayList<>();
		for (BetterBlockPos positionToBreak : positionsToBreak) {
			if (!MovementHelper.canWalkThrough(bsi, positionToBreak.x, positionToBreak.y, positionToBreak.z)) {
				result.add(positionToBreak);
			}
		}
		toBreakCached = result;
		return result;
	}

	public BlockPos[] toBreakAll() {
		return positionsToBreak;
	}

	public List<BlockPos> toPlace(BlockStateInterface bsi) {
		if (toPlaceCached != null)
			return toPlaceCached;
		List<BlockPos> result = new ArrayList<>();
		if (positionToPlace != null && !MovementHelper.canWalkOn(bsi, positionToPlace.x, positionToPlace.y, positionToPlace.z)) {
			result.add(positionToPlace);
		}
		toPlaceCached = result;
		return result;
	}

	public List<BlockPos> toWalkInto(BlockStateInterface bsi) { // overridden by movementdiagonal
		if (toWalkIntoCached == null) {
			toWalkIntoCached = new ArrayList<>();
		}
		return toWalkIntoCached;
	}

	/**
	 * Handles the execution of the latest Movement State, and offers a Status to the calling class.
	 *
	 * @return Status
	 */
	@Override
	public MovementStatus update() {
		ctx.player().abilities.flying = false;
		currentState = updateState(currentState);
		if (MovementHelper.isLiquid(ctx, ctx.playerFeet())) {
			currentState.setInput(Input.JUMP, true);
		}
		if (ctx.player().isInsideWall()) {
			ctx.getSelectedBlock().ifPresent(pos -> MovementHelper.switchToBestToolFor(ctx, BlockStateInterface.get(ctx, pos)));
			currentState.setInput(Input.CLICK_LEFT, true);
		}

		// If the movement target has to force the new rotations, or we aren't using silent move, then force the rotations
		currentState.getTarget().getRotation().ifPresent(rotation -> baritone.getLookBehavior().updateTarget(rotation, currentState.getTarget().hasToForceRotations()));
		baritone.getInputOverrideHandler().clearAllKeys();
		currentState.getInputStates().forEach((input, forced) -> {
			baritone.getInputOverrideHandler().setInputForceState(input, forced);
		});
		currentState.getInputStates().clear();

		// If the current status indicates a completed movement
		if (currentState.getStatus().isComplete()) {
			baritone.getInputOverrideHandler().clearAllKeys();
		}

		return currentState.getStatus();
	}

	/**
	 * Calculate latest movement state. Gets called once a tick.
	 *
	 * @param state The current state
	 * @return The new state
	 */
	public MovementState updateState(MovementState state) {
		if (!prepared(state))
			return state.setStatus(MovementStatus.PREPPING);
		else if (state.getStatus() == MovementStatus.PREPPING) {
			state.setStatus(MovementStatus.WAITING);
		}

		if (state.getStatus() == MovementStatus.WAITING) {
			state.setStatus(MovementStatus.RUNNING);
		}

		return state;
	}
}
