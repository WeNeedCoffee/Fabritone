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

package baritone.utils.accessor;

import java.awt.*;
import java.net.URI;
import java.net.URL;

public class GuiScreenUtils {

    /**
     * Opens the Specified Url in a Browser, if able
     *
     * @param targetUrl The URL to Open, as a String
     */
    public static void openUrl(final String targetUrl) {
        try {
            openUrl(new URI(targetUrl));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Opens the Specified Url in a Browser, if able
     *
     * @param targetUrl The URL to Open, as a URL
     */
    public static void openUrl(final URL targetUrl) {
        try {
            openUrl(targetUrl.toURI());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Opens the Specified Url in a Browser, if able
     *
     * @param targetUrl The URL to Open, as a URI
     */
    public static void openUrl(final URI targetUrl) {
        try {
            final Desktop desktop = Desktop.getDesktop();
            desktop.browse(targetUrl);
        } catch (Exception ex) {
            try {
                final Runtime runtime = Runtime.getRuntime();
                runtime.exec("xdg-open " + targetUrl.toString());
            } catch (Exception ex2) {
                ex2.printStackTrace();
            }
        }
    }

}
