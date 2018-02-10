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
package resources.server_info.loader;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.debug.Log;

import resources.server_info.SdbLoader;
import resources.server_info.SdbLoader.SdbResultSet;

public class BuildingLoader {
	
	private static final AtomicReference<SoftReference<BuildingLoader>> CACHED_LOADER = new AtomicReference<>(null);
	
	private final Map<String, BuildingLoaderInfo> buildingMap;
	
	private BuildingLoader() {
		this.buildingMap = new HashMap<>();
	}
	
	public BuildingLoaderInfo getBuilding(String buildingName) {
		return buildingMap.get(buildingName);
	}
	
	private void loadFromFile() {
		try (SdbResultSet set = SdbLoader.load(new File("serverdata/building/buildings.sdb"))) {
			while (set.next()) {
				buildingMap.put(set.getText(0), new BuildingLoaderInfo(set));
			}
		} catch (IOException e) {
			Log.e(e);
		}
	}
	
	public static BuildingLoader load() {
		SoftReference<BuildingLoader> ref = CACHED_LOADER.get();
		BuildingLoader loader = (ref == null) ? null : ref.get();
		if (loader == null) {
			loader = new BuildingLoader();
			loader.loadFromFile();
			CACHED_LOADER.set(new SoftReference<>(loader));
		}
		return loader;
	}
	
	public static class BuildingLoaderInfo {
		
		private final String name;
		private final long id;
		private final Terrain terrain;
		
		public BuildingLoaderInfo(SdbResultSet set) {
			this.name = set.getText(0);
			this.id = set.getInt(2);
			this.terrain = Terrain.valueOf(set.getText(1));
		}
		
		public String getName() {
			return name;
		}
		
		public long getId() {
			return id;
		}
		
		public Terrain getTerrain() {
			return terrain;
		}
		
	}
	
}
