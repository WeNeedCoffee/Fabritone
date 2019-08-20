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

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.event.events.RenderEvent;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.*;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.behavior.PathingBehavior;
import baritone.pathing.path.PathExecutor;
import baritone.utils.accessor.IEntityRenderManager;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
public final class PathRenderer implements Helper {

    private static final Identifier TEXTURE_BEACON_BEAM = new Identifier("textures/entity/beacon_beam.png");
    private static final Tessellator TESSELLATOR = Tessellator.getInstance();
    private static final BufferBuilder BUFFER = TESSELLATOR.getBufferBuilder();

    private PathRenderer() {}

    public static double posX() {
        return ((IEntityRenderManager) mc.getEntityRenderManager()).renderPosX();
    }

    public static double posY() {
        return ((IEntityRenderManager) mc.getEntityRenderManager()).renderPosY();
    }

    public static double posZ() {
        return ((IEntityRenderManager) mc.getEntityRenderManager()).renderPosZ();
    }

    public static void render(RenderEvent event, PathingBehavior behavior) {
        float partialTicks = event.getPartialTicks();
        Goal goal = behavior.getGoal();
        if (mc.currentScreen instanceof GuiClick) {
            ((GuiClick) mc.currentScreen).onRender();
        }

        int thisPlayerDimension = behavior.baritone.getPlayerContext().world().getDimension().getType().getRawId();
        int currentRenderViewDimension = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().world().getDimension().getType().getRawId();

        if (thisPlayerDimension != currentRenderViewDimension) {
            // this is a path for a bot in a different dimension, don't render it
            return;
        }

        Entity renderView = mc.getCameraEntity();

        if (renderView.world != BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().world()) {
            System.out.println("I have no idea what's going on");
            System.out.println("The primary baritone is in a different world than the render view entity");
            System.out.println("Not rendering the path");
            return;
        }

        if (goal != null && Baritone.settings().renderGoal.value) {
            drawDankLitGoalBox(renderView, goal, partialTicks, Baritone.settings().colorGoalBox.value);
        }

        if (!Baritone.settings().renderPath.value) {
            return;
        }
        PathExecutor current = behavior.getCurrent(); // this should prevent most race conditions?
        PathExecutor next = behavior.getNext(); // like, now it's not possible for current!=null to be true, then suddenly false because of another thread
        if (current != null && Baritone.settings().renderSelectionBoxes.value) {
            drawManySelectionBoxes(renderView, current.toBreak(), Baritone.settings().colorBlocksToBreak.value);
            drawManySelectionBoxes(renderView, current.toPlace(), Baritone.settings().colorBlocksToPlace.value);
            drawManySelectionBoxes(renderView, current.toWalkInto(), Baritone.settings().colorBlocksToWalkInto.value);
        }

        //drawManySelectionBoxes(player, Collections.singletonList(behavior.pathStart()), partialTicks, Color.WHITE);

        // Render the current path, if there is one
        if (current != null && current.getPath() != null) {
            int renderBegin = Math.max(current.getPosition() - 3, 0);
            drawPath(current.getPath(), renderBegin, renderView, partialTicks, Baritone.settings().colorCurrentPath.value, Baritone.settings().fadePath.value, 10, 20);
        }
        if (next != null && next.getPath() != null) {
            drawPath(next.getPath(), 0, renderView, partialTicks, Baritone.settings().colorNextPath.value, Baritone.settings().fadePath.value, 10, 20);
        }

        // If there is a path calculation currently running, render the path calculation process
        behavior.getInProgress().ifPresent(currentlyRunning -> {
            currentlyRunning.bestPathSoFar().ifPresent(p -> {
                drawPath(p, 0, renderView, partialTicks, Baritone.settings().colorBestPathSoFar.value, Baritone.settings().fadePath.value, 10, 20);
            });
            currentlyRunning.pathToMostRecentNodeConsidered().ifPresent(mr -> {

                drawPath(mr, 0, renderView, partialTicks, Baritone.settings().colorMostRecentConsidered.value, Baritone.settings().fadePath.value, 10, 20);
                drawManySelectionBoxes(renderView, Collections.singletonList(mr.getDest()), Baritone.settings().colorMostRecentConsidered.value);
            });
        });
    }

