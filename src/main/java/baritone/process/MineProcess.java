/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.process;

import static baritone.api.pathing.movement.ActionCosts.COST_INF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.process.IMineProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BRotationUtils;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.BlockUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.cache.CachedChunk;
import baritone.cache.WorldScanner;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.container.PlayerContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.packet.HandSwingC2SPacket;
import net.minecraft.server.network.packet.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RayTraceContext;
import net.wurstclient.WurstClient;
import net.wurstclient.util.RotationUtils;

/**
 * Mine blocks of a certain type
 *
 * @author leijurv
 */
public final class MineProcess extends BaritoneProcessHelper implements IMineProcess {

	private static class GoalThreeBlocks extends GoalTwoBlocks {

		public GoalThreeBlocks(BlockPos pos) {
			super(pos);
		}

		@Override
		public double heuristic(int x, int y, int z) {
			int xDiff = x - this.x;
			int yDiff = y - this.y;
			int zDiff = z - this.z;
			return GoalBlock.calculate(xDiff, yDiff < -1 ? yDiff + 2 : yDiff == -1 ? 0 : yDiff, zDiff);
		}

		@Override
		public boolean isInGoal(int x, int y, int z) {
			return x == this.x && (y == this.y || y == this.y - 1 || y == this.y - 2) && z == this.z;
		}
	}

	private static final int ORE_LOCATIONS_COUNT = 64;

