/***********************************************************************************
* Copyright (c) 2015 /// Project SWG /// www.projectswg.com                        *
*                                                                                  *
* ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on           *
* July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies.  *
* Our goal is to create an emulator which will provide a server for players to     *
* continue playing a game similar to the one they used to play. We are basing      *
* it on the final publish of the game prior to end-game events.                    *
*                                                                                  *
* This file is part of Holocore.                                                   *
*                                                                                  *
* -------------------------------------------------------------------------------- *
*                                                                                  *
* Holocore is free software: you can redistribute it and/or modify                 *
* it under the terms of the GNU Affero General Public License as                   *
* published by the Free Software Foundation, either version 3 of the               *
* License, or (at your option) any later version.                                  *
*                                                                                  *
* Holocore is distributed in the hope that it will be useful,                      *
* but WITHOUT ANY WARRANTY; without even the implied warranty of                   *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                    *
* GNU Affero General Public License for more details.                              *
*                                                                                  *
* You should have received a copy of the GNU Affero General Public License         *
* along with Holocore.  If not, see <http://www.gnu.org/licenses/>.                *
*                                                                                  *
***********************************************************************************/
package services.objects;

import intents.PlayerEventIntent;
import intents.object.ObjectCreateIntent;
import intents.object.ObjectCreatedIntent;
import intents.object.ObjectTeleportIntent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import main.ProjectSWG;
import network.packets.swg.zone.UpdateContainmentMessage;
import network.packets.swg.zone.insertion.CmdStartScene;
import resources.Location;
import resources.Race;
import resources.Terrain;
import resources.control.Intent;
import resources.control.Service;
import resources.objects.SWGObject;
import resources.objects.building.BuildingObject;
import resources.objects.creature.CreatureObject;
import resources.objects.quadtree.QuadTree;
import resources.objects.tangible.TangibleObject;
import resources.player.Player;
import resources.player.PlayerEvent;

public class ObjectAwareness extends Service {
	
	private static final double AWARE_RANGE = 1024;
	private static final int DEFAULT_LOAD_RANGE = (int) square(200); // Squared for speed
	
	private final Map <Terrain, QuadTree <SWGObject>> quadTree;
	
	public ObjectAwareness() {
		quadTree = new HashMap<Terrain, QuadTree<SWGObject>>();
	}
	
	@Override
	public boolean initialize() {
		registerForIntent(PlayerEventIntent.TYPE);
		registerForIntent(ObjectCreateIntent.TYPE);
		registerForIntent(ObjectCreatedIntent.TYPE);
		registerForIntent(ObjectTeleportIntent.TYPE);
		loadQuadTree();
		return true;
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		switch (i.getType()) {
			case PlayerEventIntent.TYPE:
				if (i instanceof PlayerEventIntent)
					handlePlayerEventIntent((PlayerEventIntent) i);
				break;
			case ObjectCreateIntent.TYPE:
				if (i instanceof ObjectCreateIntent)
					handleObjectCreateIntent((ObjectCreateIntent) i);
				break;
			case ObjectCreatedIntent.TYPE:
				if (i instanceof ObjectCreatedIntent)
					handleObjectCreatedIntent((ObjectCreatedIntent) i);
			case ObjectTeleportIntent.TYPE:
				if (i instanceof ObjectTeleportIntent)
					processObjectTeleportIntent((ObjectTeleportIntent) i);
				break;
			default:
				break;
		}
	}
	
	private void handlePlayerEventIntent(PlayerEventIntent pei) {
		Player p = pei.getPlayer();
		CreatureObject creature = p.getCreatureObject();
		if (creature == null)
			return;
		switch (pei.getEvent()) {
			case PE_DISAPPEAR:
				creature.clearAware();
				remove(creature);
				for (SWGObject obj : creature.getObservers())
					creature.destroyObject(obj.getOwner());
				creature.setOwner(null);
				p.setCreatureObject(null);
				break;
			case PE_FIRST_ZONE:
				if (creature.getParent() == null)
					creature.createObject(p);
				break;
			case PE_ZONE_IN:
				creature.clearAware();
				update(creature);
				break;
			default:
				break;
		}
	}
	
	private void handleObjectCreateIntent(ObjectCreateIntent oci) {
		SWGObject obj = oci.getObject();
		if (obj.getParent() == null) {
			if (obj instanceof TangibleObject || obj instanceof BuildingObject) {
				add(obj);
			}
		}
	}
	
	private void handleObjectCreatedIntent(ObjectCreatedIntent oci) {
		SWGObject obj = oci.getObject();
		if (obj.getParent() == null) {
			if (obj instanceof TangibleObject || obj instanceof BuildingObject) {
				add(obj);
			}
		}
	}
	
	private void processObjectTeleportIntent(ObjectTeleportIntent oti) {
		SWGObject object = oti.getObject();
		Player owner = object.getOwner();
		boolean creature = object instanceof CreatureObject && owner != null;
		if (oti.getParent() != null) {
			move(object, oti.getParent(), oti.getNewLocation());
			if (creature)
				startScene((CreatureObject) object, oti.getNewLocation());
		} else {
			move(object, oti.getNewLocation());
			if (creature)
				startScene((CreatureObject) object, oti.getNewLocation());
			object.createObject(owner);
		}
	}
	
