/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.behavior;

import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;
import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.utils.ToolSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.container.SlotActionType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ToolItem;
import net.minecraft.util.DefaultedList;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class InventoryBehavior extends Behavior {

	public InventoryBehavior(Baritone baritone) {
		super(baritone);
	}

	public void attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar) {
		OptionalInt destination = getTempHotbarSlot(disallowedHotbar);
		if (destination.isPresent()) {
			swapWithHotBar(inMainInvy, destination.getAsInt());
		}
	}

	private int bestToolAgainst(Block against, Class<? extends ToolItem> cla$$) {
		DefaultedList<ItemStack> invy = ctx.player().inventory.main;
		int bestInd = -1;
		double bestSpeed = -1;
		for (int i = 0; i < invy.size(); i++) {
			ItemStack stack = invy.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (cla$$.isInstance(stack.getItem())) {
				double speed = ToolSet.calculateSpeedVsBlock(stack, against.getDefaultState()); // takes into account enchants
				if (speed > bestSpeed) {
					bestSpeed = speed;
					bestInd = i;
				}
			}
		}
		return bestInd;
	}

	private int firstValidThrowaway() { // TODO offhand idk
		DefaultedList<ItemStack> invy = ctx.player().inventory.main;
		for (int i = 0; i < invy.size(); i++) {
			if (Baritone.settings().acceptableThrowawayItems.value.contains(invy.get(i).getItem()))
				return i;
		}
		return -1;
	}

	public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
		// we're using 0 and 8 for pickaxe and throwaway
		ArrayList<Integer> candidates = new ArrayList<>();
		for (int i = 1; i < 8; i++) {
			if (ctx.player().inventory.main.get(i).isEmpty() && !disallowedHotbar.test(i)) {
				candidates.add(i);
			}
		}
		if (candidates.isEmpty()) {
			for (int i = 1; i < 8; i++) {
				if (!disallowedHotbar.test(i)) {
					candidates.add(i);
				}
			}
		}
		if (candidates.isEmpty())
			return OptionalInt.empty();
		return OptionalInt.of(candidates.get(new Random().nextInt(candidates.size())));
	}

	public boolean hasGenericThrowaway() {
		for (Item item : Baritone.settings().acceptableThrowawayItems.value) {
			if (throwaway(false, stack -> item.equals(stack.getItem())))
				return true;
		}
		return false;
	}

	@Override
	public void onTick(TickEvent event) {
		if (!Baritone.settings().allowInventory.value)
			return;
		if (event.getType() == TickEvent.Type.OUT)
			return;
		if (ctx.player().container != ctx.player().container)
			// we have a crafting table or a chest or something open
			return;
		if (firstValidThrowaway() >= 9) { // aka there are none on the hotbar, but there are some in main inventory
			swapWithHotBar(firstValidThrowaway(), 8);
		}
		int pick = bestToolAgainst(Blocks.STONE, PickaxeItem.class);
		if (pick >= 9) {
			swapWithHotBar(pick, 0);
		}
	}

	public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
		BlockState maybe = baritone.getBuilderProcess().placeAt(x, y, z, baritone.bsi.get0(x, y, z));
		if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && maybe.equals(((BlockItem) stack.getItem()).getBlock().getPlacementState(new ItemPlacementContext(new ItemUsageContext(ctx.world(), ctx.player(), Hand.MAIN_HAND, stack, new BlockHitResult(new Vec3d(ctx.player().getX(), ctx.player().getY(), ctx.player().getZ()), Direction.UP, ctx.playerFeet(), false)) {
		})))))
			return true; // gotem
		if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock().equals(maybe.getBlock())))
			return true;
		for (Item item : Baritone.settings().acceptableThrowawayItems.value) {
			if (throwaway(select, stack -> item.equals(stack.getItem())))
				return true;
		}
		return false;
	}

	private void swapWithHotBar(int inInventory, int inHotbar) {
		ctx.playerController().windowClick(ctx.player().container.syncId, inInventory < 9 ? inInventory + 36 : inInventory, inHotbar, SlotActionType.SWAP, ctx.player());
	}

	public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
		ClientPlayerEntity p = ctx.player();
		DefaultedList<ItemStack> inv = p.inventory.main;
		for (int i = 0; i < 9; i++) {
			ItemStack item = inv.get(i);
			// this usage of settings() is okay because it's only called once during pathing
			// (while creating the CalculationContext at the very beginning)
			// and then it's called during execution
			// since this function is never called during cost calculation, we don't need to migrate
			// acceptableThrowawayItems to the CalculationContext
			if (desired.test(item)) {
				if (select) {
					p.inventory.selectedSlot = i;
				}
				return true;
			}
		}
		if (desired.test(p.inventory.offHand.get(0))) {
			// main hand takes precedence over off hand
			// that means that if we have block A selected in main hand and block B in off hand, right clicking places block B
			// we've already checked above ^ and the main hand can't possible have an acceptablethrowawayitem
			// so we need to select in the main hand something that doesn't right click
			// so not a shovel, not a hoe, not a block, etc
			for (int i = 0; i < 9; i++) {
				ItemStack item = inv.get(i);
				if (item.isEmpty() || item.getItem() instanceof PickaxeItem) {
					if (select) {
						p.inventory.selectedSlot = i;
					}
					return true;
				}
			}
		}
		return false;
	}
}
