/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;

public class ForceCancelCommand extends Command {

	public ForceCancelCommand(IBaritone baritone) {
		super(baritone, "forcecancel");
	}

	@Override
	public void execute(String label, IArgConsumer args) throws CommandException {
		args.requireMax(0);
		IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
		pathingBehavior.cancelEverything();
		pathingBehavior.forceCancel();
		logToast("ok force canceled");
	}

	@Override
	public List<String> getLongDesc() {
		return Arrays.asList("Like cancel, but more forceful.", "", "Usage:", "> forcecancel");
	}

	@Override
	public String getShortDesc() {
		return "Force cancel";
	}

	@Override
	public Stream<String> tabComplete(String label, IArgConsumer args) {
		return Stream.empty();
	}
}
