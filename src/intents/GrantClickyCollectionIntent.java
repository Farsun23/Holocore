/*******************************************************************************
 * Copyright (c) 2016 /// Project SWG /// www.projectswg.com
 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies.
 * Our goal is to create an emulator which will provide a server for players to
 * continue playing a game similar to the one they used to play. We are basing
 * it on the final publish of the game prior to end-game events.
 *
 * This file is part of Holocore.
 *
 * --------------------------------------------------------------------------------
 *
 * Holocore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Holocore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/
package intents;

import resources.control.Intent;
import resources.objects.SWGObject;
import resources.objects.collections.CollectionItem;
import resources.objects.creature.CreatureObject;

/**
 * Created by Yakattak on 9/8/16.
 */
public class GrantClickyCollectionIntent extends Intent {
	public static final String TYPE = "GrantClickyCollectionIntent";

	private CreatureObject creature;
	private SWGObject inventoryItem;
	private CollectionItem collection;

	public GrantClickyCollectionIntent(CreatureObject creature, SWGObject inventoryItem, CollectionItem collection) {
		super(TYPE);
		this.creature = creature;
		this.inventoryItem = inventoryItem;
		this.collection = collection;
	}

	public CreatureObject getCreature() {
		return creature;
	}

	public SWGObject getInventoryItem() {
		return inventoryItem;
	}

	public CollectionItem getCollection() {
		return collection;
	}
}
