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
import baritone.utils.accessor.IEntityRenderManager;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public interface IRenderer {

    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder buffer = tessellator.getBufferBuilder();
    IEntityRenderManager renderManager = (IEntityRenderManager) Helper.mc.getEntityRenderManager();
    Settings settings = BaritoneAPI.getSettings();

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
        Box toDraw = aabb.offset(-renderManager.renderPosX(), -renderManager.renderPosY(), -renderManager.renderPosZ());

        buffer.begin(GL_LINES, VertexFormats.POSITION);
        // bottom
        buffer.vertex(toDraw.minX, toDraw.minY, toDraw.minZ).next();
        buffer.vertex(toDraw.maxX, toDraw.minY, toDraw.minZ).next();
        buffer.vertex(toDraw.maxX, toDraw.minY, toDraw.minZ).next();
        buffer.vertex(toDraw.maxX, toDraw.minY, toDraw.maxZ).next();
        buffer.vertex(toDraw.maxX, toDraw.minY, toDraw.maxZ).next();
        buffer.vertex(toDraw.minX, toDraw.minY, toDraw.maxZ).next();
        buffer.vertex(toDraw.minX, toDraw.minY, toDraw.maxZ).next();
        buffer.vertex(toDraw.minX, toDraw.minY, toDraw.minZ).next();
        // top
        buffer.vertex(toDraw.minX, toDraw.maxY, toDraw.minZ).next();
        buffer.vertex(toDraw.maxX, toDraw.maxY, toDraw.minZ).next();
        buffer.vertex(toDraw.maxX, toDraw.maxY, toDraw.minZ).next();
        buffer.vertex(toDraw.maxX, toDraw.maxY, toDraw.maxZ).next();
        buffer.vertex(toDraw.maxX, toDraw.maxY, toDraw.maxZ).next();
        buffer.vertex(toDraw.minX, toDraw.maxY, toDraw.maxZ).next();
        buffer.vertex(toDraw.minX, toDraw.maxY, toDraw.maxZ).next();
        buffer.vertex(toDraw.minX, toDraw.maxY, toDraw.minZ).next();
        // corners
        buffer.vertex(toDraw.minX, toDraw.minY, toDraw.minZ).next();
        buffer.vertex(toDraw.minX, toDraw.maxY, toDraw.minZ).next();
        buffer.vertex(toDraw.maxX, toDraw.minY, toDraw.minZ).next();
        buffer.vertex(toDraw.maxX, toDraw.maxY, toDraw.minZ).next();
        buffer.vertex(toDraw.maxX, toDraw.minY, toDraw.maxZ).next();
        buffer.vertex(toDraw.maxX, toDraw.maxY, toDraw.maxZ).next();
        buffer.vertex(toDraw.minX, toDraw.minY, toDraw.maxZ).next();
        buffer.vertex(toDraw.minX, toDraw.maxY, toDraw.maxZ).next();
        tessellator.draw();
    }

    static void drawAABB(Box aabb, double expand) {
        drawAABB(aabb.expand(expand, expand, expand));
    }
}
