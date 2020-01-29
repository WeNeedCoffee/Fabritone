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
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;

/**
 * Contains the pause, resume, and paused commands.
 * <p>
 * This thing is scoped to hell, private so far you can't even access it using reflection, because you AREN'T SUPPOSED TO USE THIS to pause and resume Baritone. Make your own process that returns {@link PathingCommandType#REQUEST_PAUSE REQUEST_PAUSE} as needed.
 */
public class ExecutionControlCommands {

	Command pauseCommand;
	Command resumeCommand;
	Command pausedCommand;
	Command cancelCommand;

	public ExecutionControlCommands(IBaritone baritone) {
		// array for mutability, non-field so reflection can't touch it
		final boolean[] paused = { false };
		baritone.getPathingControlManager().registerProcess(new IBaritoneProcess() {
			@Override
			public String displayName0() {
				return "Pause/Resume Commands";
			}

			@Override
			public boolean isActive() {
				return paused[0];
			}

			@Override
			public boolean isTemporary() {
				return true;
			}

			@Override
			public void onLostControl() {
			}

			@Override
			public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
				return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
			}

			@Override
			public double priority() {
				return DEFAULT_PRIORITY + 1;
			}
		});
		pauseCommand = new Command(baritone, "pause") {
			@Override
			public void execute(String label, IArgConsumer args) throws CommandException {
				args.requireMax(0);
				if (paused[0])
					throw new CommandInvalidStateException("Already paused");
				paused[0] = true;
				logToast("Paused");
			}

			@Override
			public List<String> getLongDesc() {
				return Arrays.asList("The pause command tells Baritone to temporarily stop whatever it's doing.", "", "This can be used to pause pathing, building, following, whatever. A single use of the resume command will start it right back up again!", "", "Usage:", "> pause");
			}

			@Override
			public String getShortDesc() {
				return "Pauses Baritone until you use resume";
			}

			@Override
			public Stream<String> tabComplete(String label, IArgConsumer args) {
				return Stream.empty();
			}
		};
		resumeCommand = new Command(baritone, "resume") {
			@Override
			public void execute(String label, IArgConsumer args) throws CommandException {
				args.requireMax(0);
				baritone.getBuilderProcess().resume();
				if (!paused[0])
					throw new CommandInvalidStateException("Not paused");
				paused[0] = false;
				logToast("Resumed");
			}

			@Override
			public List<String> getLongDesc() {
				return Arrays.asList("The resume command tells Baritone to resume whatever it was doing when you last used pause.", "", "Usage:", "> resume");
			}

			@Override
			public String getShortDesc() {
				return "Resumes Baritone after a pause";
			}

			@Override
			public Stream<String> tabComplete(String label, IArgConsumer args) {
				return Stream.empty();
			}
		};
		pausedCommand = new Command(baritone, "paused") {
			@Override
			public void execute(String label, IArgConsumer args) throws CommandException {
				args.requireMax(0);
				logToast(String.format("Baritone is %spaused", paused[0] ? "" : "not "));
			}

			@Override
			public List<String> getLongDesc() {
				return Arrays.asList("The paused command tells you if Baritone is currently paused by use of the pause command.", "", "Usage:", "> paused");
			}

			@Override
			public String getShortDesc() {
				return "Tells you if Baritone is paused";
			}

			@Override
			public Stream<String> tabComplete(String label, IArgConsumer args) {
				return Stream.empty();
			}
		};
		cancelCommand = new Command(baritone, "cancel", "stop") {
			@Override
			public void execute(String label, IArgConsumer args) throws CommandException {
				args.requireMax(0);
				if (paused[0]) {
					paused[0] = false;
				}
				baritone.getPathingBehavior().cancelEverything();
				logDirect("ok canceled");
			}

			@Override
			public List<String> getLongDesc() {
				return Arrays.asList("The cancel command tells Baritone to stop whatever it's currently doing.", "", "Usage:", "> cancel");
			}

			@Override
			public String getShortDesc() {
				return "Cancel what Baritone is currently doing";
			}

			@Override
			public Stream<String> tabComplete(String label, IArgConsumer args) {
				return Stream.empty();
			}
		};
	}
}
