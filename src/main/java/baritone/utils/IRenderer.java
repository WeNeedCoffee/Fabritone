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
import baritone.api.Settings;
import baritone.api.utils.Helper;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public interface IRenderer {

    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder buffer = tessellator.getBuffer();
    Settings settings = BaritoneAPI.getSettings();

    static Vec3d camPos() {
        return Helper.mc.gameRenderer.getCamera().getPos();
    }

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        GlStateManager.color4f(colorComponents[0], colorComponents[1], colorComponents[2], alpha);
    }

    static void startLines(Color color, float alpha, float lineWidth, boolean ignoreDepth) {
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        glColor(color, alpha);
        GlStateManager.lineWidth(lineWidth);
        GlStateManager.disableTexture();
        GlStateManager.depthMask(false);

        if (ignoreDepth) {
            GlStateManager.disableDepthTest();
        }
    }

    static void startLines(Color color, float lineWidth, boolean ignoreDepth) {
        startLines(color, .4f, lineWidth, ignoreDepth);
    }

    static void endLines(boolean ignoredDepth) {
        if (ignoredDepth) {
            GlStateManager.enableDepthTest();
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
    }

    static void drawAABB(Box aabb) {
        Box toDraw = aabb.offset(-IRenderer.camPos().getX(), -IRenderer.camPos().getY(), -IRenderer.camPos().getZ());

        buffer.begin(GL_LINES, VertexFormats.POSITION);
        // bottom
        buffer.vertex(toDraw.x1, toDraw.y1, toDraw.z1).next();
        buffer.vertex(toDraw.x2, toDraw.y1, toDraw.z1).next();
        buffer.vertex(toDraw.x2, toDraw.y1, toDraw.z1).next();
        buffer.vertex(toDraw.x2, toDraw.y1, toDraw.z2).next();
        buffer.vertex(toDraw.x2, toDraw.y1, toDraw.z2).next();
        buffer.vertex(toDraw.x1, toDraw.y1, toDraw.z2).next();
        buffer.vertex(toDraw.x1, toDraw.y1, toDraw.z2).next();
        buffer.vertex(toDraw.x1, toDraw.y1, toDraw.z1).next();
        // top
        buffer.vertex(toDraw.x1, toDraw.y2, toDraw.z1).next();
        buffer.vertex(toDraw.x2, toDraw.y2, toDraw.z1).next();
        buffer.vertex(toDraw.x2, toDraw.y2, toDraw.z1).next();
        buffer.vertex(toDraw.x2, toDraw.y2, toDraw.z2).next();
        buffer.vertex(toDraw.x2, toDraw.y2, toDraw.z2).next();
        buffer.vertex(toDraw.x1, toDraw.y2, toDraw.z2).next();
        buffer.vertex(toDraw.x1, toDraw.y2, toDraw.z2).next();
        buffer.vertex(toDraw.x1, toDraw.y2, toDraw.z1).next();
        // corners
        buffer.vertex(toDraw.x1, toDraw.y1, toDraw.z1).next();
        buffer.vertex(toDraw.x1, toDraw.y2, toDraw.z1).next();
        buffer.vertex(toDraw.x2, toDraw.y1, toDraw.z1).next();
        buffer.vertex(toDraw.x2, toDraw.y2, toDraw.z1).next();
        buffer.vertex(toDraw.x2, toDraw.y1, toDraw.z2).next();
        buffer.vertex(toDraw.x2, toDraw.y2, toDraw.z2).next();
        buffer.vertex(toDraw.x1, toDraw.y1, toDraw.z2).next();
        buffer.vertex(toDraw.x1, toDraw.y2, toDraw.z2).next();
        tessellator.draw();
    }

    static void drawAABB(Box aabb, double expand) {
        drawAABB(aabb.expand(expand, expand, expand));
    }

    static Vec3d toVec3d(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    static void putVertex(BufferBuilder buffer, Vec3d camPos, Vec3d pos) {
        buffer.vertex(
                pos.x - camPos.x,
                pos.y - camPos.y,
                pos.z - camPos.z
        ).next();
    }
}
