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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.behavior.PathingBehavior;
import baritone.pathing.path.PathExecutor;
import net.minecraft.util.math.BlockPos;

public class PathingControlManager implements IPathingControlManager {

	private final Baritone baritone;
	private final HashSet<IBaritoneProcess> processes; // unGh
	private final List<IBaritoneProcess> active;
	private IBaritoneProcess inControlLastTick;
	private IBaritoneProcess inControlThisTick;
	private PathingCommand command;

	public PathingControlManager(Baritone baritone) {
		this.baritone = baritone;
		processes = new HashSet<>();
		active = new ArrayList<>();
		baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() { // needs to be after all behavior ticks
			@Override
			public void onTick(TickEvent event) {
				if (event.getType() == TickEvent.Type.IN) {
					postTick();
				}
			}
		});
	}

	public void cancelEverything() { // called by PathingBehavior on TickEvent Type OUT
		inControlLastTick = null;
		inControlThisTick = null;
		command = null;
		active.clear();
		for (IBaritoneProcess proc : processes) {
			proc.onLostControl();
			if (proc.isActive() && !proc.isTemporary())
				throw new IllegalStateException(proc.displayName());
		}
	}

	public PathingCommand executeProcesses() {
		for (IBaritoneProcess process : processes) {
			if (process.isActive()) {
				if (!active.contains(process)) {
					// put a newly active process at the very front of the queue
					active.add(0, process);
				}
			} else {
				active.remove(process);
			}
		}
		// ties are broken by which was added to the beginning of the list first
		active.sort(Comparator.comparingDouble(IBaritoneProcess::priority).reversed());

		Iterator<IBaritoneProcess> iterator = active.iterator();
		while (iterator.hasNext()) {
			IBaritoneProcess proc = iterator.next();

			PathingCommand exec = proc.onTick(Objects.equals(proc, inControlLastTick) && baritone.getPathingBehavior().calcFailedLastTick(), baritone.getPathingBehavior().isSafeToCancel());
			if (exec == null) {
				if (proc.isActive())
					throw new IllegalStateException(proc.displayName() + " actively returned null PathingCommand");
			} else if (exec.commandType != PathingCommandType.DEFER) {
				inControlThisTick = proc;
				if (!proc.isTemporary()) {
					iterator.forEachRemaining(IBaritoneProcess::onLostControl);
				}
				return exec;
			}
		}
		return null;
	}

	public boolean forceRevalidate(Goal newGoal) {
		PathExecutor current = baritone.getPathingBehavior().getCurrent();
		if (current != null) {
			if (newGoal.isInGoal(current.getPath().getDest()))
				return false;
			return !newGoal.toString().equals(current.getPath().getGoal().toString());
		}
		return false;
	}

	@Override
	public Optional<PathingCommand> mostRecentCommand() {
		return Optional.ofNullable(command);
	}

	@Override
	public Optional<IBaritoneProcess> mostRecentInControl() {
		return Optional.ofNullable(inControlThisTick);
	}

	private void postTick() {
		// if we did this in pretick, it would suck
		// we use the time between ticks as calculation time
		// therefore, we only cancel and recalculate after the tick for the current path has executed
		// "it would suck" means it would actually execute a path every other tick
		if (command == null)
			return;
		PathingBehavior p = baritone.getPathingBehavior();
		switch (command.commandType) {
			case FORCE_REVALIDATE_GOAL_AND_PATH:
				if (command.goal == null || forceRevalidate(command.goal) || revalidateGoal(command.goal)) {
					// pwnage
					p.softCancelIfSafe();
				}
				p.secretInternalSetGoalAndPath(command);
				break;
			case REVALIDATE_GOAL_AND_PATH:
				if (Baritone.settings().cancelOnGoalInvalidation.value && (command.goal == null || revalidateGoal(command.goal))) {
					p.softCancelIfSafe();
				}
				p.secretInternalSetGoalAndPath(command);
				break;
			default:
		}
	}

	public void preTick() {
		inControlLastTick = inControlThisTick;
		inControlThisTick = null;
		PathingBehavior p = baritone.getPathingBehavior();
		command = executeProcesses();
		if (command == null) {
			p.cancelSegmentIfSafe();
			p.secretInternalSetGoal(null);
			return;
		}
		if (!Objects.equals(inControlThisTick, inControlLastTick) && command.commandType != PathingCommandType.REQUEST_PAUSE && inControlLastTick != null && !inControlLastTick.isTemporary()) {
			// if control has changed from a real process to another real process, and the new process wants to do something
			p.cancelSegmentIfSafe();
			// get rid of the in progress stuff from the last process
		}
		switch (command.commandType) {
			case REQUEST_PAUSE:
				p.requestPause();
				break;
			case CANCEL_AND_SET_GOAL:
				p.secretInternalSetGoal(command.goal);
				p.cancelSegmentIfSafe();
				break;
			case FORCE_REVALIDATE_GOAL_AND_PATH:
				if (!p.isPathing() && !p.getInProgress().isPresent()) {
					p.secretInternalSetGoalAndPath(command);
				}
				break;
			case REVALIDATE_GOAL_AND_PATH:
				if (!p.isPathing() && !p.getInProgress().isPresent()) {
					p.secretInternalSetGoalAndPath(command);
				}
				break;
			case SET_GOAL_AND_PATH:
				// now this i can do
				if (command.goal != null) {
					baritone.getPathingBehavior().secretInternalSetGoalAndPath(command);
				}
				break;
			default:
				throw new IllegalStateException();
		}
	}

	@Override
	public void registerProcess(IBaritoneProcess process) {
		process.onLostControl(); // make sure it's reset
		processes.add(process);
	}

	public boolean revalidateGoal(Goal newGoal) {
		PathExecutor current = baritone.getPathingBehavior().getCurrent();
		if (current != null) {
			Goal intended = current.getPath().getGoal();
			BlockPos end = current.getPath().getDest();
			if (intended.isInGoal(end) && !newGoal.isInGoal(end))
				// this path used to end in the goal
				// but the goal has changed, so there's no reason to continue...
				return true;
		}
		return false;
	}
}
