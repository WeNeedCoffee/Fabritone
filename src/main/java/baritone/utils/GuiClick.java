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

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.GL_ZERO;
import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import com.mojang.blaze3d.platform.GlStateManager;
import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RayTraceContext;

public class GuiClick extends Screen implements Helper {

	private static final float[] IDENTITY_MATRIX = new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f };

	private static boolean __gluInvertMatrixf(FloatBuffer src, FloatBuffer inverse) {
		int i, j, k, swap;
		float t;
		FloatBuffer temp = BufferUtils.createFloatBuffer(16);

		for (i = 0; i < 16; i++) {
			temp.put(i, src.get(i + src.position()));
		}
		__gluMakeIdentityf(inverse);

		for (i = 0; i < 4; i++) {
			/*
			 * * Look for largest element in column
			 */
			swap = i;
			for (j = i + 1; j < 4; j++) {
				/*
				 * if (fabs(temp[j][i]) > fabs(temp[i][i])) { swap = j;
				 */
				if (Math.abs(temp.get(j * 4 + i)) > Math.abs(temp.get(i * 4 + i))) {
					swap = j;
				}
			}

			if (swap != i) {
				/*
				 * * Swap rows.
				 */
				for (k = 0; k < 4; k++) {
					t = temp.get(i * 4 + k);
					temp.put(i * 4 + k, temp.get(swap * 4 + k));
					temp.put(swap * 4 + k, t);

					t = inverse.get(i * 4 + k);
					inverse.put(i * 4 + k, inverse.get(swap * 4 + k));
					//inverse.put((i << 2) + k, inverse.get((swap << 2) + k));
					inverse.put(swap * 4 + k, t);
					//inverse.put((swap << 2) + k, t);
				}
			}

			if (temp.get(i * 4 + i) == 0)
				/*
				 * * No non-zero pivot. The matrix is singular, which shouldn't * happen. This means the user gave us a bad matrix.
				 */
				return false;

			t = temp.get(i * 4 + i);
			for (k = 0; k < 4; k++) {
				temp.put(i * 4 + k, temp.get(i * 4 + k) / t);
				inverse.put(i * 4 + k, inverse.get(i * 4 + k) / t);
			}
			for (j = 0; j < 4; j++) {
				if (j != i) {
					t = temp.get(j * 4 + i);
					for (k = 0; k < 4; k++) {
						temp.put(j * 4 + k, temp.get(j * 4 + k) - temp.get(i * 4 + k) * t);
						inverse.put(j * 4 + k, inverse.get(j * 4 + k) - inverse.get(i * 4 + k) * t);
						/*
						 * inverse.put( (j << 2) + k, inverse.get((j << 2) + k) - inverse.get((i << 2) + k) * t);
						 */
					}
				}
			}
		}
		return true;
	}

	private static void __gluMakeIdentityf(FloatBuffer m) {
		int oldPos = m.position();
		m.put(IDENTITY_MATRIX);
		m.position(oldPos);
	}

	private static void __gluMultMatricesf(FloatBuffer a, FloatBuffer b, FloatBuffer r) {
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				r.put(r.position() + i * 4 + j, a.get(a.position() + i * 4 + 0) * b.get(b.position() + 0 * 4 + j) + a.get(a.position() + i * 4 + 1) * b.get(b.position() + 1 * 4 + j) + a.get(a.position() + i * 4 + 2) * b.get(b.position() + 2 * 4 + j) + a.get(a.position() + i * 4 + 3) * b.get(b.position() + 3 * 4 + j));
			}
		}
	}

	private static void __gluMultMatrixVecf(FloatBuffer m, float[] in, float[] out) {
		for (int i = 0; i < 4; i++) {
			out[i] = in[0] * m.get(m.position() + 0 * 4 + i) + in[1] * m.get(m.position() + 1 * 4 + i) + in[2] * m.get(m.position() + 2 * 4 + i) + in[3] * m.get(m.position() + 3 * 4 + i);

		}
	}

	public static boolean gluUnProject(float winx, float winy, float winz, FloatBuffer modelMatrix, FloatBuffer projMatrix, IntBuffer viewport, FloatBuffer obj_pos) {
		FloatBuffer finalMatrix = BufferUtils.createFloatBuffer(16);
		float[] in = new float[4];
		float[] out = new float[4];

		__gluMultMatricesf(modelMatrix, projMatrix, finalMatrix);

		if (!__gluInvertMatrixf(finalMatrix, finalMatrix))
			return false;

		in[0] = winx;
		in[1] = winy;
		in[2] = winz;
		in[3] = 1.0f;

		// Map x and y from window coordinates
		in[0] = (in[0] - viewport.get(viewport.position() + 0)) / viewport.get(viewport.position() + 2);
		in[1] = (in[1] - viewport.get(viewport.position() + 1)) / viewport.get(viewport.position() + 3);

		// Map to range -1 to 1
		in[0] = in[0] * 2 - 1;
		in[1] = in[1] * 2 - 1;
		in[2] = in[2] * 2 - 1;

		__gluMultMatrixVecf(finalMatrix, in, out);

		if (out[3] == 0.0)
			return false;

		out[3] = 1.0f / out[3];

		obj_pos.put(obj_pos.position() + 0, out[0] * out[3]);
		obj_pos.put(obj_pos.position() + 1, out[1] * out[3]);
		obj_pos.put(obj_pos.position() + 2, out[2] * out[3]);

		return true;
	}

	// My name is Brady and I grant leijurv permission to use this pasted code
	private final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);

	private final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);

	private final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);

	private final FloatBuffer TO_WORLD_BUFFER = BufferUtils.createFloatBuffer(3);

	private BlockPos clickStart;

	private BlockPos currentMouseOver;

	public GuiClick() {
		super(new LiteralText("CLICK"));
	}

	// skidded from lwjgl2 :ok_hand:
	// its uhhhhh mit license so its ok
	// here is the uhh license
	/*
	 * Copyright (c) 2002-2007 Lightweight Java Game Library Project All rights reserved.
	 *
	 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
	 *
	 * * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
	 *
	 * * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
	 *
	 * * Neither the name of 'Light Weight Java Game Library' nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
	 *
	 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
	 */

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		clickStart = currentMouseOver;
		return super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
		if (mouseButton == 0) {
			if (clickStart != null && !clickStart.equals(currentMouseOver)) {
				BaritoneAPI.getProvider().getPrimaryBaritone().getSelectionManager().removeAllSelections();
				BaritoneAPI.getProvider().getPrimaryBaritone().getSelectionManager().addSelection(BetterBlockPos.from(clickStart), BetterBlockPos.from(currentMouseOver));
				Helper.HELPER.logDirect("Selection made! For usage: " + Baritone.settings().prefix.value + "help sel");
				Text component = new LiteralText("Or just click here");
				Text hoverComponent = new LiteralText("Click me to run the command");
				hoverComponent.getStyle().setColor(Formatting.GRAY);
				component.getStyle().setColor(Formatting.WHITE).setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent)).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, FORCE_COMMAND_PREFIX + "help sel"));
				Helper.HELPER.logDirect(component);
				clickStart = null;
			} else {
				BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalTwoBlocks(currentMouseOver));
			}
		} else if (mouseButton == 1) {
			BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(currentMouseOver.up()));
		}
		clickStart = null;
		return super.mouseReleased(mouseX, mouseY, mouseButton);
	}

	public void onRender() {
		GL11.glGetFloatv(GL_MODELVIEW_MATRIX, (FloatBuffer) MODELVIEW.clear());
		GL11.glGetFloatv(GL_PROJECTION_MATRIX, (FloatBuffer) PROJECTION.clear());
		GL11.glGetIntegerv(GL_VIEWPORT, (IntBuffer) VIEWPORT.clear());

		if (currentMouseOver != null) {
			Entity e = mc.getCameraEntity();
			// drawSingleSelectionBox WHEN?
			PathRenderer.drawManySelectionBoxes(e, Collections.singletonList(currentMouseOver), Color.CYAN);
			if (clickStart != null && !clickStart.equals(currentMouseOver)) {
				GlStateManager.enableBlend();
				GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
				GlStateManager.color4f(Color.RED.getColorComponents(null)[0], Color.RED.getColorComponents(null)[1], Color.RED.getColorComponents(null)[2], 0.4F);
				GlStateManager.lineWidth(Baritone.settings().pathRenderLineWidthPixels.value);
				GlStateManager.disableTexture();
				GlStateManager.depthMask(false);
				GlStateManager.disableDepthTest();
				BetterBlockPos a = new BetterBlockPos(currentMouseOver);
				BetterBlockPos b = new BetterBlockPos(clickStart);
				IRenderer.drawAABB(new Box(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x) + 1, Math.max(a.y, b.y) + 1, Math.max(a.z, b.z) + 1));
				GlStateManager.enableDepthTest();

				GlStateManager.depthMask(true);
				GlStateManager.enableTexture();
				GlStateManager.disableBlend();
			}
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTicks) {
		double mx = mc.mouse.getX();
		double my = mc.mouse.getY();
		my = mc.getWindow().getHeight() - my;
		my *= mc.getWindow().getFramebufferHeight() / (double) mc.getWindow().getHeight();
		mx *= mc.getWindow().getFramebufferWidth() / (double) mc.getWindow().getWidth();
		Vec3d near = toWorld(mx, my, 0);
		Vec3d far = toWorld(mx, my, 1); // "Use 0.945 that's what stack overflow says" - leijurv
		if (near != null && far != null) {
			///
			Vec3d viewerPos = new Vec3d(IRenderer.camPos().getX(), IRenderer.camPos().getY(), IRenderer.camPos().getZ());
			ClientPlayerEntity player = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().player();
			HitResult result = player.world.rayTrace(new RayTraceContext(near.add(viewerPos), far.add(viewerPos), RayTraceContext.ShapeType.OUTLINE, RayTraceContext.FluidHandling.NONE, player));
			if (result != null && result.getType() == HitResult.Type.BLOCK) {
				currentMouseOver = ((BlockHitResult) result).getBlockPos();
			}
		}
	}

	private Vec3d toWorld(double x, double y, double z) {
		boolean result = gluUnProject((float) x, (float) y, (float) z, MODELVIEW, PROJECTION, VIEWPORT, (FloatBuffer) TO_WORLD_BUFFER.clear());
		if (result)
			return new Vec3d(TO_WORLD_BUFFER.get(0), TO_WORLD_BUFFER.get(1), TO_WORLD_BUFFER.get(2));
		return null;
	}
}
