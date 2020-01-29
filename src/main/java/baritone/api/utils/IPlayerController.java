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

import baritone.api.BaritoneAPI;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.container.SlotActionType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/**
 * @author Brady
 * @since 12/14/2018
 */
public interface IPlayerController {

	boolean clickBlock(BlockPos loc, Direction face);

	default double getBlockReachDistance() {
		return getGameType().isCreative() ? 5.0F : BaritoneAPI.getSettings().blockReachDistance.value;
	}

	GameMode getGameType();

	boolean hasBrokenBlock();

	boolean onPlayerDamageBlock(BlockPos pos, Direction side);

	ActionResult processRightClick(ClientPlayerEntity player, World world, Hand hand);

	ActionResult processRightClickBlock(ClientPlayerEntity player, World world, Hand hand, BlockHitResult result);

	void resetBlockRemoving();

	void setHittingBlock(boolean hittingBlock);

	void syncHeldItem();

	ItemStack windowClick(int windowId, int slotId, int mouseButton, SlotActionType type, PlayerEntity player);
}
