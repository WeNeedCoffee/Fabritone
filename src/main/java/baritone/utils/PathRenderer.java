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

import static org.lwjgl.opengl.GL11.GL_LIGHTING_BIT;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import java.awt.Color;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import com.mojang.blaze3d.platform.GlStateManager;
import baritone.api.BaritoneAPI;
import baritone.api.event.events.RenderEvent;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalInverted;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.behavior.PathingBehavior;
import baritone.pathing.path.PathExecutor;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * @author Brady
 * @since 8/9/2018
 */
public final class PathRenderer implements IRenderer, Helper {

	private static final Identifier TEXTURE_BEACON_BEAM = new Identifier("textures/entity/beacon_beam.png");

	public static void drawDankLitGoalBox(Entity player, Goal goal, float partialTicks, Color color) {
		double minX, maxX;
		double minZ, maxZ;
		double minY, maxY;
		double y1, y2;
		double y = MathHelper.cos((float) (System.nanoTime() / 100000L % 20000L / 20000F * Math.PI * 2));
		if (goal instanceof IGoalRenderPos) {
			BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
			minX = goalPos.getX() + 0.002;
			maxX = goalPos.getX() + 1 - 0.002;
			minZ = goalPos.getZ() + 0.002;
			maxZ = goalPos.getZ() + 1 - 0.002;
			if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
				y /= 2;
			}
			y1 = 1 + y + goalPos.getY();
			y2 = 1 - y + goalPos.getY();
			minY = goalPos.getY();
			maxY = minY + 2;
			if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
				y1 -= 0.5;
				y2 -= 0.5;
				maxY--;
			}
		} else if (goal instanceof GoalXZ) {
			GoalXZ goalPos = (GoalXZ) goal;

			if (settings.renderGoalXZBeacon.value) {
				glPushAttrib(GL_LIGHTING_BIT);

				Helper.mc.getTextureManager().bindTexture(TEXTURE_BEACON_BEAM);
				if (settings.renderGoalIgnoreDepth.value) {
					GlStateManager.disableDepthTest();
				}

				BeaconBlockEntityRenderer.renderLightBeam(new MatrixStack(), Helper.mc.getBufferBuilders().getEntityVertexConsumers(), BeaconBlockEntityRenderer.BEAM_TEX, partialTicks, 1.0f, player.world.getTime(), 0, 256, color.getColorComponents(null),

						// Arguments filled by the private method lol
						0.2f, 0.25f);

				if (settings.renderGoalIgnoreDepth.value) {
					GlStateManager.enableDepthTest();
				}

				glPopAttrib();
				return;
			}

			minX = goalPos.getX() + 0.002;
			maxX = goalPos.getX() + 1 - 0.002;
			minZ = goalPos.getZ() + 0.002;
			maxZ = goalPos.getZ() + 1 - 0.002;

			y1 = 0;
			y2 = 0;
			minY = 0;
			maxY = 256;
		} else if (goal instanceof GoalComposite) {
			for (Goal g : ((GoalComposite) goal).goals()) {
				drawDankLitGoalBox(player, g, partialTicks, color);
			}
			return;
		} else if (goal instanceof GoalInverted) {
			drawDankLitGoalBox(player, ((GoalInverted) goal).origin, partialTicks, settings.colorInvertedGoalBox.value);
			return;
		} else if (goal instanceof GoalYLevel) {
			GoalYLevel goalpos = (GoalYLevel) goal;
			minX = player.getX() - settings.yLevelBoxSize.value;
			minZ = player.getZ() - settings.yLevelBoxSize.value;
			maxX = player.getX() + settings.yLevelBoxSize.value;
			maxZ = player.getZ() + settings.yLevelBoxSize.value;
			minY = ((GoalYLevel) goal).level;
			maxY = minY + 2;
			y1 = 1 + y + goalpos.level;
			y2 = 1 - y + goalpos.level;
		} else
			return;

		IRenderer.startLines(color, settings.goalRenderLineWidthPixels.value, settings.renderGoalIgnoreDepth.value);

		renderHorizontalQuad(minX, maxX, minZ, maxZ, y1);
		renderHorizontalQuad(minX, maxX, minZ, maxZ, y2);

		buffer.begin(GL_LINES, VertexFormats.POSITION);
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(minX, minY, minZ));
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(minX, maxY, minZ));
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(maxX, minY, minZ));
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(maxX, maxY, minZ));
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(minX, minY, minZ));
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(maxX, maxY, maxZ));
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(minX, minY, maxZ));
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(minX, maxY, maxZ));
		tessellator.draw();

		IRenderer.endLines(settings.renderGoalIgnoreDepth.value);
	}

	public static void drawLine(Vec3d start, Vec3d end) {
		boolean renderPathAsFrickinThingy = !settings.renderPathAsLine.value;

		buffer.begin(renderPathAsFrickinThingy ? GL_LINE_STRIP : GL_LINES, VertexFormats.POSITION);
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(start.x + 0.5D, start.y + 0.5D, start.z + 0.5D));
		IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(end.x + 0.5D, end.y + 0.5D, end.z + 0.5D));

		if (renderPathAsFrickinThingy) {
			IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(end.x + 0.5D, end.y + 0.53D, end.z + 0.5D));
			IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(start.x + 0.5D, start.y + 0.53D, start.z + 0.5D));
			IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(start.x + 0.5D, start.y + 0.5D, start.z + 0.5D));
		}
	}

	public static void drawManySelectionBoxes(Entity player, Collection<BlockPos> positions, Color color) {
		IRenderer.startLines(color, settings.pathRenderLineWidthPixels.value, settings.renderSelectionBoxesIgnoreDepth.value);

		//BlockPos blockpos = movingObjectPositionIn.getBlockPos();
		BlockStateInterface bsi = new BlockStateInterface(BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext()); // TODO this assumes same dimension between primary baritone and render view? is this safe?

		positions.forEach(pos -> {
			BlockState state = bsi.get0(pos);
			VoxelShape shape = state.getCollisionShape(player.world, pos);
			Box toDraw = shape.isEmpty() ? VoxelShapes.fullCube().getBoundingBox() : shape.getBoundingBox();
			toDraw = toDraw.offset(pos);
			IRenderer.drawAABB(toDraw, .002D);
		});

		IRenderer.endLines(settings.renderSelectionBoxesIgnoreDepth.value);
	}

	public static void drawPath(IPath path, int startIndex, Color color, boolean fadeOut, int fadeStart0, int fadeEnd0) {
		IRenderer.startLines(color, settings.pathRenderLineWidthPixels.value, settings.renderPathIgnoreDepth.value);

		int fadeStart = fadeStart0 + startIndex;
		int fadeEnd = fadeEnd0 + startIndex;

		List<BetterBlockPos> positions = path.positions();
		for (int i = startIndex, next; i < positions.size() - 1; i = next) {
			BetterBlockPos start = positions.get(i);
			BetterBlockPos end = positions.get(next = i + 1);

			int dirX = end.x - start.x;
			int dirY = end.y - start.y;
			int dirZ = end.z - start.z;

			while (next + 1 < positions.size() && (!fadeOut || next + 1 < fadeStart) && dirX == positions.get(next + 1).x - end.x && dirY == positions.get(next + 1).y - end.y && dirZ == positions.get(next + 1).z - end.z) {
				end = positions.get(++next);
			}

			if (fadeOut) {
				float alpha;

				if (i <= fadeStart) {
					alpha = 0.4F;
				} else {
					if (i > fadeEnd) {
						break;
					}
					alpha = 0.4F * (1.0F - (float) (i - fadeStart) / (float) (fadeEnd - fadeStart));
				}
				IRenderer.glColor(color, alpha);
			}

			drawLine(IRenderer.toVec3d(start.x, start.y, start.z), IRenderer.toVec3d(end.x, end.y, end.z));

			tessellator.draw();
		}

		IRenderer.endLines(settings.renderPathIgnoreDepth.value);
	}

	public static void render(RenderEvent event, PathingBehavior behavior) {
		float partialTicks = event.getPartialTicks();
		Goal goal = behavior.getGoal();
		if (Helper.mc.currentScreen instanceof GuiClick) {
			((GuiClick) Helper.mc.currentScreen).onRender();
		}

		int thisPlayerDimension = behavior.baritone.getPlayerContext().world().getDimension().getType().getRawId();
		int currentRenderViewDimension = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().world().getDimension().getType().getRawId();

		if (thisPlayerDimension != currentRenderViewDimension)
			// this is a path for a bot in a different dimension, don't render it
			return;

		Entity renderView = Helper.mc.getCameraEntity();

		if (renderView == null || renderView.world == null || renderView.world != BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().world()) {
			System.out.println("I have no idea what's going on");
			System.out.println("The primary baritone is in a different world than the render view entity");
			System.out.println("Not rendering the path");
			return;
		}

		if (goal != null && settings.renderGoal.value) {
			drawDankLitGoalBox(renderView, goal, partialTicks, settings.colorGoalBox.value);
		}

		if (!settings.renderPath.value)
			return;

		PathExecutor current = behavior.getCurrent(); // this should prevent most race conditions?
		PathExecutor next = behavior.getNext(); // like, now it's not possible for current!=null to be true, then suddenly false because of another thread
		if (current != null && settings.renderSelectionBoxes.value) {
			drawManySelectionBoxes(renderView, current.toBreak(), settings.colorBlocksToBreak.value);
			drawManySelectionBoxes(renderView, current.toPlace(), settings.colorBlocksToPlace.value);
			drawManySelectionBoxes(renderView, current.toWalkInto(), settings.colorBlocksToWalkInto.value);
		}

		//drawManySelectionBoxes(player, Collections.singletonList(behavior.pathStart()), partialTicks, Color.WHITE);

		// Render the current path, if there is one
		if (current != null && current.getPath() != null) {
			int renderBegin = Math.max(current.getPosition() - 3, 0);
			drawPath(current.getPath(), renderBegin, settings.colorCurrentPath.value, settings.fadePath.value, 10, 20);
		}

		if (next != null && next.getPath() != null) {
			drawPath(next.getPath(), 0, settings.colorNextPath.value, settings.fadePath.value, 10, 20);
		}

		// If there is a path calculation currently running, render the path calculation process
		behavior.getInProgress().ifPresent(currentlyRunning -> {
			currentlyRunning.bestPathSoFar().ifPresent(p -> {
				drawPath(p, 0, settings.colorBestPathSoFar.value, settings.fadePath.value, 10, 20);
			});

			currentlyRunning.pathToMostRecentNodeConsidered().ifPresent(mr -> {
				drawPath(mr, 0, settings.colorMostRecentConsidered.value, settings.fadePath.value, 10, 20);
				drawManySelectionBoxes(renderView, Collections.singletonList(mr.getDest()), settings.colorMostRecentConsidered.value);
			});
		});
	}

	private static void renderHorizontalQuad(double minX, double maxX, double minZ, double maxZ, double y) {

		if (y != 0) {
			buffer.begin(GL_LINE_LOOP, VertexFormats.POSITION);
			IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(minX, y, minZ));
			IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(maxX, y, minZ));
			IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(maxX, y, maxZ));
			IRenderer.putVertex(buffer, IRenderer.camPos(), IRenderer.toVec3d(minX, y, maxZ));
			tessellator.draw();
		}
	}

	private PathRenderer() {
	}
}
