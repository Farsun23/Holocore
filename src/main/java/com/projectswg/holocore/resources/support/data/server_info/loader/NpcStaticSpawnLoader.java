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
package com.projectswg.holocore.resources.support.data.server_info.loader;

import com.projectswg.holocore.resources.support.data.server_info.SdbLoader;
import com.projectswg.holocore.resources.support.data.server_info.SdbLoader.SdbResultSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class NpcStaticSpawnLoader extends DataLoader {
	
	private final List<StaticSpawnInfo> spawns;
	
	NpcStaticSpawnLoader() {
		this.spawns = new ArrayList<>();
	}
	
	public List<StaticSpawnInfo> getSpawns() {
		return spawns;
	}
	
	public void iterate(Consumer<StaticSpawnInfo> spawn) {
		spawns.forEach(spawn);
	}
	
	@Override
	public void load() throws IOException {
		try (SdbResultSet set = SdbLoader.load(new File("serverdata/spawn/static.msdb"))) {
			while (set.next()) {
				spawns.add(new StaticSpawnInfo(set));
			}
		}
	}
	
	public static class StaticSpawnInfo {
		
		private final int id;
		private final double x;
		private final double y;
		private final double z;
		private final int heading;
		private final int cellId;
		private final String spawnerType;
		private final String npcId;
		private final String buildingId;
		private final String mood;
		private final String behavior;
		private final int patrolId;
		private final PatrolFormation patrolFormation;
		private final int loiterRadius;
		private final int minSpawnTime;
		private final int maxSpawnTime;
		private final int amount;
		
		private StaticSpawnInfo(SdbResultSet set) {
			this.id = (int) set.getInt("spawn_id");
			this.x = set.getReal("x");
			this.y = set.getReal("y");
			this.z = set.getReal("z");
			this.heading = (int) set.getInt("heading");
			this.cellId = (int) set.getInt("cell_id");
			this.spawnerType = set.getText("spawner_type").intern();
			this.npcId = set.getText("npc_id").intern();
			this.buildingId = set.getText("building_id").intern();
			this.mood = set.getText("mood").intern();
			this.behavior = set.getText("behaviour").intern();
			this.patrolId = (int) set.getInt("patrol_id");
			this.patrolFormation = parsePatrolFormation(set.getText("patrol_formation"));
			this.loiterRadius = (int) set.getInt("loiter_radius");
			this.minSpawnTime = (int) set.getInt("min_spawn_time");
			this.maxSpawnTime = (int) set.getInt("max_spawn_time");
			this.amount = (int) set.getInt("amount");
		}
		
		public int getId() {
			return id;
		}
		
		public double getX() {
			return x;
		}
		
		public double getY() {
			return y;
		}
		
		public double getZ() {
			return z;
		}
		
		public int getHeading() {
			return heading;
		}
		
		public int getCellId() {
			return cellId;
		}
		
		public String getSpawnerType() {
			return spawnerType;
		}
		
		public String getNpcId() {
			return npcId;
		}
		
		public String getBuildingId() {
			return buildingId;
		}
		
		public String getMood() {
			return mood;
		}
		
		public String getBehavior() {
			return behavior;
		}
		
		public int getPatrolId() {
			return patrolId;
		}
		
		public PatrolFormation getPatrolFormation() {
			return patrolFormation;
		}
		
		public int getLoiterRadius() {
			return loiterRadius;
		}
		
		public int getMinSpawnTime() {
			return minSpawnTime;
		}
		
		public int getMaxSpawnTime() {
			return maxSpawnTime;
		}
		
		public int getAmount() {
			return amount;
		}
		
		private static PatrolFormation parsePatrolFormation(String str) {
			switch (str.toUpperCase(Locale.US)) {
				case "column":
					return PatrolFormation.COLUMN;
				case "wedge":
					return PatrolFormation.WEDGE;
				case "line":
					return PatrolFormation.LINE;
				case "box":
					return PatrolFormation.BOX;
				case "":
				default:
					return PatrolFormation.NONE;
			}
		}
		
	}
	
	public enum PatrolFormation {
		NONE,
		COLUMN,
		WEDGE,
		LINE,
		BOX
	}
	
}