	public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {
		if (MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), ctx.bsi.get0(pos), true) >= COST_INF)
			return false;

		// bedrock above and below makes it implausible, otherwise we're good
		return !(ctx.bsi.get0(pos.up()).getBlock() == Blocks.BEDROCK && ctx.bsi.get0(pos.down()).getBlock() == Blocks.BEDROCK);
	}

	private static List<BlockPos> prune(CalculationContext ctx, List<BlockPos> locs2, BlockOptionalMetaLookup filter, int max, List<BlockPos> blacklist, List<BlockPos> dropped) {
		dropped.removeIf(drop -> {
			for (BlockPos pos : locs2) {
				if (pos.getSquaredDistance(drop) <= 9 && filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ())) && MineProcess.plausibleToBreak(ctx, pos))
					return true;
			}
			return false;
		});
		List<BlockPos> locs = locs2.stream().distinct()

				// remove any that are within loaded chunks that aren't actually what we want
				.filter(pos -> !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ()) || filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ())) || dropped.contains(pos))

				// remove any that are implausible to mine (encased in bedrock, or touching lava)
				.filter(pos -> MineProcess.plausibleToBreak(ctx, pos))

				.filter(pos -> !blacklist.contains(pos))

				.filter(pos -> ctx.baritone.getSelectionManager().getOnlySelection() != null ? ctx.baritone.getSelectionManager().getOnlySelection().aabb().contains(new Vec3d(pos.getX(), pos.getY(), pos.getZ())) : true)

				.sorted(Comparator.comparingDouble(new BlockPos(ctx.getBaritone().getPlayerContext().player())::getSquaredDistance)).collect(Collectors.toList());

		if (locs.size() > max)
			return locs.subList(0, max);
		return locs;
	}

	public static List<BlockPos> searchWorld(CalculationContext ctx, BlockOptionalMetaLookup filter, int max, List<BlockPos> alreadyKnown, List<BlockPos> blacklist, List<BlockPos> dropped) {
		List<BlockPos> locs = new ArrayList<>();
		List<Block> untracked = new ArrayList<>();
		for (BlockOptionalMeta bom : filter.blocks()) {
			Block block = bom.getBlock();
			if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
				BetterBlockPos pf = ctx.baritone.getPlayerContext().playerFeet();

				// maxRegionDistanceSq 2 means adjacent directly or adjacent diagonally; nothing further than that
				locs.addAll(ctx.worldData.getCachedWorld().getLocationsOf(BlockUtils.blockToString(block), Baritone.settings().maxCachedWorldScanCount.value, pf.x, pf.z, 2));
			} else {
				untracked.add(block);
			}
		}

		locs = prune(ctx, locs, filter, max, blacklist, dropped);

		if (!untracked.isEmpty() || Baritone.settings().extendCacheOnThreshold.value && locs.size() < max) {
			locs.addAll(WorldScanner.INSTANCE.scanChunkRadius(ctx.getBaritone().getPlayerContext(), filter, max, 10, 32)); // maxSearchRadius is NOT sq
		}

		locs.addAll(alreadyKnown);

		return prune(ctx, locs, filter, max, blacklist, dropped);
	}

	private BlockOptionalMetaLookup filter;
	private List<BlockPos> knownOreLocations;
	private List<BlockPos> blacklist; // inaccessible
	private Map<BlockPos, Long> anticipatedDrops;

	private BlockPos branchPoint;

	private GoalRunAway branchPointRunaway;

	private int desiredQuantity;

	private int tickCount;

	public MineProcess(Baritone baritone) {
		super(baritone);
	}

	private void addNearby() {
		List<BlockPos> dropped = droppedItemsScan();
		knownOreLocations.addAll(dropped);
		BlockPos playerFeet = ctx.playerFeet();
		BlockStateInterface bsi = new BlockStateInterface(ctx);
		int searchDist = WurstClient.MC.options.viewDistance * 16;
		double fakedBlockReachDistance = 20; // at least 10 * sqrt(3) with some extra space to account for positioning within the block
		for (int x = playerFeet.getX() - searchDist; x <= playerFeet.getX() + searchDist; x++) {
			for (int y = playerFeet.getY() - searchDist; y <= playerFeet.getY() + searchDist; y++) {
				for (int z = playerFeet.getZ() - searchDist; z <= playerFeet.getZ() + searchDist; z++) {
					// crucial to only add blocks we can see because otherwise this
					// is an x-ray and it'll get caught
					if (filter.has(bsi.get0(x, y, z))) {
						BlockPos pos = new BlockPos(x, y, z);
						if (Baritone.settings().legitMineIncludeDiagonals.value && knownOreLocations.stream().anyMatch(ore -> ore.getSquaredDistance(pos) <= 2 /* sq means this is pytha dist <= sqrt(2) */) || BRotationUtils.reachable(ctx.player(), pos, fakedBlockReachDistance).isPresent()) {
							knownOreLocations.add(pos);
						}
					}
				}
			}
		}
		knownOreLocations = prune(new CalculationContext(baritone), knownOreLocations, filter, ORE_LOCATIONS_COUNT, blacklist, dropped);
	}

	private Goal coalesce(BlockPos loc, List<BlockPos> locs, CalculationContext context) {
		boolean assumeVerticalShaftMine = !(baritone.bsi.get0(loc.up()).getBlock() instanceof FallingBlock);
		if (!Baritone.settings().forceInternalMining.value) {
			if (assumeVerticalShaftMine)
				// we can get directly below the block
				return new GoalThreeBlocks(loc);
			else
				// we need to get feet or head into the block
				return new GoalTwoBlocks(loc);
		}
		boolean upwardGoal = internalMiningGoal(loc.up(), context, locs);
		boolean downwardGoal = internalMiningGoal(loc.down(), context, locs);
		boolean doubleDownwardGoal = internalMiningGoal(loc.down(2), context, locs);
		if (upwardGoal == downwardGoal) { // symmetric
			if (doubleDownwardGoal && assumeVerticalShaftMine)
				// we have a checkerboard like pattern
				// this one, and the one two below it
				// therefore it's fine to path to immediately below this one, since your feet will be in the doubleDownwardGoal
				// but only if assumeVerticalShaftMine
				return new GoalThreeBlocks(loc);
			else
				// this block has nothing interesting two below, but is symmetric vertically so we can get either feet or head into it
				return new GoalTwoBlocks(loc);
		}
		if (upwardGoal)
			// downwardGoal known to be false
			// ignore the gap then potential doubleDownward, because we want to path feet into this one and head into upwardGoal
			return new GoalBlock(loc);
		// upwardGoal known to be false, downwardGoal known to be true
		if (doubleDownwardGoal && assumeVerticalShaftMine)
			// this block and two below it are goals
			// path into the center of the one below, because that includes directly below this one
			return new GoalTwoBlocks(loc.down());
		// upwardGoal false, downwardGoal true, doubleDownwardGoal false
		// just this block and the one immediately below, no others
		return new GoalBlock(loc.down());
	}

	@Override
	public String displayName0() {
		return "Mine " + filter;
	}

	public List<BlockPos> droppedItemsScan() {
		if (!Baritone.settings().mineScanDroppedItems.value)
			return Collections.emptyList();
		List<BlockPos> ret = new ArrayList<>();
		for (Entity entity : ((ClientWorld) ctx.world()).getEntities()) {
			if (entity instanceof ItemEntity) {
				ItemEntity ei = (ItemEntity) entity;
				if (filter.has(ei.getStack())) {
					ret.add(new BlockPos(entity));
				}
			}
		}
		ret.addAll(anticipatedDrops.keySet());
		return ret;
	}

	private boolean internalMiningGoal(BlockPos pos, CalculationContext context, List<BlockPos> locs) {
		// Here, BlockStateInterface is used because the position may be in a cached chunk (the targeted block is one that is kept track of)
		if (locs.contains(pos))
			return true;
		BlockState state = context.bsi.get0(pos);
		if (Baritone.settings().internalMiningAirException.value && state.getBlock() instanceof AirBlock)
			return true;
		return filter.has(state) && plausibleToBreak(context, pos);
	}

	@Override
	public boolean isActive() {
		return filter != null;
	}

	@Override
	public void mine(int quantity, BlockOptionalMetaLookup filter) {
		this.filter = filter;
		if (filter != null && !Baritone.settings().allowBreak.value) {
			logDirect("Unable to mine when allowBreak is false!");
			this.mine(quantity, (BlockOptionalMetaLookup) null);
			return;
		}
		desiredQuantity = quantity;
		knownOreLocations = new ArrayList<>();
		blacklist = new ArrayList<>();
		branchPoint = null;
		branchPointRunaway = null;
		anticipatedDrops = new HashMap<>();
		if (filter != null) {
			rescan(new ArrayList<>(), new CalculationContext(baritone));
		}
	}

	@Override
	public void mineByName(int quantity, String... blocks) {
		mine(quantity, new BlockOptionalMetaLookup(blocks));
	}

	@Override
	public void onLostControl() {
		mine(0, (BlockOptionalMetaLookup) null);
	}

	boolean a = false;
	int b = 0;
	BlockPos chest = null;
	boolean d = false;
	@Override
	public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
		if (a) {
			b++;
			if (b > 10) {
				WurstClient.IMC.rightClick();
				WurstClient.MC.player.swingHand(Hand.MAIN_HAND);
				a = false;
				b = 0;
				d = true;
			}
			return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
		}
		if (WurstClient.INSTANCE.getHax().autoStoreHack.forced) {
			return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
		} else {
			a = false;
			if (d) {
				if (WurstClient.MC.currentScreen != null) WurstClient.MC.currentScreen.onClose();
				d = false;
			}
		}
		int haschest = -1;
		for (int i = 0; i < 9; i++) {
			if (WurstClient.MC.player.inventory.getInvStack(i).getItem().equals(Items.CHEST)) {
				haschest = i;
				break;
			}
		}
		boolean full = true;
		if (haschest >= 0)
			for (int i = 9; i < 36; i++) {
				if (WurstClient.MC.player.inventory.getInvStack(i).isEmpty()) {
					full = false;
					break;
				}
			}
		if (haschest >= 0)
			if (full && !a) {
				int reach = 3;
				int x = WurstClient.MC.player.getBlockPos().getX();
				int y = WurstClient.MC.player.getBlockPos().getY();
				int z = WurstClient.MC.player.getBlockPos().getZ();
				for (int xx = -reach; xx <= reach; xx++) {
					for (int yy = -reach; yy <= reach; yy++) {
						for (int zz = -reach; zz <= reach; zz++) {
							BlockPos pos = new BlockPos(xx + x, yy + y, zz + z);
							if (WurstClient.MC.world.getBlockState(new BlockPos(xx + x, yy + y + 1, zz + z)).isAir()) {
								WurstClient.MC.player.inventory.selectedSlot = haschest;
								if (tryToPlace(pos, Math.pow(4.25, 2))) {
									System.out.println(pos.toShortString());
									a = true;
									WurstClient.INSTANCE.getHax().autoStoreHack.setEnabled(true);
									WurstClient.INSTANCE.getHax().autoStoreHack.forced = true;
									
									return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
								}
							}
						}
					}
				}
			}
		if (desiredQuantity > 0) {
			int curr = ctx.player().inventory.main.stream().filter(stack -> filter.has(stack)).mapToInt(ItemStack::getCount).sum();
			System.out.println("Currently have " + curr + " valid items");
			if (curr >= desiredQuantity) {
				logDirect("Have " + curr + " valid items");
				cancel();
				return null;
			}
		}
		if (calcFailed) {
			if (!knownOreLocations.isEmpty() && Baritone.settings().blacklistClosestOnFailure.value) {
				logDirect("Unable to find any path to " + filter + ", blacklisting presumably unreachable closest instance...");
				knownOreLocations.stream().min(Comparator.comparingDouble(ctx.playerFeet()::getSquaredDistance)).ifPresent(blacklist::add);
				knownOreLocations.removeIf(blacklist::contains);
			} else {
				logDirect("Unable to find any path to " + filter + ", canceling mine");
				cancel();
				return null;
			}
		}
		if (!Baritone.settings().allowBreak.value) {
			logDirect("Unable to mine when allowBreak is false!");
			cancel();
			return null;
		}
		updateLoucaSystem();
		int mineGoalUpdateInterval = Baritone.settings().mineGoalUpdateInterval.value;
		List<BlockPos> curr = new ArrayList<>(knownOreLocations);
		if (mineGoalUpdateInterval != 0 && tickCount++ % mineGoalUpdateInterval == 0) { // big brain
			CalculationContext context = new CalculationContext(baritone, true);
			Baritone.getExecutor().execute(() -> rescan(curr, context));
		}
		if (Baritone.settings().legitMine.value) {
			addNearby();
		}

		Optional<BlockPos> shaft = curr.stream().filter(pos -> pos.getX() == ctx.playerFeet().getX() && pos.getZ() == ctx.playerFeet().getZ()).filter(pos -> pos.getY() >= ctx.playerFeet().getY()).filter(pos -> !(BlockStateInterface.get(ctx, pos).getBlock() instanceof AirBlock)) // after breaking a block, it takes mineGoalUpdateInterval ticks for it to actually update this list =(
				.min(Comparator.comparingDouble(ctx.playerFeet()::getSquaredDistance));
		baritone.getInputOverrideHandler().clearAllKeys();
		if (shaft.isPresent() && ctx.player().onGround) {
			BlockPos pos = shaft.get();
			BlockState state = baritone.bsi.get0(pos);
			if (!MovementHelper.avoidBreaking(baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
				Optional<Rotation> rot = BRotationUtils.reachable(ctx, pos);
				if (rot.isPresent() && isSafeToCancel) {
					baritone.getLookBehavior().updateTarget(rot.get(), true);
					MovementHelper.switchToBestToolFor(ctx, ctx.world().getBlockState(pos));
					if (ctx.isLookingAt(pos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
						baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
					}
					return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
				}
			}
		}
		PathingCommand command = updateGoal();
		if (command == null) {
			// none in range
			// maybe say something in chat? (ahem impact)
			cancel();
			return null;
		}
		return command;
	}

	private boolean faceVectorClient(Vec3d vec) {
		net.wurstclient.util.RotationUtils.Rotation rotation = RotationUtils.getNeededRotations(vec);

		float oldYaw = WurstClient.MC.player.prevYaw;
		float oldPitch = WurstClient.MC.player.prevPitch;

		WurstClient.MC.player.yaw = limitAngleChange(oldYaw, rotation.getYaw(), 30);
		WurstClient.MC.player.pitch = rotation.getPitch();

		return Math.abs(oldYaw - rotation.getYaw()) + Math.abs(oldPitch - rotation.getPitch()) < 1F;
	}

	private float limitAngleChange(float current, float intended, float maxChange) {
		float change = MathHelper.wrapDegrees(intended - current);
		change = MathHelper.clamp(change, -maxChange, maxChange);
		return MathHelper.wrapDegrees(current + change);
	}

	boolean c = false;

	private boolean tryToClick(BlockPos pos) {
		// face block
		//faceVectorClient(VecUtils.calculateBlockCenter(WurstClient.MC.world, pos));
		//WurstClient.IMC.rightClick();
		// place block
		Vec3d posVec = new Vec3d(pos).add(0.5, 0.5, 0.5);
		net.wurstclient.util.RotationUtils.Rotation place = RotationUtils.getNeededRotations(posVec.add(new Vec3d(Direction.NORTH.getVector()).multiply(0.5)));
		baritone.getLookBehavior().updateTarget(new Rotation(place.getYaw(), place.getPitch()), true);
		WurstClient.IMC.rightClick();
		WurstClient.MC.player.swingHand(Hand.MAIN_HAND);
		System.out.println("bbb");
		return true;

	}

	private void placeBlockSimple(BlockPos pos) {
		Direction side = null;
		Direction[] sides = Direction.values();

		Vec3d eyesPos = RotationUtils.getEyesPos();
		Vec3d posVec = new Vec3d(pos).add(0.5, 0.5, 0.5);
		double distanceSqPosVec = eyesPos.squaredDistanceTo(posVec);

		Vec3d[] hitVecs = new Vec3d[sides.length];
		for (int i = 0; i < sides.length; i++) {
			hitVecs[i] = posVec.add(new Vec3d(sides[i].getVector()).multiply(0.5));
		}

		if (side == null) {
			for (int i = 0; i < sides.length; i++) {
				// check if neighbor can be right clicked
				if (!net.wurstclient.util.BlockUtils.canBeClicked(pos)) {
					continue;
				}

				// check if side is facing away from player
				if (distanceSqPosVec > eyesPos.squaredDistanceTo(hitVecs[i])) {
					continue;
				}

				side = sides[i];
				break;
			}
		}

		if (side == null)
			return;

		Vec3d hitVec = hitVecs[side.ordinal()];

		// face block
		WurstClient.INSTANCE.getRotationFaker().faceVectorPacket(hitVec);
		if (RotationUtils.getAngleToLastReportedLookVec(hitVec) > 1)
			return;

		// check timer
		if (WurstClient.IMC.getItemUseCooldown() > 0)
			return;

		// place block
		WurstClient.IMC.getInteractionManager().rightClickBlock(pos.offset(side), side.getOpposite(), hitVec);

		// swing arm
		WurstClient.MC.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

		// reset timer
		WurstClient.IMC.setItemUseCooldown(4);
	}

	private boolean tryToPlace(BlockPos pos, double rangeSq) {
		Vec3d eyesPos = RotationUtils.getEyesPos();
		Vec3d posVec = new Vec3d(pos).add(0.5, 0.5, 0.5);
		double distanceSqPosVec = eyesPos.squaredDistanceTo(posVec);

		if (WurstClient.MC.world.getCollisions(null, new Box(pos, pos), null).count() > 0) {
			return false;
		}
		for (Direction side : Direction.values()) {
			BlockPos neighbor = pos.offset(side);
			if (net.wurstclient.util.BlockUtils.getState(neighbor).getBlock() instanceof ChestBlock) {
				return false;
			}
		}
		for (Direction side : Direction.values()) {
			BlockPos neighbor = pos.offset(side);

			// check if neighbor can be right clicked
			if (!net.wurstclient.util.BlockUtils.canBeClicked(neighbor) || net.wurstclient.util.BlockUtils.getState(neighbor).getMaterial().isReplaceable()) {
				continue;
			}

			Vec3d dirVec = new Vec3d(side.getVector());
			Vec3d hitVec = posVec.add(dirVec.multiply(0.5));

			// check if hitVec is within range
			if (eyesPos.squaredDistanceTo(hitVec) > rangeSq) {
				continue;
			}

			// check if side is visible (facing away from player)
			if (distanceSqPosVec > eyesPos.squaredDistanceTo(posVec.add(dirVec))) {
				continue;
			}

			if (WurstClient.MC.world.rayTrace(new RayTraceContext(eyesPos, hitVec, RayTraceContext.ShapeType.COLLIDER, RayTraceContext.FluidHandling.NONE, WurstClient.MC.player)).getType() != HitResult.Type.MISS) {
				continue;
			}
			// face block
			net.wurstclient.util.RotationUtils.Rotation rot = RotationUtils.getNeededRotations(hitVec);
			baritone.getLookBehavior().updateTarget(new Rotation(rot.getYaw(), rot.getPitch()), true);
			//WurstClient.IMC.rightClick();
			// place block
			WurstClient.IMC.getInteractionManager().rightClickBlock(neighbor, side.getOpposite(), hitVec);
			//WurstClient.MC.player.swingHand(Hand.MAIN_HAND);
			return true;
		}

		return false;
	}

	private void rescan(List<BlockPos> already, CalculationContext context) {
		if (filter == null)
			return;
		if (Baritone.settings().legitMine.value)
			return;
		List<BlockPos> dropped = droppedItemsScan();
		List<BlockPos> locs = searchWorld(context, filter, ORE_LOCATIONS_COUNT, already, blacklist, dropped);
		locs.addAll(dropped);
		if (locs.isEmpty()) {
			logDirect("No locations for " + filter + " known, cancelling");
			cancel();
			return;
		}
		knownOreLocations = locs;
	}

	private PathingCommand updateGoal() {
		if (WurstClient.INSTANCE.getHax().autoStoreHack.forced) {
			return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
		}
		boolean legit = Baritone.settings().legitMine.value;
		List<BlockPos> locs = knownOreLocations;
		if (!locs.isEmpty()) {
			CalculationContext context = new CalculationContext(baritone);
			List<BlockPos> locs2 = prune(context, new ArrayList<>(locs), filter, ORE_LOCATIONS_COUNT, blacklist, droppedItemsScan());
			// can't reassign locs, gotta make a new var locs2, because we use it in a lambda right here, and variables you use in a lambda must be effectively final
			Goal goal = new GoalComposite(locs2.stream().map(loc -> coalesce(loc, locs2, context)).toArray(Goal[]::new));
			knownOreLocations = locs2;
			return new PathingCommand(goal, legit ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
		}
		// we don't know any ore locations at the moment
		if (!legit)
			return null;
		// only in non-Xray mode (aka legit mode) do we do this
		int y = Baritone.settings().legitMineYLevel.value;
		if (branchPoint == null) {
			/*
			 * if (!baritone.getPathingBehavior().isPathing() && playerFeet().y == y) { // cool, path is over and we are at desired y branchPoint = playerFeet(); branchPointRunaway = null; } else { return new GoalYLevel(y); }
			 */
			branchPoint = ctx.playerFeet();
		}
		// TODO shaft mode, mine 1x1 shafts to either side
		// TODO also, see if the GoalRunAway with maintain Y at 11 works even from the surface
		if (branchPointRunaway == null) {
			branchPointRunaway = new GoalRunAway(1, y, branchPoint) {
				@Override
				public boolean isInGoal(int x, int y, int z) {
					return false;
				}
			};
		}
		return new PathingCommand(branchPointRunaway, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
	}

	private void updateLoucaSystem() {
		Map<BlockPos, Long> copy = new HashMap<>(anticipatedDrops);
		ctx.getSelectedBlock().ifPresent(pos -> {
			if (knownOreLocations.contains(pos)) {
				copy.put(pos, System.currentTimeMillis() + Baritone.settings().mineDropLoiterDurationMSThanksLouca.value);
			}
		});
		// elaborate dance to avoid concurrentmodificationexcepption since rescan thread reads this
		// don't want to slow everything down with a gross lock do we now
		for (BlockPos pos : anticipatedDrops.keySet()) {
			if (copy.get(pos) < System.currentTimeMillis()) {
				copy.remove(pos);
			}
		}
		anticipatedDrops = copy;
	}
}
