/***********************************************************************************
 * Copyright (c) 2019 /// Project SWG /// www.projectswg.com                       *
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

package com.projectswg.holocore.resources.support.data.server_info.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import com.projectswg.common.data.encodables.mongo.MongoData;
import com.projectswg.holocore.resources.gameplay.crafting.resource.galactic.GalacticResource;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class PswgResourceDatabase implements PswgDatabase {
	
	private MongoCollection<Document> collection;
	
	PswgResourceDatabase() {
		this.collection = null;
	}
	
	@Override
	public void open(MongoCollection<Document> collection) {
		this.collection = collection;
		collection.createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));
	}
	
	public void addResource(@NotNull GalacticResource resource) {
		collection.replaceOne(Filters.eq("id", resource.getId()), MongoData.store(resource).toDocument(), new ReplaceOptions().upsert(true));
	}
	
	public void addResources(@NotNull Collection<GalacticResource> resources) {
		collection.bulkWrite(resources.stream()
				.map(resource -> new ReplaceOneModel<>(
						Filters.eq("id", resource.getId()),
						MongoData.store(resource).toDocument(),
						new ReplaceOptions().upsert(true)
				))
				.collect(toList()),
				new BulkWriteOptions().ordered(false));
	}
	
	@NotNull
	public List<GalacticResource> getResources() {
		return collection.find().map(MongoData::new).map(data -> MongoData.create(data, GalacticResource::new)).into(new ArrayList<>());
	}
	
}
