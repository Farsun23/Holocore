/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/
package com.projectswg.holocore.services.support.objects.awareness;

import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.network.packets.SWGPacket;
import com.projectswg.common.network.packets.swg.zone.*;
import com.projectswg.common.network.packets.swg.zone.chat.ChatOnConnectAvatar;
import com.projectswg.common.network.packets.swg.zone.chat.VoiceChatStatus;
import com.projectswg.common.network.packets.swg.zone.insertion.ChatServerStatus;
import com.projectswg.common.network.packets.swg.zone.insertion.CmdStartScene;
import com.projectswg.common.network.packets.swg.zone.object_controller.DataTransform;
import com.projectswg.common.network.packets.swg.zone.object_controller.DataTransformWithParent;
import com.projectswg.holocore.ProjectSWG;
import com.projectswg.holocore.intents.support.global.network.CloseConnectionIntent;
import com.projectswg.holocore.intents.support.global.network.InboundPacketIntent;
import com.projectswg.holocore.intents.support.global.zone.PlayerEventIntent;
import com.projectswg.holocore.intents.support.global.zone.PlayerTransformedIntent;
import com.projectswg.holocore.intents.support.global.zone.RequestZoneInIntent;
import com.projectswg.holocore.intents.support.objects.awareness.ForceAwarenessUpdateIntent;
import com.projectswg.holocore.intents.support.objects.swg.*;
import com.projectswg.holocore.resources.support.global.network.DisconnectReason;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.global.player.PlayerEvent;
import com.projectswg.holocore.resources.support.global.player.PlayerState;
import com.projectswg.holocore.resources.support.objects.awareness.AwarenessType;
import com.projectswg.holocore.resources.support.objects.awareness.ObjectAwareness;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.services.support.objects.ObjectStorageService.ObjectLookup;
import me.joshlarson.jlcommon.control.Intent;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;
import me.joshlarson.jlcommon.log.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AwarenessService extends Service {
	
	private static final Location GONE_LOCATION = Location.builder().setTerrain(Terrain.GONE).setPosition(0, 0, 0).build();
	
	private final ObjectAwareness awareness;
	
	public AwarenessService() {
		this.awareness = new ObjectAwareness();
	}
	
	@IntentHandler
	private void handlePlayerEventIntent(PlayerEventIntent pei) {
		Player p = pei.getPlayer();
		CreatureObject creature = p.getCreatureObject();
		switch (pei.getEvent()) {
			case PE_DESTROYED:
				assert creature != null;
				creature.setOwner(null);
				awareness.destroyObject(creature);
				break;
			default:
				break;
		}
	}
	
	@IntentHandler
	private void handleObjectCreatedIntent(ObjectCreatedIntent oci) {
		oci.getObject().updateLoadRange();
		awareness.createObject(oci.getObject());
	}
	
	@IntentHandler
	private void handleDestroyObjectIntent(DestroyObjectIntent doi) {
		SWGObject obj = doi.getObject();
		synchronized (obj.getAwarenessLock()) {
			obj.systemMove(null, GONE_LOCATION);
			awareness.destroyObject(doi.getObject());
		}
	}
	
	@IntentHandler
	private void processObjectTeleportIntent(ObjectTeleportIntent oti) {
		@NotNull SWGObject obj = oti.getObject();
		@Nullable SWGObject oldParent = obj.getParent();
		@Nullable SWGObject newParent = oti.getNewParent();
		@NotNull Location oldLocation = obj.getLocation();
		@NotNull Location newLocation = oti.getNewLocation();
		
		if (isPlayerZoneInRequired(obj, newParent, newLocation)) {
			handleZoneIn((CreatureObject) obj, newLocation, newParent);
		} else {
			synchronized (obj.getAwarenessLock()) {
				obj.systemMove(newParent, newLocation);
				sendTeleportPackets(obj, newParent, 0, true);
				awareness.updateObject(obj);
			}
		}
		
		update(oldParent);
		onObjectMoved(obj, oldParent, newParent, oldLocation, newLocation);
	}
	
	@IntentHandler
	private void processGalacticPacketIntent(InboundPacketIntent gpi) {
		SWGPacket packet = gpi.getPacket();
		if (packet instanceof DataTransform) {
			handleDataTransform((DataTransform) packet);
		} else if (packet instanceof DataTransformWithParent) {
			handleDataTransformWithParent((DataTransformWithParent) packet);
		} else if (packet instanceof CmdSceneReady) {
			handleCmdSceneReady(gpi.getPlayer(), (CmdSceneReady) packet);
		}
	}
	
	@IntentHandler
	private void processMoveObjectIntent(MoveObjectIntent moi) {
		moveObjectWithTransform(moi.getObject(), moi.getParent(), moi.getNewLocation(), moi.getSpeed());
	}
	
	@IntentHandler
	private void processContainerTransferIntent(ContainerTransferIntent cti) {
		@NotNull SWGObject obj = cti.getObject();
		@Nullable SWGObject oldContainer = cti.getOldContainer();
		@Nullable SWGObject newContainer = cti.getContainer();
		
		update(cti.getObject());
		update(oldContainer);
		
		obj.sendObservers(new UpdateContainmentMessage(obj.getObjectId(), newContainer == null ? 0 : newContainer.getObjectId(), obj.getSlotArrangement()));
		onObjectMoved(obj, oldContainer, newContainer, obj.getLocation(), obj.getLocation());
	}
	
	@IntentHandler
	private void handleForceAwarenessUpdateIntent(ForceAwarenessUpdateIntent faui) {
		update(faui.getObject());
	}
	
	@IntentHandler
	private void handleRequestZoneInIntent(RequestZoneInIntent rzii) {
		handleZoneIn(rzii.getCreature(), rzii.getCreature().getLocation(), rzii.getCreature().getParent());
	}
	
	private void handleZoneIn(CreatureObject creature, Location loc, SWGObject parent) {
		Player player = creature.getOwner();
		
		// Fresh login or teleport/travel
		PlayerState state = player.getPlayerState();
		boolean firstZone = (state == PlayerState.LOGGED_IN);
		if (!firstZone && state != PlayerState.ZONED_IN) {
			CloseConnectionIntent.broadcast(player, DisconnectReason.SUSPECTED_HACK);
			return;
		}
		
		SWGObject oldParent = creature.getParent();
		Location oldLocation = creature.getLocation();
		synchronized (creature.getAwarenessLock()) {
			// Safely clear awareness
			creature.setOwner(null);
			creature.setAware(AwarenessType.OBJECT, List.of());
			creature.setOwner(player);
			
			creature.systemMove(parent, loc);
			creature.resetObjectsAware();
			startZone(creature, firstZone);
			awareness.updateObject(creature);
		}
		onObjectMoved(creature, oldParent, parent, oldLocation, loc);
	}
	
	private void startZone(CreatureObject creature, boolean firstZone) {
		Player player = creature.getOwner();
		player.setPlayerState(PlayerState.ZONING_IN);
		Intent firstZoneIntent = null;
		if (firstZone) {
			player.sendPacket(new HeartBeat());
			player.sendPacket(new ChatServerStatus(true));
			player.sendPacket(new VoiceChatStatus());
			player.sendPacket(new ParametersMessage());
			player.sendPacket(new ChatOnConnectAvatar());
			firstZoneIntent = new PlayerEventIntent(player, PlayerEvent.PE_FIRST_ZONE);
			firstZoneIntent.broadcast();
		}
		Location loc = creature.getWorldLocation();
		player.sendPacket(new CmdStartScene(true, creature.getObjectId(), creature.getRace(), loc, ProjectSWG.getGalacticTime(), (int) (System.currentTimeMillis() / 1E3)));
		Log.i("Zoning in %s with character %s to %s %s", player.getUsername(), player.getCharacterName(), loc.getPosition(), loc.getTerrain());
		new PlayerEventIntent(player, PlayerEvent.PE_ZONE_IN_CLIENT).broadcastAfterIntent(firstZoneIntent);
	}
	
	private void handleCmdSceneReady(Player player, CmdSceneReady p) {
		assert player.getPlayerState() == PlayerState.ZONING_IN;
		player.setPlayerState(PlayerState.ZONED_IN);
		Log.i("%s with character %s zoned in from %s", player.getUsername(), player.getCharacterName(), p.getSocketAddress());
		new PlayerEventIntent(player, PlayerEvent.PE_ZONE_IN_SERVER).broadcast();
		player.sendPacket(new CmdSceneReady());
		player.sendBufferedDeltas();
	}
	
	private void handleDataTransform(DataTransform dt) {
		SWGObject obj = ObjectLookup.getObjectById(dt.getObjectId());
		if (!(obj instanceof CreatureObject)) {
			Log.w("DataTransform object not CreatureObject! Was: " + (obj == null ? "null" : obj.getClass()));
			return;
		}
		Location requestedLocation = Location.builder(dt.getLocation()).setTerrain(obj.getTerrain()).build();
		moveObjectWithTransform(obj, null, requestedLocation, dt.getSpeed());
	}
	
	private void handleDataTransformWithParent(DataTransformWithParent dt) {
		SWGObject obj = ObjectLookup.getObjectById(dt.getObjectId());
		SWGObject parent = ObjectLookup.getObjectById(dt.getCellId());
		if (!(obj instanceof CreatureObject)) {
			Log.w("DataTransformWithParent object not CreatureObject! Was: " + (obj == null ? "null" : obj.getClass()));
			return;
		}
		if (parent == null) {
			Log.w("Unknown data transform parent! Obj: %d/%s  Parent: %d", dt.getObjectId(), obj, dt.getCellId());
			return;
		}
		Location requestedLocation = Location.builder(dt.getLocation()).setTerrain(obj.getTerrain()).build();
		moveObjectWithTransform(obj, parent, requestedLocation, dt.getSpeed());
	}
	
	private void moveObjectWithTransform(SWGObject obj, SWGObject parent, Location requestedLocation, double speed) {
		if (obj.isExposeWithWorld()) {
			parent = obj.getParent();
			assert parent != null : "expose parent is null";
			
			synchronized (parent.getAwarenessLock()) {
				parent.systemMove(null, requestedLocation);
				awareness.updateObject(parent);
			}
			
			for (SWGObject child : parent.getSlottedObjects()) {
				SWGObject oldParent = child.getParent();
				Location oldLocation = child.getLocation();
				synchronized (child.getAwarenessLock()) {
					child.systemMove(parent, requestedLocation);
//					sendTeleportPackets(child, parent, speed, false);
				}
				onObjectMoved(child, oldParent, parent, oldLocation, requestedLocation);
			}
			
			sendTeleportPackets(parent, null, speed, false);
		} else {
			SWGObject oldParent = obj.getParent();
			Location oldLocation = obj.getLocation();
			synchronized (obj.getAwarenessLock()) {
				obj.systemMove(parent, requestedLocation);
				awareness.updateObject(obj);
			}
			
			sendTeleportPackets(obj, parent, speed, false);
			
			if (oldParent != parent)
				update(oldParent);
			onObjectMoved(obj, oldParent, parent, oldLocation, requestedLocation);
		}
	}
	
	private void update(@Nullable SWGObject obj) {
		if (obj != null) {
			synchronized (obj.getAwarenessLock()) {
				awareness.updateObject(obj);
			}
		}
	}
	
	private static boolean isPlayerZoneInRequired(@NotNull SWGObject obj, @Nullable SWGObject parent, @NotNull Location newLocation) {
		if (!(obj instanceof CreatureObject))
			return false;
		if (!((CreatureObject) obj).isLoggedInPlayer())
			return false;
		if (parent == null)
			return newLocation.getTerrain() != obj.getTerrain() || newLocation.distanceTo(obj.getWorldLocation()) > 1024;
		return !obj.getAware().contains(parent);
	}
	
	private static void sendTeleportPackets(@NotNull SWGObject obj, @Nullable SWGObject parent, double speed, boolean forceSelfUpdate) {
		@NotNull Location location = obj.getLocation();
		int counter = obj.getNextUpdateCount();
		if (parent != null) {
			if (forceSelfUpdate)
				obj.sendSelf(new DataTransformWithParent(obj.getObjectId(), 0, counter, parent.getObjectId(), location, (byte) speed));
			obj.sendObservers(new UpdateTransformWithParentMessage(obj.getObjectId(), parent.getObjectId(), counter, location, (byte) speed));
			obj.sendObservers(new UpdateContainmentMessage(obj.getObjectId(), parent.getObjectId(), obj.getSlotArrangement()));
		} else {
			if (forceSelfUpdate)
				obj.sendSelf(new DataTransform(obj.getObjectId(), 0, counter, location, (byte) speed));
			obj.sendObservers(new UpdateTransformMessage(obj.getObjectId(), counter, location, (byte) speed));
			obj.sendObservers(new UpdateContainmentMessage(obj.getObjectId(), 0, obj.getSlotArrangement()));
		}
	}
	
	private static void onObjectMoved(@NotNull SWGObject obj, @Nullable SWGObject oldParent, @Nullable SWGObject newParent, @NotNull Location oldLocation, @NotNull Location newLocation) {
		if (obj instanceof CreatureObject && ((CreatureObject) obj).isLoggedInPlayer())
			new PlayerTransformedIntent((CreatureObject) obj, oldParent, newParent, oldLocation, newLocation).broadcast();
	}
	
}
