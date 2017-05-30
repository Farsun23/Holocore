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
package services.crafting.resource.galactic;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.debug.Log;

import resources.server_info.StandardLog;
import services.crafting.resource.galactic.storage.GalacticResourceContainer;
import services.crafting.resource.raw.RawResource;

public class GalacticResourceSpawner {
	
	private static final Terrain [] BASE_PLANETS = new Terrain[] {
		Terrain.CORELLIA,	Terrain.DANTOOINE,		Terrain.DATHOMIR,
		Terrain.ENDOR,		Terrain.KASHYYYK_MAIN,	Terrain.LOK,
		Terrain.NABOO,		Terrain.RORI,			Terrain.TALUS,
		Terrain.TATOOINE,	Terrain.YAVIN4
	};
	
	private final Random random;
	private final AtomicLong resourceIdMax;
	
	public GalacticResourceSpawner() {
		this.random = new Random();
		this.resourceIdMax = new AtomicLong(0);
	}
	
	public void initialize() {
		loadOldResources();
		updateAllResources();
	}
	
	public void terminate() {
		saveResources();
	}
	
	public void updateAllResources() {
		updateSpawns();
		updateUnusedPools();
	}
	
	private void loadOldResources() {
		loadOldGalacticResources();
		loadOldGalacticResourceSpawns();
	}
	
	private void loadOldGalacticResources() {
		GalacticResourceLoader loader = new GalacticResourceLoader();
		long startTime = StandardLog.onStartLoad("galactic resources");
		List<GalacticResource> resources = loader.loadResources();
		for (GalacticResource resource : resources) {
			GalacticResourceContainer.getContainer().addGalacticResource(resource);
			if (resource.getId() > resourceIdMax.get())
				resourceIdMax.set(resource.getId());
		}
		StandardLog.onEndLoad(resources.size(), "galactic resources", startTime);
	}
	
	private void loadOldGalacticResourceSpawns() {
		GalacticResourceLoader loader = new GalacticResourceLoader();
		long startTime = StandardLog.onStartLoad("galactic spawns");
		List<GalacticResourceSpawn> spawns = loader.loadSpawns();
		for (GalacticResourceSpawn spawn: spawns) {
			if (!spawn.isExpired())
				GalacticResourceContainer.getContainer().addResourceSpawn(spawn);
			else
				Log.v("Removing expired resource spawn %s for resource %s", spawn, GalacticResourceContainer.getContainer().getGalacticResource(spawn.getResourceId()));
		}
		StandardLog.onEndLoad(spawns.size(), "galactic spawns", startTime);
	}
	
	private void saveResources() {
		GalacticResourceLoader loader = new GalacticResourceLoader();
		loader.saveResources(GalacticResourceContainer.getContainer().getAllResources());
		loader.saveSpawns(GalacticResourceContainer.getContainer().getAllResourceSpawns());
	}
	
	private void updateUnusedPools() {
		List<RawResource> rawResources = GalacticResourceContainer.getContainer().getRawResources();
		for (RawResource raw : rawResources) {
			if (raw.getMaxPools() == 0)
				continue;
			updateUnusedResourcePool(raw);
		}
	}
	
	private void updateUnusedResourcePool(RawResource raw) {
		int spawned = GalacticResourceContainer.getContainer().getSpawnedGalacticResources(raw);
		int minTypes = raw.getMinTypes();
		int maxTypes = raw.getMaxTypes();
		if (spawned > minTypes)
			return; // Only respawn once total number spawned in goes below minTypes
		int targetTypes = random.nextInt(maxTypes - minTypes + 1) + minTypes;
		for (int i = spawned; i < targetTypes; i++) {
			createNewResourceWithSpawns(raw);
		}
	}
	
	private void createNewResourceWithSpawns(RawResource raw) {
		int targetSpawns = random.nextInt(raw.getMaxPools() - raw.getMinPools() + 1) + raw.getMinPools();
		GalacticResource resource = createNewResource(raw);
		Log.i("Created new resource '%s' with ID %d of type %s with %d spawns per planet", resource.getName(), resource.getId(), raw.getName().getKey(), targetSpawns);
		for (Terrain terrain : BASE_PLANETS) {
			for (int i = 0; i < targetSpawns; i++) {
				createNewSpawn(resource, terrain);
			}
		}
	}
	
	private GalacticResource createNewResource(RawResource raw) {
		long newId = resourceIdMax.incrementAndGet();
		String newName = createRandomName(raw) + " " + newId;
		GalacticResource resource = new GalacticResource(newId, newName, raw.getId());
		GalacticResourceContainer.getContainer().addGalacticResource(resource);
		return resource;
	}
	
	private void createNewSpawn(GalacticResource resource, Terrain terrain) {
		GalacticResourceSpawn spawn = new GalacticResourceSpawn(resource.getId());
		spawn.setRandomValues(terrain);
		GalacticResourceContainer.getContainer().addResourceSpawn(spawn);
	}
	
	private String createRandomName(RawResource raw) {
		return raw.getName().getKey();
	}
	
	private void updateSpawns() {
		List<GalacticResource> resources = GalacticResourceContainer.getContainer().getSpawnedResources();
		for (GalacticResource resource : resources) {
			updateResourceSpawns(resource);
		}
	}
	
	private void updateResourceSpawns(GalacticResource resource) {
		for (Terrain terrain : BASE_PLANETS) {
			updateResourceTerrainSpawns(resource, terrain);
		}
	}
	
	private void updateResourceTerrainSpawns(GalacticResource resource, Terrain terrain) {
		List<GalacticResourceSpawn> spawns = GalacticResourceContainer.getContainer().getTerrainResourceSpawns(resource, terrain);
		// Remove expired spawns
		List<GalacticResourceSpawn> expired = spawns.stream().filter(s -> s.isExpired()).collect(Collectors.toList());
		for (GalacticResourceSpawn spawn : expired) {
			GalacticResourceContainer.getContainer().removeResourceSpawn(spawn);
		}
	}
	
}