    public static void drawPath(IPath path, int startIndex, Entity player, float partialTicks, Color color, boolean fadeOut, int fadeStart0, int fadeEnd0) {
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        GlStateManager.color4f(color.getColorComponents(null)[0], color.getColorComponents(null)[1], color.getColorComponents(null)[2], 0.4F);
        GlStateManager.lineWidth(Baritone.settings().pathRenderLineWidthPixels.value);
        GlStateManager.disableTexture();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (Baritone.settings().renderPathIgnoreDepth.value) {
            GlStateManager.disableDepthTest();
        }
        List<BetterBlockPos> positions = path.positions();
        int next;
        Tessellator tessellator = Tessellator.getInstance();
        int fadeStart = fadeStart0 + startIndex;
        int fadeEnd = fadeEnd0 + startIndex;
        for (int i = startIndex; i < positions.size() - 1; i = next) {
            BetterBlockPos start = positions.get(i);

            next = i + 1;
            BetterBlockPos end = positions.get(next);

            int dirX = end.x - start.x;
            int dirY = end.y - start.y;
            int dirZ = end.z - start.z;
            while (next + 1 < positions.size() && (!fadeOut || next + 1 < fadeStart) && (dirX == positions.get(next + 1).x - end.x && dirY == positions.get(next + 1).y - end.y && dirZ == positions.get(next + 1).z - end.z)) {
                next++;
                end = positions.get(next);
            }
            double x1 = start.x;
            double y1 = start.y;
            double z1 = start.z;
            double x2 = end.x;
            double y2 = end.y;
            double z2 = end.z;
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
                GlStateManager.color4f(color.getColorComponents(null)[0], color.getColorComponents(null)[1], color.getColorComponents(null)[2], alpha);
            }
            drawLine(x1, y1, z1, x2, y2, z2);
            tessellator.draw();
        }
        if (Baritone.settings().renderPathIgnoreDepth.value) {
            GlStateManager.enableDepthTest();
        }
        //GlStateManager.color(0.0f, 0.0f, 0.0f, 0.4f);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
    }

    public static void drawLine(double bp1x, double bp1y, double bp1z, double bp2x, double bp2y, double bp2z) {
        double d0 = posX();
        double d1 = posY();
        double d2 = posZ();
        BUFFER.begin(GL_LINE_STRIP, VertexFormats.POSITION);
        BUFFER.vertex(bp1x + 0.5D - d0, bp1y + 0.5D - d1, bp1z + 0.5D - d2).next();
        BUFFER.vertex(bp2x + 0.5D - d0, bp2y + 0.5D - d1, bp2z + 0.5D - d2).next();
        BUFFER.vertex(bp2x + 0.5D - d0, bp2y + 0.53D - d1, bp2z + 0.5D - d2).next();
        BUFFER.vertex(bp1x + 0.5D - d0, bp1y + 0.53D - d1, bp1z + 0.5D - d2).next();
        BUFFER.vertex(bp1x + 0.5D - d0, bp1y + 0.5D - d1, bp1z + 0.5D - d2).next();
    }

    public static void drawManySelectionBoxes(Entity player, Collection<BlockPos> positions, Color color) {
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        GlStateManager.color4f(color.getColorComponents(null)[0], color.getColorComponents(null)[1], color.getColorComponents(null)[2], 0.4F);
        GlStateManager.lineWidth(Baritone.settings().pathRenderLineWidthPixels.value);
        GlStateManager.disableTexture();
        GlStateManager.depthMask(false);

        if (Baritone.settings().renderSelectionBoxesIgnoreDepth.value) {
            GlStateManager.disableDepthTest();
        }


        //BlockPos blockpos = movingObjectPositionIn.getBlockPos();
        BlockStateInterface bsi = new BlockStateInterface(BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext()); // TODO this assumes same dimension between primary baritone and render view? is this safe?
        positions.forEach(pos -> {
            BlockState state = bsi.get0(pos);
            VoxelShape shape = state.getOutlineShape(player.world, pos);
            Box toDraw = shape.isEmpty() ? VoxelShapes.fullCube().getBoundingBox() : shape.getBoundingBox();
            toDraw = toDraw.offset(pos);
            drawAABB(toDraw);
        });

        if (Baritone.settings().renderSelectionBoxesIgnoreDepth.value) {
            GlStateManager.enableDepthTest();
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
    }

    public static void drawAABB(Box aabb) {
        float expand = 0.002F;
        Box toDraw = aabb.expand(expand, expand, expand).offset(-posX(), -posY(), -posZ());
        BUFFER.begin(GL_LINE_STRIP, VertexFormats.POSITION);
        BUFFER.vertex(toDraw.minX, toDraw.minY, toDraw.minZ).next();
        BUFFER.vertex(toDraw.maxX, toDraw.minY, toDraw.minZ).next();
        BUFFER.vertex(toDraw.maxX, toDraw.minY, toDraw.maxZ).next();
        BUFFER.vertex(toDraw.minX, toDraw.minY, toDraw.maxZ).next();
        BUFFER.vertex(toDraw.minX, toDraw.minY, toDraw.minZ).next();
        TESSELLATOR.draw();
        BUFFER.begin(GL_LINE_STRIP, VertexFormats.POSITION);
        BUFFER.vertex(toDraw.minX, toDraw.maxY, toDraw.minZ).next();
        BUFFER.vertex(toDraw.maxX, toDraw.maxY, toDraw.minZ).next();
        BUFFER.vertex(toDraw.maxX, toDraw.maxY, toDraw.maxZ).next();
        BUFFER.vertex(toDraw.minX, toDraw.maxY, toDraw.maxZ).next();
        BUFFER.vertex(toDraw.minX, toDraw.maxY, toDraw.minZ).next();
        TESSELLATOR.draw();
        BUFFER.begin(GL_LINES, VertexFormats.POSITION);
        BUFFER.vertex(toDraw.minX, toDraw.minY, toDraw.minZ).next();
        BUFFER.vertex(toDraw.minX, toDraw.maxY, toDraw.minZ).next();
        BUFFER.vertex(toDraw.maxX, toDraw.minY, toDraw.minZ).next();
        BUFFER.vertex(toDraw.maxX, toDraw.maxY, toDraw.minZ).next();
        BUFFER.vertex(toDraw.maxX, toDraw.minY, toDraw.maxZ).next();
        BUFFER.vertex(toDraw.maxX, toDraw.maxY, toDraw.maxZ).next();
        BUFFER.vertex(toDraw.minX, toDraw.minY, toDraw.maxZ).next();
        BUFFER.vertex(toDraw.minX, toDraw.maxY, toDraw.maxZ).next();
        TESSELLATOR.draw();
    }

    public static void drawDankLitGoalBox(Entity player, Goal goal, float partialTicks, Color color) {
        double renderPosX = posX();
        double renderPosY = posY();
        double renderPosZ = posZ();
        double minX;
        double maxX;
        double minZ;
        double maxZ;
        double minY;
        double maxY;
        double y1;
        double y2;
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

            if (Baritone.settings().renderGoalXZBeacon.value) {
                glPushAttrib(GL_LIGHTING_BIT);

                mc.getTextureManager().bindTexture(TEXTURE_BEACON_BEAM);

                if (Baritone.settings().renderGoalIgnoreDepth.value) {
                    GlStateManager.disableDepthTest();
                }

                BeaconBlockEntityRenderer.renderLightBeam(
                        goalPos.getX() - renderPosX,
                        -renderPosY,
                        goalPos.getZ() - renderPosZ,
                        partialTicks,
                        1.0,
                        player.world.getTimeOfDay(),
                        0,
                        256,
                        color.getColorComponents(null),

                        // Arguments filled by the private method lol
                        0.2D,
                        0.25D
                );

                if (Baritone.settings().renderGoalIgnoreDepth.value) {
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
        } else if (goal instanceof GoalYLevel) {
            GoalYLevel goalpos = (GoalYLevel) goal;
            minX = player.x - Baritone.settings().yLevelBoxSize.value - renderPosX;
            minZ = player.z - Baritone.settings().yLevelBoxSize.value - renderPosZ;
            maxX = player.x + Baritone.settings().yLevelBoxSize.value - renderPosX;
            maxZ = player.z + Baritone.settings().yLevelBoxSize.value - renderPosZ;
            minY = ((GoalYLevel) goal).level - renderPosY;
            maxY = minY + 2;
            y1 = 1 + y + goalpos.level - renderPosY;
            y2 = 1 - y + goalpos.level - renderPosY;
        } else {
            return;
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        GlStateManager.color4f(color.getColorComponents(null)[0], color.getColorComponents(null)[1], color.getColorComponents(null)[2], 0.6F);
        GlStateManager.lineWidth(Baritone.settings().goalRenderLineWidthPixels.value);
        GlStateManager.disableTexture();
        GlStateManager.depthMask(false);
        if (Baritone.settings().renderGoalIgnoreDepth.value) {
            GlStateManager.disableDepthTest();
        }

        renderHorizontalQuad(minX, maxX, minZ, maxZ, y1);
        renderHorizontalQuad(minX, maxX, minZ, maxZ, y2);

        BUFFER.begin(GL_LINES, VertexFormats.POSITION);
        BUFFER.vertex(minX, minY, minZ).next();
        BUFFER.vertex(minX, maxY, minZ).next();
        BUFFER.vertex(maxX, minY, minZ).next();
        BUFFER.vertex(maxX, maxY, minZ).next();
        BUFFER.vertex(maxX, minY, maxZ).next();
        BUFFER.vertex(maxX, maxY, maxZ).next();
        BUFFER.vertex(minX, minY, maxZ).next();
        BUFFER.vertex(minX, maxY, maxZ).next();
        TESSELLATOR.draw();

        if (Baritone.settings().renderGoalIgnoreDepth.value) {
            GlStateManager.enableDepthTest();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
    }

    private static void renderHorizontalQuad(double minX, double maxX, double minZ, double maxZ, double y) {
        if (y != 0) {
            BUFFER.begin(GL_LINE_LOOP, VertexFormats.POSITION);
            BUFFER.vertex(minX, y, minZ).next();
            BUFFER.vertex(maxX, y, minZ).next();
            BUFFER.vertex(maxX, y, maxZ).next();
            BUFFER.vertex(minX, y, maxZ).next();
            TESSELLATOR.draw();
        }
    }
}
