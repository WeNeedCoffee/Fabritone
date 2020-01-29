/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import baritone.Baritone;
import baritone.api.event.events.BlockInteractEvent;
import baritone.api.event.events.ChatEvent;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.PathEvent;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.event.events.SprintStateEvent;
import baritone.api.event.events.TabCompleteEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.IEventBus;
import baritone.api.event.listener.IGameEventListener;
import baritone.api.utils.Helper;
import baritone.cache.WorldProvider;
import baritone.utils.BlockStateInterface;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * @author Brady
 * @since 7/31/2018
 */
public final class GameEventHandler implements IEventBus, Helper {

	private final Baritone baritone;

	private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();

	public GameEventHandler(Baritone baritone) {
		this.baritone = baritone;
	}

	@Override
	public void onBlockInteract(BlockInteractEvent event) {
		listeners.forEach(l -> l.onBlockInteract(event));
	}

	@Override
	public void onChunkEvent(ChunkEvent event) {
		EventState state = event.getState();
		ChunkEvent.Type type = event.getType();

		boolean isPostPopulate = state == EventState.POST && (type == ChunkEvent.Type.POPULATE_FULL || type == ChunkEvent.Type.POPULATE_PARTIAL);

		World world = baritone.getPlayerContext().world();

		// Whenever the server sends us to another dimension, chunks are unloaded
		// technically after the new world has been loaded, so we perform a check
		// to make sure the chunk being unloaded is already loaded.
		boolean isPreUnload = state == EventState.PRE && type == ChunkEvent.Type.UNLOAD && world.getChunkManager().getChunk(event.getX(), event.getZ(), null, false) != null;

		if (isPostPopulate || isPreUnload) {
			baritone.getWorldProvider().ifWorldLoaded(worldData -> {
				WorldChunk chunk = world.getChunk(event.getX(), event.getZ());
				worldData.getCachedWorld().queueForPacking(chunk);
			});
		}

		listeners.forEach(l -> l.onChunkEvent(event));
	}

	@Override
	public void onPathEvent(PathEvent event) {
		listeners.forEach(l -> l.onPathEvent(event));
	}

	@Override
	public void onPlayerDeath() {
		listeners.forEach(IGameEventListener::onPlayerDeath);
	}

	@Override
	public void onPlayerRotationMove(RotationMoveEvent event) {
		listeners.forEach(l -> l.onPlayerRotationMove(event));
	}

	@Override
	public void onPlayerSprintState(SprintStateEvent event) {
		listeners.forEach(l -> l.onPlayerSprintState(event));
	}

	@Override
	public void onPlayerUpdate(PlayerUpdateEvent event) {
		listeners.forEach(l -> l.onPlayerUpdate(event));
	}

	@Override
	public void onPreTabComplete(TabCompleteEvent event) {
		listeners.forEach(l -> l.onPreTabComplete(event));
	}

	@Override
	public void onReceivePacket(PacketEvent event) {
		listeners.forEach(l -> l.onReceivePacket(event));
	}

	@Override
	public void onRenderPass(RenderEvent event) {
		listeners.forEach(l -> l.onRenderPass(event));
	}

	@Override
	public void onSendChatMessage(ChatEvent event) {
		listeners.forEach(l -> l.onSendChatMessage(event));
	}

	@Override
	public void onSendPacket(PacketEvent event) {
		listeners.forEach(l -> l.onSendPacket(event));
	}

	@Override
	public void onTick(TickEvent event) {
		if (event.getType() == TickEvent.Type.IN) {
			try {
				baritone.bsi = new BlockStateInterface(baritone.getPlayerContext(), true);
			} catch (Exception ex) {
				ex.printStackTrace();
				baritone.bsi = null;
			}
		} else {
			baritone.bsi = null;
		}
		listeners.forEach(l -> l.onTick(event));
	}

	@Override
	public void onWorldEvent(WorldEvent event) {
		WorldProvider cache = baritone.getWorldProvider();

		if (event.getState() == EventState.POST) {
			cache.closeWorld();
			if (event.getWorld() != null) {
				cache.initWorld(event.getWorld().getDimension().getType());
			}
		}

		listeners.forEach(l -> l.onWorldEvent(event));
	}

	@Override
	public void registerEventListener(IGameEventListener listener) {
		listeners.add(listener);
	}
}
