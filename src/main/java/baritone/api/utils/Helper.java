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

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.command.IBaritoneChatControl;
import baritone.api.utils.gui.BaritoneToast;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * An ease-of-access interface to provide the {@link MinecraftClient} game instance,
 * chat and console logging mechanisms, and the Baritone chat prefix.
 *
 * @author Brady
 * @since 8/1/2018
 */
public interface Helper {

    /**
     * Instance of {@link Helper}. Used for static-context reference.
     */
    Helper HELPER = new Helper() {};

    /**
     * Instance of the game
     */
    MinecraftClient mc = MinecraftClient.getInstance();
    static Text getPrefix() {
        // Inner text component
        Text baritone = new LiteralText(BaritoneAPI.getSettings().shortBaritonePrefix.value ? "F" : "Fabritone");
        baritone.getStyle().setColor(Formatting.DARK_GREEN);

        // Outer brackets
        Text prefix = new LiteralText("");
        prefix.getStyle().setColor(Formatting.DARK_GREEN);
        prefix.append("[");
        prefix.append(baritone);
        prefix.append("]");

        return prefix;
    }

    //Stuff to be able to disable normal Fabritone command handling but still let Aristois bypass the settings
    default String getSecretPrefix(){
        return IBaritoneChatControl.FORCE_COMMAND_PREFIX;
    }

    default void clientMode(boolean mode){
        Settings settings = Baritone.settings() ;
        if (mode){
            settings.prefixControl.value = false;
            settings.clientMode.value = true;
            settings.chatControl.reset();
            settings.chatControlAnyway.reset();
        }else{
            settings.prefixControl.value = true;
            settings.clientMode.reset();
        }
    }
    /**
     * Send a message to the toaster to show it as a popup
     *
     * @param title The title to display in popup as textcomponent
     * @param message The message to display in popup as textcomponent
     */
    default void logToast(Text title, Text message){
        ToastManager guitoast = MinecraftClient.getInstance().getToastManager();
        if(Baritone.settings().allowToast.value) {
            BaritoneToast.addOrUpdate(guitoast, title, message, Baritone.settings().toastTimer.value);
        }
    }

    /**
     * Send a message to the toaster to show it as a popup
     *
     * @param title The title to display in popup (in default, baritone prefix)
     * @param message The message to display in popup
     */
    default void logToast(String title, String message){
        Text titleLine = new LiteralText(title);
        Text subtitle = new LiteralText(message);
        logToast(titleLine,subtitle);
    }

    /**
     * Send a message to the toaster to show it as a popup
     * Send components to popup with the [Baritone] prefix (title)
     *
     * @param message The message to display in popup
     */
    default void logToast(String message){
        Text title = Helper.getPrefix();
        Text subtitle = new LiteralText(message);
        logToast(title,subtitle);
    }

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
     * Send components to chat with the [Baritone] prefix
     *
     * @param components The components to send
     */
    default void logDirect(Text... components) {
        Text component = new LiteralText("");
        component.append(getPrefix());
        component.append(new LiteralText(" "));
        Arrays.asList(components).forEach(component::append);
        mc.execute(() -> BaritoneAPI.getSettings().logger.value.accept(component));
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message The message to display in chat
     * @param color   The color to print that message in
     */
    default void logDirect(String message, Formatting color) {
        Stream.of(message.split("\n")).forEach(line -> {
            Text component = new LiteralText(line.replace("\t", "    "));
            component.getStyle().setColor(color);
            logDirect(component);
        });
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message The message to display in chat
     */
    default void logDirect(String message) {
        logDirect(message, Formatting.GRAY);
    }
}
