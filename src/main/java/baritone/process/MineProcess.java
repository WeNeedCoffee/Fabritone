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

package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.*;
import baritone.api.process.IMineProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BlockUtils;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.cache.CachedChunk;
import baritone.cache.WorldScanner;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import net.minecraft.block.*;
import net.minecraft.client.resource.ClientResourcePackCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.loot.LootManager;
import net.minecraft.world.loot.LootTables;
import net.minecraft.world.loot.context.LootContext;
import net.minecraft.world.loot.context.LootContextParameter;
import net.minecraft.world.loot.context.LootContextParameters;
import net.minecraft.world.loot.context.LootContextTypes;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static baritone.api.pathing.movement.ActionCosts.COST_INF;

/**
 * Mine blocks of a certain type
 *
 * @author leijurv
 */
public final class MineProcess extends BaritoneProcessHelper implements IMineProcess {

    private static final int ORE_LOCATIONS_COUNT = 64;

    private List<Block> mining;
    private List<BlockPos> knownOreLocations;
    private List<BlockPos> blacklist; // inaccessible
    private BlockPos branchPoint;
    private GoalRunAway branchPointRunaway;
    private int desiredQuantity;
    private int tickCount;

    private static LootManager manager;
    private static Map<Block, List<Item>> drops = new HashMap<>();

