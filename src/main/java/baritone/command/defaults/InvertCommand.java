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
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalInverted;
import baritone.api.process.ICustomGoalProcess;

public class InvertCommand extends Command {

	public InvertCommand(IBaritone baritone) {
		super(baritone, "invert");
	}

	@Override
	public void execute(String label, IArgConsumer args) throws CommandException {
		args.requireMax(0);
		ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
		Goal goal;
		if ((goal = customGoalProcess.getGoal()) == null)
			throw new CommandInvalidStateException("No goal");
		if (goal instanceof GoalInverted) {
			goal = ((GoalInverted) goal).origin;
		} else {
			goal = new GoalInverted(goal);
		}
		customGoalProcess.setGoalAndPath(goal);
		logToast(String.format("Goal: %s", goal.toString()));
	}

	@Override
	public List<String> getLongDesc() {
		return Arrays.asList("The invert command tells Baritone to head away from the current goal rather than towards it.", "", "Usage:", "> invert - Invert the current goal.");
	}

	@Override
	public String getShortDesc() {
		return "Run away from the current goal";
	}

	@Override
	public Stream<String> tabComplete(String label, IArgConsumer args) {
		return Stream.empty();
	}
}
