/************************************************************************************
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
package com.projectswg.holocore.test_resources;

import com.projectswg.common.data.encodables.tangible.Race;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.common.data.swgfile.visitors.ObjectData;
import com.projectswg.common.data.swgfile.visitors.ObjectData.ObjectDataAttribute;
import com.projectswg.common.data.swgfile.visitors.SlotArrangementData;
import com.projectswg.common.data.swgfile.visitors.SlotDescriptorData;
import com.projectswg.common.network.packets.SWGPacket;
import com.projectswg.holocore.intents.object.ObjectCreatedIntent;
import com.projectswg.holocore.resources.containers.ContainerPermissionsType;
import com.projectswg.holocore.resources.objects.GameObjectType;
import com.projectswg.holocore.resources.objects.SWGObject;
import com.projectswg.holocore.resources.objects.creature.CreatureObject;
import com.projectswg.holocore.resources.objects.player.PlayerObject;
import com.projectswg.holocore.resources.player.Player;
import com.projectswg.holocore.resources.player.PlayerState;
import com.projectswg.holocore.services.objects.ObjectCreator;

import java.util.Map.Entry;

public class GenericCreatureObject extends CreatureObject {
	
	private Player player;
	
	public GenericCreatureObject(long objectId) {
		super(objectId);
		player = new Player() {
			@Override
			public void sendPacket(SWGPacket ... packets) {
				// Nah
			}
		};
		player.setPlayerState(PlayerState.ZONED_IN);
		setHasOwner(true);
		setSlot("ghost", new PlayerObject(-getObjectId()));
	}
	
	public void setHasOwner(boolean hasOwner) {
		if (hasOwner) {
			player.setCreatureObject(this);
		} else {
			player.setCreatureObject(null);
		}
	}
	
	public void setupAsCharacter() {
		handlePostCreation();
		createInventoryObject("object/tangible/inventory/shared_character_inventory.iff");
		createInventoryObject("object/tangible/datapad/shared_character_datapad.iff");
		createInventoryObject("object/tangible/inventory/shared_appearance_inventory.iff");
		createInventoryObject("object/tangible/bank/shared_character_bank.iff");
		createInventoryObject("object/tangible/mission_bag/shared_mission_bag.iff");
	}
	
	private void createTangible(ContainerPermissionsType type, String template) {
		SWGObject obj = ObjectCreator.createObjectFromTemplate(template);
		obj.setContainerPermissions(type);
		obj.moveToContainer(this);
		ObjectCreatedIntent.broadcast(obj);
	}
	
	/** Creates an object with inventory-level world visibility (only the owner) */
	private void createInventoryObject(String template) {
		createTangible(ContainerPermissionsType.INVENTORY, template);
	}
	
	private void handlePostCreation() {
		ObjectData attributes = (ObjectData) ClientFactory.getInfoFromFile(Race.HUMAN_MALE.getFilename());
		addObjectAttributes(attributes);
		createObjectSlots();
		Object got = getDataAttribute(ObjectDataAttribute.GAME_OBJECT_TYPE);
		if (got != null)
			setGameObjectType(GameObjectType.getTypeFromId((Integer) got));
	}

	private void addObjectAttributes(ObjectData attributes) {
		if (attributes == null)
			return;

		for (Entry<ObjectDataAttribute, Object> e : attributes.getAttributes().entrySet()) {
			setObjectAttribute(e.getKey(), e.getValue());
		}
	}

	private void setObjectAttribute(ObjectDataAttribute key, Object value) {
		setDataAttribute(key, value);
		switch (key) {
			case OBJECT_NAME: setStringId(value.toString()); break;
			case DETAILED_DESCRIPTION: setDetailStringId(value.toString()); break;
			case CONTAINER_TYPE: setContainerType((Integer) value); break;
			default: break;
		}
	}

	private void createObjectSlots() {
		if (getDataAttribute(ObjectDataAttribute.SLOT_DESCRIPTOR_FILENAME) != null) {
			// These are the slots that the object *HAS*
			SlotDescriptorData descriptor = (SlotDescriptorData) ClientFactory.getInfoFromFile((String) getDataAttribute(ObjectDataAttribute.SLOT_DESCRIPTOR_FILENAME));
			if (descriptor == null)
				return;

			for (String slotName : descriptor.getSlots()) {
				setSlot(slotName, null);
			}
		}
		
		if (getDataAttribute(ObjectDataAttribute.ARRANGEMENT_DESCRIPTOR_FILENAME) != null) {
			// This is what slots the created object is able to go into/use
			SlotArrangementData arrangementData = (SlotArrangementData) ClientFactory.getInfoFromFile((String) getDataAttribute(ObjectDataAttribute.ARRANGEMENT_DESCRIPTOR_FILENAME));
			if (arrangementData == null)
				return;

			setArrangement(arrangementData.getArrangement());
		}
	}
	
}
