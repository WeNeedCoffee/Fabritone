package baritone.api.utils.gui;

/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BaritoneToast implements Toast {
	public static void addOrUpdate(ToastManager toast, Text title, Text subtitle, long totalShowTime) {
		BaritoneToast baritonetoast = toast.getToast(BaritoneToast.class, new Object());

		if (baritonetoast == null) {
			toast.add(new BaritoneToast(title, subtitle, totalShowTime));
		} else {
			baritonetoast.setDisplayedText(title, subtitle);
		}
	}

	private String title;
	private String subtitle;
	private long firstDrawTime;
	private boolean newDisplay;

	private long totalShowTime;

	public BaritoneToast(Text titleComponent, Text subtitleComponent, long totalShowTime) {
		title = titleComponent.getString();
		subtitle = subtitleComponent == null ? null : subtitleComponent.getString();
		this.totalShowTime = totalShowTime;
	}

	@Override
	public Visibility draw(ToastManager toastGui, long delta) {
		if (newDisplay) {
			firstDrawTime = delta;
			newDisplay = false;
		}

		toastGui.getGame().getTextureManager().bindTexture(new Identifier("textures/gui/toasts.png"));
		GlStateManager.color4f(1.0F, 1.0F, 1.0F, 255.0f);
		toastGui.blit(0, 0, 0, 32, 160, 32);

		if (subtitle == null) {
			toastGui.getGame().textRenderer.draw(title, 18, 12, -11534256);
		} else {
			toastGui.getGame().textRenderer.draw(title, 18, 7, -11534256);
			toastGui.getGame().textRenderer.draw(subtitle, 18, 18, -16777216);
		}

		return delta - firstDrawTime < totalShowTime ? Visibility.SHOW : Visibility.HIDE;
	}

	public void setDisplayedText(Text titleComponent, Text subtitleComponent) {
		title = titleComponent.getString();
		subtitle = subtitleComponent == null ? null : subtitleComponent.getString();
		newDisplay = true;
	}
}