    public MineProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return mining != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (desiredQuantity > 0) {
            List<Item> item = drops(mining.get(0));
            int curr = ctx.player().inventory.main.stream().filter(stack -> item.contains(stack.getItem())).mapToInt(ItemStack::getCount).sum();
            System.out.println("Currently have " + curr + " " + item);
            if (curr >= desiredQuantity) {
                logDirect("Have " + curr);
                cancel();
                return null;
            }
        }
        if (calcFailed) {
            if (!knownOreLocations.isEmpty() && Baritone.settings().blacklistClosestOnFailure.value) {
                logDirect("Unable to find any path to " + mining + ", blacklisting presumably unreachable closest instance...");
                knownOreLocations.stream().min(Comparator.comparingDouble(ctx.playerFeet()::getSquaredDistance)).ifPresent(blacklist::add);
                knownOreLocations.removeIf(blacklist::contains);
            } else {
                logDirect("Unable to find any path to " + mining + ", canceling Mine");
                cancel();
                return null;
            }
        }
        if (!Baritone.settings().allowBreak.value) {
            logDirect("Unable to mine when allowBreak is false!");
            cancel();
            return null;
        }
        int mineGoalUpdateInterval = Baritone.settings().mineGoalUpdateInterval.value;
        List<BlockPos> curr = new ArrayList<>(knownOreLocations);
        if (mineGoalUpdateInterval != 0 && tickCount++ % mineGoalUpdateInterval == 0) { // big brain
            CalculationContext context = new CalculationContext(baritone, true);
            Baritone.getExecutor().execute(() -> rescan(curr, context));
        }
        if (Baritone.settings().legitMine.value) {
            addNearby();
        }
        Optional<BlockPos> shaft = curr.stream()
                .filter(pos -> pos.getX() == ctx.playerFeet().getX() && pos.getZ() == ctx.playerFeet().getZ())
                .filter(pos -> pos.getY() >= ctx.playerFeet().getY())
                .filter(pos -> !(BlockStateInterface.get(ctx, pos).getBlock() instanceof AirBlock)) // after breaking a block, it takes mineGoalUpdateInterval ticks for it to actually update this list =(
                .min(Comparator.comparingDouble(ctx.playerFeet()::getSquaredDistance));
        baritone.getInputOverrideHandler().clearAllKeys();
        if (shaft.isPresent() && ctx.player().onGround) {
            BlockPos pos = shaft.get();
            BlockState state = baritone.bsi.get0(pos);
            if (!MovementHelper.avoidBreaking(baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
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

    public static LootManager getManager() {
        if (manager == null) {
            ResourcePackContainerManager rpl = new ResourcePackContainerManager<>(ResourcePackContainer::new);
            rpl.addCreator(new DefaultResourcePackCreator());
            rpl.callCreators();
            ResourcePack thePack = ((ResourcePackContainer) rpl.getAlphabeticallyOrderedContainers().iterator().next()).createResourcePack();
            ReloadableResourceManager resourceManager = new ReloadableResourceManagerImpl(ResourceType.SERVER_DATA, null);
            manager = new LootManager();
            resourceManager.registerListener(manager);
            try {
                resourceManager.beginReload(Baritone.getExecutor(), Baritone.getExecutor(), Collections.singletonList(thePack), CompletableFuture.completedFuture(Unit.INSTANCE)).get();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
        return manager;
    }

    private static List<Item> drops(Block b) {
        return drops.computeIfAbsent(b, block -> {
            Identifier lootTableLocation = block.getDropTableId();
            if (lootTableLocation == LootTables.EMPTY) {
                return Collections.emptyList();
            } else {
                return getManager().getSupplier(lootTableLocation).getDrops(new LootContext.Builder(null).setRandom(new Random()).put(LootContextParameters.POSITION, BlockPos.ORIGIN).put(LootContextParameters.TOOL, ItemStack.EMPTY).putNullable(LootContextParameters.BLOCK_ENTITY, null).put(LootContextParameters.BLOCK_STATE, block.getDefaultState()).build(LootContextTypes.BLOCK)).stream().map(ItemStack::getItem).collect(Collectors.toList());
            }
        });
    }

    @Override
    public void onLostControl() {
        mine(0, (Block[]) null);
    }

    @Override
    public String displayName0() {
        return "Mine " + mining;
    }

    private PathingCommand updateGoal() {
        boolean legit = Baritone.settings().legitMine.value;
        List<BlockPos> locs = knownOreLocations;
        if (!locs.isEmpty()) {
            List<BlockPos> locs2 = prune(new CalculationContext(baritone), new ArrayList<>(locs), mining, ORE_LOCATIONS_COUNT, blacklist);
            // can't reassign locs, gotta make a new var locs2, because we use it in a lambda right here, and variables you use in a lambda must be effectively final
            Goal goal = new GoalComposite(locs2.stream().map(loc -> coalesce(loc, locs2)).toArray(Goal[]::new));
            knownOreLocations = locs2;
            return new PathingCommand(goal, legit ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        }
        // we don't know any ore locations at the moment
        if (!legit) {
            return null;
        }
        // only in non-Xray mode (aka legit mode) do we do this
        int y = Baritone.settings().legitMineYLevel.value;
        if (branchPoint == null) {
            /*if (!baritone.getPathingBehavior().isPathing() && playerFeet().y == y) {
                // cool, path is over and we are at desired y
                branchPoint = playerFeet();
                branchPointRunaway = null;
            } else {
                return new GoalYLevel(y);
            }*/
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

    private void rescan(List<BlockPos> already, CalculationContext context) {
        if (mining == null) {
            return;
        }
        if (Baritone.settings().legitMine.value) {
            return;
        }
        List<BlockPos> locs = searchWorld(context, mining, ORE_LOCATIONS_COUNT, already, blacklist);
        locs.addAll(droppedItemsScan(mining, ctx.world()));
        if (locs.isEmpty()) {
            logDirect("No locations for " + mining + " known, cancelling");
            cancel();
            return;
        }
        knownOreLocations = locs;
    }

    private boolean internalMiningGoal(BlockPos pos, IPlayerContext ctx, List<BlockPos> locs) {
        // Here, BlockStateInterface is used because the position may be in a cached chunk (the targeted block is one that is kept track of)
        if (locs.contains(pos)) {
            return true;
        }
        Block block = BlockStateInterface.getBlock(ctx, pos);
        if (Baritone.settings().internalMiningAirException.value && block instanceof AirBlock) {
            return true;
        }
        return mining.contains(block);
    }

    private Goal coalesce(BlockPos loc, List<BlockPos> locs) {
        boolean assumeVerticalShaftMine = !(baritone.bsi.get0(loc.up()).getBlock() instanceof FallingBlock);
        if (!Baritone.settings().forceInternalMining.value) {
            if (assumeVerticalShaftMine) {
                // we can get directly below the block
                return new GoalThreeBlocks(loc);
            } else {
                // we need to get feet or head into the block
                return new GoalTwoBlocks(loc);
            }
        }
        boolean upwardGoal = internalMiningGoal(loc.up(), ctx, locs);
        boolean downwardGoal = internalMiningGoal(loc.down(), ctx, locs);
        boolean doubleDownwardGoal = internalMiningGoal(loc.down(2), ctx, locs);
        if (upwardGoal == downwardGoal) { // symmetric
            if (doubleDownwardGoal && assumeVerticalShaftMine) {
                // we have a checkerboard like pattern
                // this one, and the one two below it
                // therefore it's fine to path to immediately below this one, since your feet will be in the doubleDownwardGoal
                // but only if assumeVerticalShaftMine
                return new GoalThreeBlocks(loc);
            } else {
                // this block has nothing interesting two below, but is symmetric vertically so we can get either feet or head into it
                return new GoalTwoBlocks(loc);
            }
        }
        if (upwardGoal) {
            // downwardGoal known to be false
            // ignore the gap then potential doubleDownward, because we want to path feet into this one and head into upwardGoal
            return new GoalBlock(loc);
        }
        // upwardGoal known to be false, downwardGoal known to be true
        if (doubleDownwardGoal && assumeVerticalShaftMine) {
            // this block and two below it are goals
            // path into the center of the one below, because that includes directly below this one
            return new GoalTwoBlocks(loc.down());
        }
        // upwardGoal false, downwardGoal true, doubleDownwardGoal false
        // just this block and the one immediately below, no others
        return new GoalBlock(loc.down());
    }

    private static class GoalThreeBlocks extends GoalTwoBlocks {

        public GoalThreeBlocks(BlockPos pos) {
            super(pos);
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            return x == this.x && (y == this.y || y == this.y - 1 || y == this.y - 2) && z == this.z;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            int xDiff = x - this.x;
            int yDiff = y - this.y;
            int zDiff = z - this.z;
            return GoalBlock.calculate(xDiff, yDiff < -1 ? yDiff + 2 : yDiff == -1 ? 0 : yDiff, zDiff);
        }
    }


    public static List<BlockPos> droppedItemsScan(List<Block> mining, World world) {
        if (!Baritone.settings().mineScanDroppedItems.value) {
            return Collections.emptyList();
        }
        Set<Item> searchingFor = new HashSet<>();
        for (Block block : mining) {
            searchingFor.addAll(drops(block));
            searchingFor.add(block.asItem());
        }
        List<BlockPos> ret = new ArrayList<>();
        for (Entity entity : ((ClientWorld) world).getEntities()) {
            if (entity instanceof ItemEntity) {
                ItemEntity ei = (ItemEntity) entity;
                if (searchingFor.contains(ei.getStack().getItem())) {
                    ret.add(new BlockPos(entity));
                }
            }
        }
        return ret;
    }

    public static List<BlockPos> searchWorld(CalculationContext ctx, List<Block> mining, int max, List<BlockPos> alreadyKnown, List<BlockPos> blacklist) {
        List<BlockPos> locs = new ArrayList<>();
        List<Block> uninteresting = new ArrayList<>();
        for (Block m : mining) {
            if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(m)) {
                // maxRegionDistanceSq 2 means adjacent directly or adjacent diagonally; nothing further than that
                locs.addAll(ctx.worldData.getCachedWorld().getLocationsOf(BlockUtils.blockToString(m), Baritone.settings().maxCachedWorldScanCount.value, ctx.getBaritone().getPlayerContext().playerFeet().getX(), ctx.getBaritone().getPlayerContext().playerFeet().getZ(), 2));
            } else {
                uninteresting.add(m);
            }
        }
        locs = prune(ctx, locs, mining, max, blacklist);
        if (locs.isEmpty() || (Baritone.settings().extendCacheOnThreshold.value && locs.size() < max)) {
            uninteresting = mining;
        }
        if (!uninteresting.isEmpty()) {
            locs.addAll(WorldScanner.INSTANCE.scanChunkRadius(ctx.getBaritone().getPlayerContext(), uninteresting, max, 10, 32)); // maxSearchRadius is NOT sq
        }
        locs.addAll(alreadyKnown);
        return prune(ctx, locs, mining, max, blacklist);
    }

    private void addNearby() {
        knownOreLocations.addAll(droppedItemsScan(mining, ctx.world()));
        BlockPos playerFeet = ctx.playerFeet();
        BlockStateInterface bsi = new BlockStateInterface(ctx);
        int searchDist = 10;
        double fakedBlockReachDistance = 20; // at least 10 * sqrt(3) with some extra space to account for positioning within the block
        for (int x = playerFeet.getX() - searchDist; x <= playerFeet.getX() + searchDist; x++) {
            for (int y = playerFeet.getY() - searchDist; y <= playerFeet.getY() + searchDist; y++) {
                for (int z = playerFeet.getZ() - searchDist; z <= playerFeet.getZ() + searchDist; z++) {
                    // crucial to only add blocks we can see because otherwise this
                    // is an x-ray and it'll get caught
                    if (mining.contains(bsi.get0(x, y, z).getBlock())) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if ((Baritone.settings().legitMineIncludeDiagonals.value && knownOreLocations.stream().anyMatch(ore -> ore.getSquaredDistance(pos) <= 2 /* sq means this is pytha dist <= sqrt(2) */)) || RotationUtils.reachable(ctx.player(), pos, fakedBlockReachDistance).isPresent()) {
                            knownOreLocations.add(pos);
                        }
                    }
                }
            }
        }
        knownOreLocations = prune(new CalculationContext(baritone), knownOreLocations, mining, ORE_LOCATIONS_COUNT, blacklist);
    }

    private static List<BlockPos> prune(CalculationContext ctx, List<BlockPos> locs2, List<Block> mining, int max, List<BlockPos> blacklist) {
        List<BlockPos> dropped = droppedItemsScan(mining, ctx.world);
        dropped.removeIf(drop -> {
            for (BlockPos pos : locs2) {
                if (pos.getSquaredDistance(drop) <= 9 && mining.contains(ctx.getBlock(pos.getX(), pos.getY(), pos.getZ())) && MineProcess.plausibleToBreak(ctx, pos)) { // TODO maybe drop also has to be supported? no lava below?
                    return true;
                }
            }
            return false;
        });
        List<BlockPos> locs = locs2
                .stream()
                .distinct()

                // remove any that are within loaded chunks that aren't actually what we want
                .filter(pos -> !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ()) || mining.contains(ctx.getBlock(pos.getX(), pos.getY(), pos.getZ())) || dropped.contains(pos))

                // remove any that are implausible to mine (encased in bedrock, or touching lava)
                .filter(pos -> MineProcess.plausibleToBreak(ctx, pos))

                .filter(pos -> !blacklist.contains(pos))

                .sorted(Comparator.comparingDouble(new BlockPos(ctx.getBaritone().getPlayerContext().player())::getSquaredDistance))
                .collect(Collectors.toList());

        if (locs.size() > max) {
            return locs.subList(0, max);
        }
        return locs;
    }

    public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {
        if (MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), ctx.bsi.get0(pos), true) >= COST_INF) {
            return false;
        }

        // bedrock above and below makes it implausible, otherwise we're good
        return !(ctx.bsi.get0(pos.up()).getBlock() == Blocks.BEDROCK && ctx.bsi.get0(pos.down()).getBlock() == Blocks.BEDROCK);
    }

    @Override
    public void mineByName(int quantity, String... blocks) {
        mine(quantity, blocks == null || blocks.length == 0 ? null : Arrays.stream(blocks).map(BlockUtils::stringToBlockRequired).toArray(Block[]::new));
    }

    @Override
    public void mine(int quantity, Block... blocks) {
        this.mining = blocks == null || blocks.length == 0 ? null : Arrays.asList(blocks);
        if (mining != null && !Baritone.settings().allowBreak.value) {
            logDirect("Unable to mine when allowBreak is false!");
            mining = null;
        }
        this.desiredQuantity = quantity;
        this.knownOreLocations = new ArrayList<>();
        this.blacklist = new ArrayList<>();
        this.branchPoint = null;
        this.branchPointRunaway = null;
        if (mining != null) {
            rescan(new ArrayList<>(), new CalculationContext(baritone));
        }
    }
}
