/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import baritone.Baritone;
import baritone.api.cache.IWaypoint;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BRotationUtils;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.container.Slot;
import net.minecraft.container.SlotActionType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DefaultedList;
import net.minecraft.util.math.BlockPos;

public abstract class BaritoneProcessHelper implements IBaritoneProcess, Helper {

	protected final Baritone baritone;
	protected final IPlayerContext ctx;

	public BaritoneProcessHelper(Baritone baritone) {
		this.baritone = baritone;
		ctx = baritone.getPlayerContext();
		baritone.getPathingControlManager().registerProcess(this);
	}

	@Override
	public boolean isTemporary() {
		return false;
	}

	public void returnhome() {
		IWaypoint waypoint = baritone.getWorldProvider().getCurrentWorld().getWaypoints().getMostRecentByTag(IWaypoint.Tag.HOME);
		if (waypoint != null) {
			Goal goal = new GoalBlock(waypoint.getLocation());
			baritone.getCustomGoalProcess().setGoalAndPath(goal);
		} else {
			logDirect("No recent waypoint found, can't return home");
		}
	}

	public boolean putInventoryInChest(Set<Item> validDrops) {
		List<Slot> chestInv = ctx.player().container.slots;
		DefaultedList<ItemStack> inv = ctx.player().inventory.main;

		for (int i = 0; i < inv.size(); i++) {
			if (!inv.isEmpty() && validDrops.contains(inv.get(i).getItem())) {
				for (int j = 0; j < chestInv.size() - inv.size(); j++) {
					if (chestInv.get(j).getStack().isEmpty()) {
						ctx.playerController().windowClick(ctx.player().container.syncId, i < 9 ? chestInv.size() - 9 + i : chestInv.size() - inv.size() + i - 9, 0, SlotActionType.QUICK_MOVE, ctx.player());
						return false;
					}
				}
			}
		}
		return true;
	}

	public boolean isInventoryFull() {
		DefaultedList<ItemStack> inv = ctx.player().inventory.main;
		boolean inventoryFull = true;

		for (ItemStack stack : inv) {
			if (stack.isEmpty()) {
				inventoryFull = false;
				break;
			}
		}

		return inventoryFull;
	}

	public Set<ItemStack> notFullStacks(Set<Item> validDrops) {
		DefaultedList<ItemStack> inv = ctx.player().inventory.main;

		Set<ItemStack> stacks = inv.stream().filter(stack -> validDrops.contains(stack.getItem()) && stack.getMaxCount() != stack.getCount()).collect(Collectors.toCollection(HashSet::new));

		return stacks;
	}

	public BlockOptionalMetaLookup getBlacklistBlocks(Set<ItemStack> notFullStacks, BlockOptionalMetaLookup filter) {
		List<BlockOptionalMeta> blacklistBlocks = new ArrayList<>();

		for (BlockOptionalMeta bom : filter.blocks()) {
			boolean blacklist = true;
			for (ItemStack stack : notFullStacks) {
				if (bom.matches(stack)) {
					blacklist = false;
					break;
				}
			}
			if (blacklist)
				blacklistBlocks.add(bom);
		}

		return new BlockOptionalMetaLookup(blacklistBlocks.toArray(new BlockOptionalMeta[blacklistBlocks.size()]));
	}

	public PathingCommand gotoChest(boolean isSafeToCancel) {
		IWaypoint waypoint = baritone.getWorldProvider().getCurrentWorld().getWaypoints().getMostRecentByTag(IWaypoint.Tag.USECHEST);
		IWaypoint chestLoc = baritone.getWorldProvider().getCurrentWorld().getWaypoints().getMostRecentByTag(IWaypoint.Tag.CHEST);
		if (chestLoc != null && waypoint != null) {
			BlockPos chest = chestLoc.getLocation();
			if (waypoint.getLocation().getSquaredDistance(chest.getX(), chest.getY(), chest.getZ(), true) < 6) {
				Goal goal = new GoalBlock(waypoint.getLocation());
				if (goal.isInGoal(ctx.playerFeet()) && goal.isInGoal(baritone.getPathingBehavior().pathStart())) {
					Optional<Rotation> rot = BRotationUtils.reachable(ctx, chest);
					if (rot.isPresent() && isSafeToCancel) {
						baritone.getLookBehavior().updateTarget(rot.get(), true);
						if (ctx.isLookingAt(chest)) {
							if (ctx.player().container == ctx.player().playerContainer) {
								baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
							} else {
								baritone.getInputOverrideHandler().clearAllKeys();
							}
						}
						return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
					}
				} else {
					return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
				}
			} else {
				logDirect("Chest not properly set, please use #setchest again");
			}
		} else {
			logDirect("No chest set, please use #setchest");
		}

		return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
	}
}
