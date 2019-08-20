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

package baritone.api.utils;

import baritone.api.BaritoneAPI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * @author Brady
 * @since 8/1/2018
 */
public interface Helper {

    /**
     * Instance of {@link Helper}. Used for static-context reference.
     */
    Helper HELPER = new Helper() {};

    Text MESSAGE_PREFIX = new LiteralText(String.format(
            "%s[%sBaritone%s]%s",
            Formatting.DARK_PURPLE,
            Formatting.LIGHT_PURPLE,
            Formatting.DARK_PURPLE,
            Formatting.GRAY
    ));

    MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * Send a message to chat only if chatDebug is on
     *
     * @param message The message to display in chat
     */
    default void logDebug(String message) {
        if (!BaritoneAPI.getSettings().chatDebug.value) {
            //System.out.println("Suppressed debug message:");
            //System.out.println(message);
            return;
        }
        logDirect(message);
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a direct response to a chat command)
     *
     * @param message The message to display in chat
     */
    default void logDirect(String message) {
        Text component = MESSAGE_PREFIX.copy();
        component.getStyle().setColor(Formatting.GRAY);
        component.append(new LiteralText(" " + message));
        MinecraftClient.getInstance().execute(() -> BaritoneAPI.getSettings().logger.value.accept(component));
    }
}
