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

package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.event.events.RenderEvent;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.*;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.behavior.PathingBehavior;
import baritone.pathing.path.PathExecutor;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author Brady
 * @since 8/9/2018
 */
public final class PathRenderer implements IRenderer, Helper {

    private static final Identifier TEXTURE_BEACON_BEAM = new Identifier("textures/entity/beacon_beam.png");


    private PathRenderer() {}

    public static double posX() {
        return renderManager.renderPosX();
    }

    public static double posY() {
        return renderManager.renderPosY();
    }

    public static double posZ() {
        return renderManager.renderPosZ();
    }

    public static void render(RenderEvent event, PathingBehavior behavior) {
        float partialTicks = event.getPartialTicks();
        Goal goal = behavior.getGoal();
        if (Helper.mc.currentScreen instanceof GuiClick) {
            ((GuiClick) Helper.mc.currentScreen).onRender();
        }

        int thisPlayerDimension = behavior.baritone.getPlayerContext().world().getDimension().getType().getRawId();
        int currentRenderViewDimension = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().world().getDimension().getType().getRawId();

        if (thisPlayerDimension != currentRenderViewDimension) {
            // this is a path for a bot in a different dimension, don't render it
            return;
        }

        Entity renderView = Helper.mc.getCameraEntity();

        if (renderView.world != BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().world()) {
            System.out.println("I have no idea what's going on");
            System.out.println("The primary baritone is in a different world than the render view entity");
            System.out.println("Not rendering the path");
            return;
        }

        if (goal != null && settings.renderGoal.value) {
            drawDankLitGoalBox(renderView, goal, partialTicks, settings.colorGoalBox.value);
        }

        if (!settings.renderPath.value) {
            return;
        }

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

            while (next + 1 < positions.size() && (!fadeOut || next + 1 < fadeStart) &&
                    (dirX == positions.get(next + 1).x - end.x &&
                            dirY == positions.get(next + 1).y - end.y &&
                            dirZ == positions.get(next + 1).z - end.z)) {
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

            drawLine(start.x, start.y, start.z, end.x, end.y, end.z);

            tessellator.draw();
        }

        IRenderer.endLines(settings.renderPathIgnoreDepth.value);
    }


    public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2) {
        double vpX = posX();
        double vpY = posY();
        double vpZ = posZ();
        boolean renderPathAsFrickinThingy = !settings.renderPathAsLine.value;

        buffer.begin(renderPathAsFrickinThingy ? GL_LINE_STRIP : GL_LINES, VertexFormats.POSITION);
        buffer.postPosition(x1 + 0.5D - vpX, y1 + 0.5D - vpY, z1 + 0.5D - vpZ);
        buffer.postPosition(x2 + 0.5D - vpX, y2 + 0.5D - vpY, z2 + 0.5D - vpZ);

        if (renderPathAsFrickinThingy) {
            buffer.postPosition(x2 + 0.5D - vpX, y2 + 0.53D - vpY, z2 + 0.5D - vpZ);
            buffer.postPosition(x1 + 0.5D - vpX, y1 + 0.53D - vpY, z1 + 0.5D - vpZ);
            buffer.postPosition(x1 + 0.5D - vpX, y1 + 0.5D - vpY, z1 + 0.5D - vpZ);
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

    public static void drawDankLitGoalBox(Entity player, Goal goal, float partialTicks, Color color) {
        double renderPosX = posX();
        double renderPosY = posY();
        double renderPosZ = posZ();
        double minX, maxX;
        double minZ, maxZ;
        double minY, maxY;
        double y1, y2;
        double y = MathHelper.cos((float) (((float) ((System.nanoTime() / 100000L) % 20000L)) / 20000F * Math.PI * 2));
        if (goal instanceof IGoalRenderPos) {
            BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
                y /= 2;
            }
            y1 = 1 + y + goalPos.getY() - renderPosY;
            y2 = 1 - y + goalPos.getY() - renderPosY;
            minY = goalPos.getY() - renderPosY;
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

                BeaconBlockEntityRenderer.renderLightBeam(
                        goalPos.getX() - renderPosX,
                        -renderPosY,
                        goalPos.getZ() - renderPosZ,
                        partialTicks,
                        1.0,
                        player.world.getTime(),
                        0,
                        256,
                        color.getColorComponents(null),

                        // Arguments filled by the private method lol
                        0.2D,
                        0.25D
                );

                if (settings.renderGoalIgnoreDepth.value) {
                    GlStateManager.enableDepthTest();
                }

                glPopAttrib();
                return;
            }

            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;

            y1 = 0;
            y2 = 0;
            minY = 0 - renderPosY;
            maxY = 256 - renderPosY;
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
            minX = player.x - settings.yLevelBoxSize.value - renderPosX;
            minZ = player.z - settings.yLevelBoxSize.value - renderPosZ;
            maxX = player.x + settings.yLevelBoxSize.value - renderPosX;
            maxZ = player.z + settings.yLevelBoxSize.value - renderPosZ;
            minY = ((GoalYLevel) goal).level - renderPosY;
            maxY = minY + 2;
            y1 = 1 + y + goalpos.level - renderPosY;
            y2 = 1 - y + goalpos.level - renderPosY;
        } else {
            return;
        }

        IRenderer.startLines(color, settings.goalRenderLineWidthPixels.value, settings.renderGoalIgnoreDepth.value);

        renderHorizontalQuad(minX, maxX, minZ, maxZ, y1);
        renderHorizontalQuad(minX, maxX, minZ, maxZ, y2);


        buffer.begin(GL_LINES, VertexFormats.POSITION);
        buffer.postPosition(minX, minY, minZ);
        buffer.postPosition(minX, maxY, minZ);
        buffer.postPosition(maxX, minY, minZ);
        buffer.postPosition(maxX, maxY, minZ);
        buffer.postPosition(maxX, minY, maxZ);
        buffer.postPosition(maxX, maxY, maxZ);
        buffer.postPosition(minX, minY, maxZ);
        buffer.postPosition(minX, maxY, maxZ);
        tessellator.draw();

        IRenderer.endLines(settings.renderGoalIgnoreDepth.value);
    }

    private static void renderHorizontalQuad(double minX, double maxX, double minZ, double maxZ, double y) {
        if (y != 0) {
            buffer.begin(GL_LINE_LOOP, VertexFormats.POSITION);
            buffer.postPosition(minX, y, minZ);
            buffer.postPosition(maxX, y, minZ);
            buffer.postPosition(maxX, y, maxZ);
            buffer.postPosition(minX, y, maxZ);
            tessellator.draw();
        }
    }
}