	private void startScene(CreatureObject object, Location newLocation) {
		long time = (long) (ProjectSWG.getCoreTime() / 1E3);
		Race race = ((CreatureObject)object).getRace();
		sendPacket(object.getOwner(), new CmdStartScene(false, object.getObjectId(), race, newLocation, time, (int)(System.currentTimeMillis()/1E3)));
		new PlayerEventIntent(object.getOwner(), PlayerEvent.PE_ZONE_IN).broadcast();
	}
	
	private void loadQuadTree() {
		for (Terrain t : Terrain.values()) {
			quadTree.put(t, new QuadTree<SWGObject>(16, -8192, -8192, 8192, 8192));
		}
	}
	
	/**
	 * Adds the specified object to the awareness quadtree
	 * @param object the object to add
	 */
	public void add(SWGObject object) {
		update(object);
		Location l = object.getLocation();
		if (invalidLocation(l))
			return;
		QuadTree <SWGObject> tree = getTree(l);
		synchronized (tree) {
			tree.put(l.getX(), l.getZ(), object);
		}
	}
	
	/**
	 * Removes the specified object from the awareness quadtree
	 * @param object the object to remove
	 */
	public void remove(SWGObject object) {
		Location l = object.getLocation();
		if (invalidLocation(l))
			return;
		QuadTree <SWGObject> tree = getTree(l);
		synchronized (tree) {
			tree.remove(l.getX(), l.getZ(), object);
		}
	}
	
	/**
	 * This function is used for moving objects within the world, which
	 * includes moving from a cell to the world.
	 * @param object the object to move
	 * @param nLocation the new location
	 */
	public void move(SWGObject object, Location nLocation) {
		if (object.getParent() != null) {
			object.getParent().removeObject(object); // Moving from cell to world
			object.sendObserversAndSelf(new UpdateContainmentMessage(object.getObjectId(), 0, object.getSlotArrangement()));
		} else {
			remove(object); // World to World
		}
		object.setLocation(nLocation);
		add(object);
	}
	
	/**
	 * This function is used for moving objects to or within containers,
	 * probably a cell. This handles the logic for removing the object from the
	 * previous cell and adding it to the new one, if necessary.
	 * @param object the object to move
	 * @param nParent the new parent the object will be in
	 * @param nLocation the new location relative to the parent
	 */
	public void move(SWGObject object, SWGObject nParent, Location nLocation) {
		SWGObject parent = object.getParent();
		if (parent != null && nParent != parent) {
			parent.removeObject(object); // Moving from cell to cell, for instance
		} else if (parent == null) {
			remove(object); // Moving from world to cell
		}
		if (object.getParent() == null) { // Should have been updated in removeObject()
			nParent.addObject(object); // If necessary, add to new cell
			object.sendObserversAndSelf(new UpdateContainmentMessage(object.getObjectId(), nParent.getObjectId(), object.getSlotArrangement()));
		}
		object.setLocation(nLocation);
		update(object);
	}
	
	/**
	 * Updates the specified object after it has been moved, or to verify that
	 * the awareness is up to date
	 * @param obj the object to update
	 */
	public void update(SWGObject obj) {
		if (obj.isBuildout())
			return;
		Location l = obj.getWorldLocation();
		if (invalidLocation(l))
			return;
		Set <SWGObject> objectAware = new HashSet<SWGObject>();
		QuadTree <SWGObject> tree = getTree(l);
		synchronized (tree) {
			List <SWGObject> range = tree.getWithinRange(l.getX(), l.getZ(), AWARE_RANGE);
			for (SWGObject inRange : range) {
				if (isValidInRange(obj, inRange, l))
					objectAware.add(inRange);
			}
		}
		obj.updateObjectAwareness(objectAware);
	}
	
	private QuadTree <SWGObject> getTree(Location l) {
		return quadTree.get(l.getTerrain());
	}
	
	private boolean invalidLocation(Location l) {
		return l == null || l.getTerrain() == null;
	}
	
	private boolean isValidInRange(SWGObject obj, SWGObject inRange, Location objLoc) {
		if (inRange.getObjectId() == obj.getObjectId())
			return false;
		int distSquared = distanceSquared(objLoc, inRange.getWorldLocation());
		int loadSquared = (int) (square(inRange.getLoadRange()) + 0.5);
		return (loadSquared != 0 || distSquared <= DEFAULT_LOAD_RANGE) && (loadSquared == 0 || distSquared <= loadSquared);
	}
	
	private int distanceSquared(Location l1, Location l2) {
		return (int) (square(l1.getX()-l2.getX()) + square(l1.getY()-l2.getY()) + square(l1.getZ()-l2.getZ()) + 0.5);
	}
	
	private static double square(double x) {
		return x * x;
	}
	
}