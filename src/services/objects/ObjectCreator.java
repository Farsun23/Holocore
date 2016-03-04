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

import java.lang.reflect.InvocationTargetException;
import java.util.Map.Entry;

import resources.client_info.ClientFactory;
import resources.client_info.visitors.ObjectData;
import resources.client_info.visitors.ObjectData.ObjectDataAttribute;
import resources.client_info.visitors.SlotArrangementData;
import resources.client_info.visitors.SlotDescriptorData;
import resources.objects.SWGObject;
import resources.objects.building.BuildingObject;
import resources.objects.cell.CellObject;
import resources.objects.creature.CreatureObject;
import resources.objects.factory.FactoryObject;
import resources.objects.group.GroupObject;
import resources.objects.guild.GuildObject;
import resources.objects.installation.InstallationObject;
import resources.objects.intangible.IntangibleObject;
import resources.objects.manufacture.ManufactureSchematicObject;
import resources.objects.player.PlayerObject;
import resources.objects.resource.ResourceContainerObject;
import resources.objects.ship.ShipObject;
import resources.objects.sound.SoundObject;
import resources.objects.staticobject.StaticObject;
import resources.objects.tangible.TangibleObject;
import resources.objects.waypoint.WaypointObject;
import resources.objects.weapon.WeaponObject;

public final class ObjectCreator {
	
	private static final Object OBJECT_ID_MUTEX = new Object();
	private static long maxObjectId = 1;
	
	public static void updateMaxObjectId(long objectId) {
		synchronized (OBJECT_ID_MUTEX) {
			if (objectId > maxObjectId)
				maxObjectId = objectId;
		}
	}
	
	public static SWGObject createObjectFromTemplate(long objectId, String template) {
		if (!template.startsWith("object/"))
			return null;
		if (!template.endsWith(".iff"))
			return null;
		SWGObject obj = createObjectFromType(objectId, getTemplatePart(template, 1));
		if (obj == null)
			return null;
		obj.setTemplate(template);

		handlePostCreation(obj);
		updateMaxObjectId(objectId);
		return obj;
	}
	
	public static <T extends SWGObject> T createObjectFromTemplate(long objectId, String template, Class <T> c) {
		T obj;
		try {
			obj = c.getConstructor(Integer.TYPE).newInstance(objectId);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			obj = null;
		}
		if (obj == null)
			return null;
		obj.setTemplate(template);

		handlePostCreation(obj);
		updateMaxObjectId(objectId);
		return obj;
	}
	
	public static SWGObject createObjectFromTemplate(String template) {
		long id = 0;
		synchronized (OBJECT_ID_MUTEX) {
			id = maxObjectId++;
		}
		return createObjectFromTemplate(id, template);
	}
	
	public static <T extends SWGObject> T createObjectFromTemplate(String template, Class <T> c) {
		long id = 0;
		synchronized (OBJECT_ID_MUTEX) {
			id = maxObjectId++;
		}
		return createObjectFromTemplate(id, template, c);
	}
	
	private static SWGObject createObjectFromType(long objectId, String type) {
		switch (type) {
			case "building":				return new BuildingObject(objectId);
			case "cell":					return new CellObject(objectId);
			case "creature":				return new CreatureObject(objectId);
			case "factory":					return new FactoryObject(objectId);
			case "group":					return new GroupObject(objectId);
			case "guild":					return new GuildObject(objectId);
			case "installation":			return new InstallationObject(objectId);
			case "intangible":				return new IntangibleObject(objectId);
			case "manufacture_schematic":	return new ManufactureSchematicObject(objectId);
			case "mobile":					return new CreatureObject(objectId);
			case "player":					return new PlayerObject(objectId);
			case "resource_container":		return new ResourceContainerObject(objectId);
			case "ship":					return new ShipObject(objectId);
			case "soundobject":				return new SoundObject(objectId);
			case "static":					return new StaticObject(objectId);
			case "tangible":				return new TangibleObject(objectId);
			case "waypoint":				return new WaypointObject(objectId);
			case "weapon":					return new WeaponObject(objectId);
			default:						System.err.println("Unknown type: " + type); return null;
		}
	}

	private static void handlePostCreation(SWGObject object) {
		addObjectAttributes(object, object.getTemplate());
		createObjectSlots(object);
	}

	private static void addObjectAttributes(SWGObject obj, String template) {
		ObjectData attributes = (ObjectData) ClientFactory.getInfoFromFile(ClientFactory.formatToSharedFile(template), true);

		if (attributes == null)
			return;

		for (Entry<ObjectDataAttribute, Object> e : attributes.getAttributes().entrySet()) {
			setObjectAttribute(e.getKey(), e.getValue(), obj);
		}
	}

	private static void setObjectAttribute(ObjectDataAttribute key, Object value, SWGObject object) {
		object.setDataAttribute(key, value);
		switch (key) {
			case OBJECT_NAME: object.setStringId(value.toString()); break;
			case DETAILED_DESCRIPTION: object.setDetailStringId(value.toString()); break;
			case CONTAINER_VOLUME_LIMIT: object.setVolume((Integer) value); break;
			case CONTAINER_TYPE: object.setContainerType((Integer) value); break;
			default: break;
		}
	}

	private static void createObjectSlots(SWGObject object) {
		if (object.getDataAttribute(ObjectDataAttribute.SLOT_DESCRIPTOR_FILENAME) != null) {
			// These are the slots that the object *HAS*
			SlotDescriptorData descriptor = (SlotDescriptorData) ClientFactory.getInfoFromFile((String) object.getDataAttribute(ObjectDataAttribute.SLOT_DESCRIPTOR_FILENAME), true);
			if (descriptor == null)
				return;

			for (String slotName : descriptor.getSlots()) {
				object.setSlot(slotName, null);
			}
		}
		
		if (object.getDataAttribute(ObjectDataAttribute.ARRANGEMENT_DESCRIPTOR_FILENAME) != null) {
			// This is what slots the created object is able to go into/use
			SlotArrangementData arrangementData = (SlotArrangementData) ClientFactory.getInfoFromFile((String) object.getDataAttribute(ObjectDataAttribute.ARRANGEMENT_DESCRIPTOR_FILENAME), true);
			if (arrangementData == null)
				return;

			object.setArrangement(arrangementData.getArrangement());
		}
	}

	/*
		Misc helper methods
	 */
	private static String getTemplatePart(String template, int index) {
		int start = 0;
		int end = 0;
		for (int i = 0; i < template.length(); i++) {
			if (template.charAt(i) != '/')
				continue;
			index--;
			if (index == 0)
				start = i+1;
			else if (index == -1) {
				end = i;
				break;
			}
		}
		return template.substring(start, end);
	}
	
}
