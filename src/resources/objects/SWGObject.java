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
package resources.objects;

import network.packets.Packet;
import network.packets.swg.zone.SceneCreateObjectByCrc;
import network.packets.swg.zone.SceneDestroyObject;
import network.packets.swg.zone.SceneEndBaselines;
import network.packets.swg.zone.UpdateContainmentMessage;
import network.packets.swg.zone.baselines.Baseline.BaselineType;
import resources.Location;
import resources.Terrain;
import resources.buildout.BuildoutArea;
import resources.client_info.visitors.ObjectData.ObjectDataAttribute;
import resources.common.CRC;
import resources.containers.ContainerPermissionsType;
import resources.containers.ContainerResult;
import resources.encodables.StringId;
import resources.network.BaselineBuilder;
import resources.network.BaselineObject;
import resources.network.NetBuffer;
import resources.network.NetBufferStream;
import resources.objects.awareness.ObjectAware;
import resources.objects.building.BuildingObject;
import resources.objects.creature.CreatureObject;
import resources.persistable.Persistable;
import resources.persistable.SWGObjectFactory;
import resources.player.Player;
import resources.server_info.Log;
import services.CoreManager;
import services.objects.ObjectCreator;
import utilities.AwarenessUtilities;
import intents.object.ContainerTransferIntent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class SWGObject extends BaselineObject implements Comparable<SWGObject>, Persistable {
	
	private final long 							objectId;
	private final Location 						location		= new Location(0, 0, 0, null);
	private final Location						buildoutLocation= new Location(0, 0, 0, null);
	private final Set<SWGObject>				containedObjects= new HashSet<>();
	private final HashMap <String, SWGObject>	slots			= new HashMap<>(); // HashMap used for null value support
	private final transient Map <String, String>				attributes		= new LinkedHashMap<>();
	private final transient ObjectAware							awareness		= new ObjectAware(this);
	private final transient Map <ObjectDataAttribute, Object>	dataAttributes	= new HashMap<>();
	private final transient AtomicInteger						updateCounter	= new AtomicInteger(1);
	
	private ObjectClassification		classification	= ObjectClassification.GENERATED;
	private GameObjectType				gameObjectType	= GameObjectType.GOT_NONE;
	private ContainerPermissionsType	permissions		= ContainerPermissionsType.DEFAULT;
	private List <List <String>>		arrangement		= new ArrayList<>();
	private BuildoutArea				buildoutArea	= null;
	private Player						owner			= null;
	
	private SWGObject	parent			= null;
	private StringId 	stringId		= new StringId("", "");
	private StringId 	detailStringId	= new StringId("", "");
	private String		template		= "";
	private int			crc				= 0;
	private String		objectName		= "";
	private int			volume			= 0;
	private float		complexity		= 1;
	private int     	containerType	= 0;
	private double		prefLoadRange	= 200;
	private int			areaId			= -1;
	private int     	slotArrangement	= -1;
	
	public SWGObject() {
		this(0, null);
	}
	
	public SWGObject(long objectId, BaselineType objectType) {
		super(objectType);
		this.objectId = objectId;
	}
	
	/**
	 * Adds the specified object to this object and places it in the appropriate slot if needed
	 * @param object Object to add to this container, which will either be put into the appropriate slot(s) or become a contained object
	 */
	public void addObject(SWGObject object) {
		object.setSlotArrangement(getArrangementId(object));
		if (object.getSlotArrangement() == -1) {
			synchronized (containedObjects) {
				containedObjects.add(object);
				
				// We need to adjust the volume of our container accordingly!
				setVolume(getVolume() + object.getVolume() + 1);
			}
		} else {
			for (String requiredSlot : object.getArrangement().get(object.getSlotArrangement() - 4)) {
				setSlot(requiredSlot, object);
			}
		}
		object.parent = this;
		object.getAwareness().setParent(getAwareness());
	}
	
	/**
	 * Removes the specified object from this current object.
	 * @param object Object to remove
	 */
	protected void removeObject(SWGObject object) {
		if (object.getSlotArrangement() == -1) {
			synchronized (containedObjects) {
				containedObjects.remove(object);
				
				// We need to adjust the volume of our container accordingly!
				setVolume(getVolume() - object.getVolume() - 1);
			}
		} else {
			for (String requiredSlot : object.getArrangement().get(object.getSlotArrangement() - 4)) {
				setSlot(requiredSlot, null);
			}
		}
		
		// Inherit parent aware
		Set<SWGObject> oldAware = object.getObjectsAware();
		for (SWGObject obj : oldAware)
			object.getAwareness().addObjectAware(obj.getAwareness());
		object.getAwareness().addObjectAware(object.getSuperParent().getAwareness());
		// Remove as parent
		object.parent = null;
		object.getAwareness().setParent(null);
		object.slotArrangement = -1;
	}
	
	/**
	 * Moves this object to the passed container if the requester has the MOVE permission for the container
	 * @param requester Object that is requesting to move the object, used for permission checking
	 * @param container Where this object should be moved to
	 * @return {@link ContainerResult}
	 */
	public ContainerResult moveToContainer(SWGObject requester, SWGObject container) {
		ContainerResult result = moveToContainerChecks(requester, container);
		if (result != ContainerResult.SUCCESS)
			return result;
		
		Set<Player> oldObservers = getObserversAndParent();
		SWGObject parent = this.parent;
		if (parent != null)
			parent.removeObject(this);
		
		if (container != null) {
			int arrangement = container.getArrangementId(this);
			if (arrangement != -1)
				container.handleSlotReplacement(parent, this, arrangement);
			container.addObject(this);
			synchronized (location) {
				location.setTerrain(container.getTerrain());
			}
		}
		
		Set<Player> newObservers = getObserversAndParent();
		long newId = (container != null) ? container.getObjectId() : 0;
		UpdateContainmentMessage update = new UpdateContainmentMessage(getObjectId(), newId, getSlotArrangement());
		AwarenessUtilities.callForSameObserver(oldObservers, newObservers, (observer) -> observer.sendPacket(update));
		AwarenessUtilities.callForNewObserver(oldObservers, newObservers, (observer) -> createObject(observer, false));
		AwarenessUtilities.callForOldObserver(oldObservers, newObservers, (observer) -> destroyObject(observer));
		new ContainerTransferIntent(this, container).broadcast();
		return ContainerResult.SUCCESS;
	}

	/**
	 * Attempts to move this object to the defined container without checking for permissions
	 * @param container
	 * @return {@link ContainerResult}
	 */
	public ContainerResult moveToContainer(SWGObject container) {
		return moveToContainer(null, container);
	}
	
	private ContainerResult moveToContainerChecks(SWGObject requester, SWGObject container) {
		if (requester == null)
			return ContainerResult.SUCCESS;
		if (!permissions.canMove(requester, this)) {
			Log.w("SWGObject", "No permission 'MOVE' for requestor %s with object %s", requester, this);
			return ContainerResult.NO_PERMISSION;
		}
		if (container == null)
			return ContainerResult.SUCCESS;
		
		// Check if the requester has MOVE permissions to the destination container
		if (!permissions.canMove(requester, container)) {
			Log.w("SWGObject", "No permission 'MOVE' for requestor %s with container %s", requester, this);
			return ContainerResult.NO_PERMISSION;
		}
		
		// Check if object can fit into container or slots
		int arrangementId = container.getArrangementId(this);
		if (arrangementId == -1) {
			if (container.getMaxContainerSize() <= container.getContainedObjects().size() && container.getMaxContainerSize() > 0) {
				Log.w("SWGObject", "Unable to add object to container! Container Full. Max Size: %d", container.getMaxContainerSize());
				return ContainerResult.CONTAINER_FULL;
			}
		}
		return ContainerResult.SUCCESS;
	}
	
	protected void handleSlotReplacement(SWGObject oldParent, SWGObject obj, int arrangement) {
		for (String slot : obj.getArrangement().get(arrangement-4)) {
			SWGObject slotObj = getSlottedObject(slot);
			if (slotObj != null)
				slotObj.moveToContainer(oldParent);
		}
	}
	
	public boolean isVisible(SWGObject target) {
		if (target == null)
			return true;
		if (!permissions.canView(target, this))
			return false;
		if (getParent() != null)
			return getParent().isVisible(target);
		return true;
	}
	
	public void addAttribute(String attribute, String value) {
		attributes.put(attribute, value);
	}

	/**
	 * Gets the object that occupies the specified slot
	 * @param slotName
	 * @return The {@link SWGObject} occupying the slot. Returns null if there is nothing in the slot or it doesn't exist.
	 */
	public SWGObject getSlottedObject(String slotName) {
		synchronized (slots) {
			return slots.get(slotName);
		}
	}

	/**
	 * Gets a list of all the objects in the current container. This should only be used for viewing the objects
	 * in the current container.
	 * @return An unmodifiable {@link Collection} of {@link SWGObject}'s in the container
	 */
	public Collection<SWGObject> getContainedObjects() {
		synchronized (containedObjects) {
			return new ArrayList<>(containedObjects);
		}
	}

	public boolean hasSlot(String slotName) {
		synchronized (slots) {
			return slots.containsKey(slotName);
		}
	}
	
	public void setSlot(String name, SWGObject value) {
		synchronized (slots) {
			slots.put(name, value);
		}
	}

	public Map<String, SWGObject> getSlots() {
		synchronized (slots) {
			return new HashMap<>(slots);
		}
	}

	public void setOwner(Player player) {
		if (owner == player)
			return;
		if (owner != null)
			owner.setCreatureObject(null);
		this.owner = player;
		if (player != null && this instanceof CreatureObject)
			player.setCreatureObject((CreatureObject) this);
	}
	
	public void setLocation(Location l) {
		if (l == null)
			return;
		synchronized (location) {
			buildoutLocation.mergeWith(l);
			updateBuildoutLocation();
		}
	}
	
	public void setLocation(double x, double y, double z) {
		synchronized (location) {
			buildoutLocation.mergeLocation(x, y, z);
			updateBuildoutLocation();
		}
	}
	
	public void setStf(String stfFile, String stfKey) {
		this.stringId = new StringId(stfFile, stfKey);
	}
	
	public void setStringId(String stringId) {
		this.stringId = new StringId(stringId);
	}
	
	public void setDetailStf(String stfFile, String stfKey) {
		this.detailStringId = new StringId(stfFile, stfKey);
	}
	
	public void setDetailStringId(String stf) {
		this.detailStringId = new StringId(stf);
	}
	
	public void setTemplate(String template) {
		this.template = template;
		this.crc = CRC.getCrc(template);
	}
	
	public void setName(String name) {
		this.objectName = name;
	}
	
	public void setVolume(int volume) {
		this.volume = volume;
	}
	
	public void setComplexity(float complexity) {
		this.complexity = complexity;
	}
	
	public void setBuildoutArea(BuildoutArea buildoutArea) {
		this.buildoutArea = buildoutArea;
		updateBuildoutLocation();
	}
	
	private void updateBuildoutLocation() {
		synchronized (location) {
			if (buildoutArea != null)
				buildoutArea.adjustLocation(buildoutLocation, location);
			else
				location.mergeWith(buildoutLocation);
		}
	}
	
	public void setBuildoutAreaId(int areaId) {
		this.areaId = areaId;
	}
	
	public void setArrangement(List<List<String>> arrangement) {
		this.arrangement = arrangement;
	}
	
	public Player getOwner() {
		if (owner != null)
			return owner;

		if (getParent() != null)
			return getParent().getOwner();
		
		return null;
	}
	
	public Player getOwnerShallow() {
		return owner;
	}
	
	public SWGObject getParent() {
		return parent;
	}
	
	public SWGObject getSuperParent() {
		SWGObject sParent = parent;
		if (sParent == null)
			return null;
		while (sParent.getParent() != null)
			sParent = sParent.getParent();
		return sParent;
	}
	
	public StringId getStringId() {
		return stringId;
	}
	
	public StringId getDetailStringId() {
		return detailStringId;
	}
	
	public String getTemplate() {
		return template;
	}
	
	public int getCrc() {
		return crc;
	}
	
	public long getObjectId() {
		return objectId;
	}
	
	public Location getLocation() {
		return new Location(location);
	}
	
	public Location getBuildoutLocation() {
		return new Location(buildoutLocation);
	}
	
	public Location getWorldLocation() {
		Location loc = new Location(location);
		SWGObject parent = getParent();
		while (parent != null) {
			Location l = parent.location; // Have to access privately to avoid copies
			loc.translateLocation(l);
			loc.setTerrain(l.getTerrain());
			parent = parent.getParent();
		}
		return loc;
	}
	
	public double getX() {
		return location.getX();
	}
	
	public double getY() {
		return location.getY();
	}
	
	public double getZ() {
		return location.getZ();
	}
	
	public Terrain getTerrain() {
		return location.getTerrain();
	}
	
	public String getName() {
		return objectName;
	}
	
	public int getVolume() {
		return volume;
	}
	
	public float getComplexity() {
		return complexity;
	}
	
	public BuildoutArea getBuildoutArea() {
		return buildoutArea;
	}
	
	public int getBuildoutAreaId() {
		return areaId;
	}
	
	public Object getDataAttribute(ObjectDataAttribute key) {
		return dataAttributes.get(key);
	}

	public void setDataAttribute(ObjectDataAttribute key, Object value) {
		dataAttributes.put(key, value);
	}
	
	public ObjectAware getAwareness() {
		return awareness;
	}
	
	public List<List<String>> getArrangement() {
		return arrangement;
	}

	public String getAttribute(String attribute) {
		return attributes.get(attribute);
	}
	
	public Map<String, String> getAttributes() {
		return attributes;
	}

	public int getContainerType() {
		return containerType;
	}

	public void setContainerType(int containerType) {
		this.containerType = containerType;
	}

	public int getSlotArrangement() {
		return slotArrangement;
	}

	public void setSlotArrangement(int slotArrangement) {
		this.slotArrangement = slotArrangement;
	}
	
	public int getMaxContainerSize() {
		Object maxContents = dataAttributes.get(ObjectDataAttribute.CONTAINER_VOLUME_LIMIT);
		if (maxContents == null) {
			Log.w("SWGObject", "Volume is null!");
			return 0;
		}
		return (Integer) maxContents;
	}
	
	public int getNextUpdateCount() {
		return updateCounter.getAndIncrement();
	}
	
	public void setClassification(ObjectClassification classification) {
		this.classification = classification;
	}
	
	public ObjectClassification getClassification() {
		return classification;
	}
	
	public GameObjectType getGameObjectType() {
		return gameObjectType;
	}
	
	public void setGameObjectType(GameObjectType gameObjectType) {
		this.gameObjectType = gameObjectType;
	}
	
	public ContainerPermissionsType getContainerPermissions() {
		return permissions;
	}
	
	public void setContainerPermissions(ContainerPermissionsType permissions) {
		this.permissions = permissions;
	}

	public boolean isBuildout() {
		return classification == ObjectClassification.BUILDOUT;
	}
	
	public boolean isSnapshot() {
		return classification == ObjectClassification.SNAPSHOT;
	}
	
	public boolean isGenerated() {
		return classification == ObjectClassification.GENERATED;
	}
	
	public double getLoadRange() {
		double bubble = getPrefLoadRange();
		synchronized (containedObjects) {
			for (SWGObject contained : containedObjects) {
				double x = contained.getX();
				double z = contained.getZ();
				double dist = Math.sqrt(x*x+z*z) + contained.getLoadRange();
				if (dist > bubble)
					bubble = dist;
			}
		}
		return bubble;
	}
	
	public double getChildRadius() {
		return getLoadRange();
	}
	
	public double getPrefLoadRange() {
		return prefLoadRange;
	}
	
	public void setPrefLoadRange(double range) {
		this.prefLoadRange = range;
	}
	
	/**
	 * Gets the arrangementId for the {@link SWGObject} for the current instance
	 * @param object
	 * @return Arrangement ID for the object
	 */
	public int getArrangementId(SWGObject object) {
		synchronized (slots) {
			if (slots.size() == 0 || object.getArrangement() == null)
				return -1;
		}

		int arrangementId = 4;
		int filledId = -1;

		for (List<String> arrangementList : object.getArrangement()) {
			boolean passesCompletely = true;
			boolean isValid = true;
			for (String slot : arrangementList) {
				if (!hasSlot(slot)) {
					isValid = false;
					break;
				}  else if (getSlottedObject(slot) != null) {
					passesCompletely = false;
				}
			}
			if (isValid && passesCompletely)
				return arrangementId;
			else if (isValid)
				filledId = arrangementId;

			arrangementId++;
		}
		return (filledId != -1) ? filledId : -1;
	}
	
	private final void sendSceneCreateObject(Player target) {
		SceneCreateObjectByCrc create = new SceneCreateObjectByCrc();
		create.setObjectId(objectId);
		create.setLocation(buildoutLocation);
		create.setObjectCrc(crc);
		target.sendPacket(create);
		if (parent != null)
			target.sendPacket(new UpdateContainmentMessage(objectId, parent.getObjectId(), slotArrangement));
	}
	
	private final void sendSceneDestroyObject(Player target) {
		SceneDestroyObject destroy = new SceneDestroyObject();
		destroy.setObjectId(objectId);
		target.sendPacket(destroy);
	}
	
	public void createObject(SWGObject target) {
		createObject(target, false);
	}
	
	public void createObject(SWGObject target, boolean ignoreSnapshotChecks) {
		if (target == null)
			return;
		Set<Player> observers = target.getAwareness().getChildObservers();
		if (target.getOwnerShallow() != null)
			observers.add(target.getOwnerShallow());
		for (Player observer : observers) {
			createObject(observer, ignoreSnapshotChecks);
		}
	}
	
	public void createObject(Player target, boolean ignoreSnapshotChecks) {
		if (!isVisible(target.getCreatureObject())) {
			return;
		}
		
		sendSceneCreateObject(target);
		sendBaselines(target);
		createChildrenObjects(target, ignoreSnapshotChecks);
		sendFinalBaselinePackets(target);
		target.sendPacket(new SceneEndBaselines(getObjectId()));
	}
	
	public void destroyObject(SWGObject target) {
		if (isSnapshot() || target == null)
			return;
		Set<Player> observers = target.getAwareness().getChildObservers();
		if (target.getOwnerShallow() != null)
			observers.add(target.getOwnerShallow());
		for (Player observer : observers) {
			destroyObject(observer);
		}
	}
	
	public void destroyObject(Player target) {
		sendSceneDestroyObject(target);
	}
	
	public void addObjectAware(SWGObject aware) {
		if (awareness.addObjectAware(aware.getAwareness())) {
			createObject(aware);
			aware.createObject(this);
		}
	}
	
	public void removeObjectAware(SWGObject aware) {
		if (awareness.removeObjectAware(aware.getAwareness())) {
			destroyObject(aware);
			aware.destroyObject(this);
		}
	}
	
	public boolean isObjectAware(SWGObject aware) {
		return awareness.isObjectAware(aware);
	}
	
	public void clearObjectsAware() {
		Set<SWGObject> aware = awareness.getObjectsAware();
		awareness.clearObjectsAware();
		for (SWGObject obj : aware) {
			destroyObject(obj);
			obj.destroyObject(this);
		}
	}
	
	public void resetAwareness() {
		awareness.clearObjectsAware();
	}
	
	public Set <SWGObject> getObjectsAware() {
		return awareness.getObjectsAware();
	}
	
	public void addCustomAware(SWGObject aware) {
		if (awareness.addCustomAware(aware.getAwareness())) {
			createObject(aware);
			aware.createObject(this);
		}
	}
	
	public void removeCustomAware(SWGObject aware) {
		if (awareness.removeCustomAware(aware.getAwareness())) {
			destroyObject(aware);
			aware.destroyObject(this);
		}
	}
	
	public boolean isCustomAware(SWGObject aware) {
		return awareness.isCustomAware(aware);
	}
	
	public void clearCustomAware(boolean sendUpdates) {
		Set<SWGObject> aware = sendUpdates ? awareness.getCustomAware() : null;
		awareness.clearCustomAware();
		if (!sendUpdates)
			return;
		for (SWGObject obj : aware) {
			destroyObject(obj);
			obj.destroyObject(this);
		}
	}
	
	public Set<Player> getObserversAndParent() {
		Set<Player> observers = getObservers();
		Player parentOwner = (getParent() != null) ? getParent().getOwner() : null;
		if (parentOwner != null)
			observers.add(parentOwner);
		return observers;
	}
	
	public Set<Player> getObservers() {
		return getObservers(true);
	}
	
	private Set<Player> getObservers(boolean useAware) {
		if (!useAware)
			return awareness.getChildObservers();
		return awareness.getObservers();
	}
	
	public void sendObserversAndSelf(Packet ... packets) {
		sendSelf(packets);
		sendObservers(packets);
	}
	
	public void sendObservers(Packet ... packets) {
		for (Player observer : getObservers()) {
			observer.sendPacket(packets);
		}
	}
	
	public void sendSelf(Packet ... packets) {
		Player owner = getOwner();
		if (owner != null)
			owner.sendPacket(packets);
	}
	
	protected void sendBaselines(Player target) {
		target.sendPacket(createBaseline3(target));
		target.sendPacket(createBaseline6(target));
		
		if (getOwner() == target) {
			target.sendPacket(createBaseline8(target));
			target.sendPacket(createBaseline9(target));
		}
	}
	
	protected void createChildrenObjects(Player target) {
		createChildrenObjects(target, false);
	}
	
	private void createChildrenObjects(Player target, boolean ignoreSnapshotChecks) {
		synchronized (slots) {
			if (slots.size() == 0 && containedObjects.size() == 0)
				return;
		}

		// First create the objects in the slots
		synchronized (slots) {
			for (SWGObject slotObject : slots.values()) {
				if (slotObject != null) {
					slotObject.createObject(target, ignoreSnapshotChecks);
				}
			}
		}
		
		// Now create the contained objects
		synchronized (containedObjects) {
			for (SWGObject containedObject : containedObjects) {
				if (containedObject != null) {
					if (containedObject instanceof CreatureObject && ((CreatureObject) containedObject).isLoggedOutPlayer())
						continue; // If it's a player, but that's logged out
					containedObject.createObject(target, ignoreSnapshotChecks);
				}
			}
		}
	}
	
	protected void sendFinalBaselinePackets(Player target) {
		
	}
	
	public boolean isInBuilding() {
		SWGObject parent = getParent();
		if (parent == null)
			return false;
		parent = parent.getParent();
		return parent != null && parent instanceof BuildingObject;
	}

	@Override
	public String toString() {
		return String.format("SWGObject[%d '%s' %s]", objectId, objectName, template.replace("object/", ""));
	}
	
	@Override
	public int compareTo(SWGObject obj) {
		return Long.compare(objectId, obj.getObjectId());
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SWGObject))
			return false;
		return getObjectId() == ((SWGObject)o).getObjectId();
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(getObjectId());
	}
	
	protected void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb);
		bb.addFloat(complexity); // 0
		bb.addObject(stringId); // 1
		bb.addUnicode(objectName); // custom name -- 2
		bb.addInt(volume); // 3

		bb.incrementOperandCount(4);
	}
	
	protected void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb);
		bb.addInt(CoreManager.getGalaxyId()); // 0
		bb.addObject(detailStringId); // 1
		
		bb.incrementOperandCount(2);
	}
	
	protected void parseBaseline3(NetBuffer buffer) {
		super.parseBaseline3(buffer);
		complexity = buffer.getFloat();
		stringId = buffer.getEncodable(StringId.class);
		objectName = buffer.getUnicode();
		volume = buffer.getInt();
	}
	
	protected void parseBaseline6(NetBuffer buffer) {
		super.parseBaseline6(buffer);
		buffer.getInt(); // Immutable ... can't change the galaxy id
		detailStringId = buffer.getEncodable(StringId.class);
	}
	
	/* Baseline send permissions based on packet observations:
	 * 
	 * Baseline1 sent if you have full permissions to the object.
	 * Baseline4 sent if you have full permissions to the object.
	 * 
	 * Baseline8 sent if you have some permissions to the object.
	 * Baseline9 sent if you have some permissions to the object.
	 * 
	 * Baseline3 always sent.
	 * Baseline6 always sent.
	 * 
	 * Baseline7 sent on using the object.
	 * 
	 * Only sent if they are defined (can still be empty if defined).
	 */
	
	@Override
	public void save(NetBufferStream stream) {
		stream.addByte(3);
		location.save(stream);
		buildoutLocation.save(stream);
		stream.addBoolean(parent != null && parent.getClassification() != ObjectClassification.GENERATED);
		if (parent != null && parent.getClassification() != ObjectClassification.GENERATED)
			SWGObjectFactory.save(ObjectCreator.createObjectFromTemplate(parent.getObjectId(), parent.getTemplate()), stream);
		stream.addAscii(permissions.name());
		stream.addAscii(classification.name());
		stream.addUnicode(objectName);
		stringId.save(stream);
		detailStringId.save(stream);
		stream.addFloat(complexity);
		stream.addFloat((float) prefLoadRange);
		synchronized (attributes) {
			stream.addMap(attributes, (e) -> {
				stream.addAscii(e.getKey());
				stream.addAscii(e.getValue());
			});
		}
		Set<SWGObject> contained;
		synchronized (containedObjects) {
			contained = new HashSet<>(containedObjects);
		}
		synchronized (slots) {
			contained.addAll(slots.values());
			contained.remove(null);
		}
		stream.addList(contained, (c) -> SWGObjectFactory.save(c, stream));
	}
	
	public void read(NetBufferStream stream) {
		switch(stream.getByte()) {
			case 3:
				readVersion3(stream);
				break;
			case 2:
				readVersion2(stream);
				break;
			case 1:
				readVersion1(stream);
				break;
			case 0:
				readVersion0(stream);
				break;
		}
	}
	
	private void readVersion3(NetBufferStream stream) {
		location.read(stream);
		buildoutLocation.read(stream);
		if (stream.getBoolean())
			parent = SWGObjectFactory.create(stream);
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		classification = ObjectClassification.valueOf(stream.getAscii());
		objectName = stream.getUnicode();
		stringId.read(stream);
		detailStringId.read(stream);
		complexity = stream.getFloat();
		prefLoadRange = stream.getFloat();
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> SWGObjectFactory.create(stream).moveToContainer(this));
	}
	
	private void readVersion2(NetBufferStream stream) {
		buildoutLocation.read(stream);
		location.mergeWith(buildoutLocation);
		if (stream.getBoolean())
			parent = SWGObjectFactory.create(stream);
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		classification = ObjectClassification.valueOf(stream.getAscii());
		objectName = stream.getUnicode();
		stringId.read(stream);
		detailStringId.read(stream);
		complexity = stream.getFloat();
		prefLoadRange = stream.getFloat();
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> SWGObjectFactory.create(stream).moveToContainer(this));
	}
	
	private void readVersion1(NetBufferStream stream) {
		location.read(stream);
		if (stream.getBoolean())
			parent = SWGObjectFactory.create(stream);
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		classification = ObjectClassification.valueOf(stream.getAscii());
		objectName = stream.getUnicode();
		complexity = stream.getFloat();
		prefLoadRange = stream.getFloat();
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> SWGObjectFactory.create(stream).moveToContainer(this));
	}
	
	private void readVersion0(NetBufferStream stream) {
		location.read(stream);
		if (stream.getBoolean())
			parent = SWGObjectFactory.create(stream);
		permissions = ContainerPermissionsType.valueOf(stream.getAscii());
		classification = ObjectClassification.valueOf(stream.getAscii());
		objectName = stream.getUnicode();
		// Ignore the saved volume - this is now set automagically in addObject() and removeObject()
		stream.getInt();
		complexity = stream.getFloat();
		prefLoadRange = stream.getFloat();
		stream.getList((i) -> attributes.put(stream.getAscii(), stream.getAscii()));
		stream.getList((i) -> SWGObjectFactory.create(stream).moveToContainer(this));
	}
	
	public enum ObjectClassification {
		GENERATED,
		BUILDOUT,
		SNAPSHOT
	}
}
