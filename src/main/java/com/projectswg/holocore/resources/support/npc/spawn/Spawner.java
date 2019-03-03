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
package com.projectswg.holocore.resources.support.npc.spawn;

import com.projectswg.common.data.encodables.tangible.PvpFaction;
import com.projectswg.common.data.location.Location;
import com.projectswg.holocore.resources.support.data.server_info.loader.DataLoader;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcLoader.CreatureNpcInfo;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcLoader.DroidNpcInfo;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcLoader.HumanoidNpcInfo;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcLoader.NpcInfo;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcPatrolRouteLoader.PatrolRouteWaypoint;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcPatrolRouteLoader.PatrolType;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcStaticSpawnLoader.PatrolFormation;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcStaticSpawnLoader.SpawnerFlag;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcStaticSpawnLoader.StaticSpawnInfo;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.building.BuildingObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureDifficulty;
import com.projectswg.holocore.resources.support.objects.swg.custom.AIBehavior;
import com.projectswg.holocore.services.support.objects.ObjectStorageService.BuildingLookup;
import me.joshlarson.jlcommon.log.Log;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

public final class Spawner {
	
	private final StaticSpawnInfo spawn;
	private final NpcInfo npc;
	
	private final Location location;
	private final List<ResolvedPatrolWaypoint> waypoints;
	private final SWGObject egg;
	private final Random random;
	
	public Spawner(@NotNull StaticSpawnInfo spawn, @NotNull SWGObject egg) {
		this.spawn = Objects.requireNonNull(spawn, "spawn");
		this.npc = DataLoader.npcs().getNpc(spawn.getNpcId());
		Objects.requireNonNull(npc, "Invalid npc id: " + spawn.getNpcId());
		
		this.location = Location.builder()
					.setTerrain(spawn.getTerrain())
					.setPosition(spawn.getX(), spawn.getY(), spawn.getZ())
					.setHeading(spawn.getHeading())
					.build();
		if (spawn.getPatrolId().isEmpty() || spawn.getPatrolId().equals("0")) { // TODO: Replace the latter with empty string
			this.waypoints = null;
		} else {
			List<PatrolRouteWaypoint> waypoints = Objects.requireNonNull(DataLoader.npcPatrolRoutes().getPatrolRoute(spawn.getPatrolId()), "Invalid patrol route: " + spawn.getPatrolId());
			this.waypoints = waypoints.stream().map(ResolvedPatrolWaypoint::new).collect(Collectors.toList());
		}
		this.egg = Objects.requireNonNull(egg, "egg");
		this.random = new Random();
	}
	
	/**
	 * Calculates a random number between {@code minRespawnDelay} and
	 * {@code maxRespawnDelay}
	 * @return a random number between {@code minRespawnDelay} and
	 * {@code maxRespawnDelay}
	 */
	public int getRespawnDelay() {
		return random.nextInt((getMaxSpawnTime() - getMinSpawnTime()) + 1) + getMinSpawnTime();
	}
	
	/**
	 * @return a random IFF template
	 */
	public String getRandomIffTemplate() {
		return getRandom(getIffs());
	}
	
	public String getRandomPrimaryWeapon() {
		return getRandom(getPrimaryWeapons());
	}
	
	public String getRandomSecondaryWeapon() {
		return getRandom(getSecondaryWeapons());
	}
	
	public List<ResolvedPatrolWaypoint> getPatrolRoute() {
		return waypoints;
	}
	
	public SWGObject getEgg() {
		return egg;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public String getId() {
		return spawn.getId();
	}
	
	public String getSpawnerType() {
		return spawn.getSpawnerType();
	}
	
	public String getNpcId() {
		return spawn.getNpcId();
	}
	
	public String getBuildingId() {
		return spawn.getBuildingId();
	}
	
	public String getMood() {
		return spawn.getMood();
	}
	
	public AIBehavior getBehavior() {
		return spawn.getBehavior();
	}
	
	public String getPatrolId() {
		return spawn.getPatrolId();
	}
	
	public PatrolFormation getPatrolFormation() {
		return spawn.getPatrolFormation();
	}
	
	public int getLoiterRadius() {
		return spawn.getLoiterRadius();
	}
	
	public int getMinSpawnTime() {
		return spawn.getMinSpawnTime();
	}
	
	public int getMaxSpawnTime() {
		return spawn.getMaxSpawnTime();
	}
	
	public int getAmount() {
		return spawn.getAmount();
	}
	
	public SpawnerFlag getSpawnerFlag() {
		return spawn.getSpawnerFlag();
	}
	
	public CreatureDifficulty getDifficulty() {
		return spawn.getDifficulty();
	}
	
	public int getMinLevel() {
		return spawn.getMinLevel();
	}
	
	public int getMaxLevel() {
		return spawn.getMaxLevel();
	}
	
	public String getName() {
		return npc.getName();
	}
	
	public String getStfName() {
		return npc.getStfName();
	}
	
	public List<String> getIffs() {
		return npc.getIffs();
	}
	
	public PvpFaction getFaction() {
		return npc.getFaction();
	}
	
	public boolean isSpecForce() {
		return npc.isSpecForce();
	}
	
	public double getAttackSpeed() {
		return npc.getAttackSpeed();
	}
	
	public double getMovementSpeed() {
		return npc.getMovementSpeed();
	}
	
	public double getScaleMin() {
		return npc.getScaleMin();
	}
	
	public double getScaleMax() {
		return npc.getScaleMax();
	}
	
	public int getHue() {
		return npc.getHue();
	}
	
	public List<String> getPrimaryWeapons() {
		return npc.getPrimaryWeapons().stream().map(DataLoader.npcWeapons()::getWeapons).filter(Objects::nonNull).flatMap(List::stream).collect(Collectors.toList());
	}
	
	public List<String> getSecondaryWeapons() {
		return npc.getSecondaryWeapons().stream().map(DataLoader.npcWeapons()::getWeapons).filter(Objects::nonNull).flatMap(List::stream).collect(Collectors.toList());
	}
	
	public int getAggressiveRadius() {
		return npc.getAggressiveRadius();
	}
	
	public int getAssistRadius() {
		return npc.getAssistRadius();
	}
	
	public boolean isDeathblow() {
		return npc.isDeathblow();
	}
	
	public String getLootTable1() {
		return npc.getLootTable1();
	}
	
	public String getLootTable2() {
		return npc.getLootTable2();
	}
	
	public String getLootTable3() {
		return npc.getLootTable3();
	}
	
	public int getLootTable1Chance() {
		return npc.getLootTable1Chance();
	}
	
	public int getLootTable2Chance() {
		return npc.getLootTable2Chance();
	}
	
	public int getLootTable3Chance() {
		return npc.getLootTable3Chance();
	}
	
	public HumanoidNpcInfo getHumanoidInfo() {
		return npc.getHumanoidInfo();
	}
	
	public DroidNpcInfo getDroidInfo() {
		return npc.getDroidInfo();
	}
	
	public CreatureNpcInfo getCreatureInfo() {
		return npc.getCreatureInfo();
	}
	
	private <T> T getRandom(List<T> list) {
		return list.get(random.nextInt(list.size()));
	}
	
	public static class ResolvedPatrolWaypoint {
		
		private final PatrolRouteWaypoint waypoint;
		private final SWGObject parent;
		private final Location location;
		
		private ResolvedPatrolWaypoint(PatrolRouteWaypoint waypoint) {
			this.waypoint = waypoint;
			this.parent = getPatrolWaypointParent(waypoint);
			this.location = getPatrolWaypointLocation(waypoint);
		}
		
		public SWGObject getParent() {
			return parent;
		}
		
		public Location getLocation() {
			return location;
		}
		
		public double getDelay() {
			return waypoint.getDelay();
		}
		
		public PatrolType getPatrolType() {
			return waypoint.getPatrolType();
		}
		
		public String getGroupId() {
			return waypoint.getGroupId();
		}
		
		public String getPatrolId() {
			return waypoint.getPatrolId();
		}
		
		private static Location getPatrolWaypointLocation(PatrolRouteWaypoint waypoint) {
			return Location.builder()
					.setTerrain(waypoint.getTerrain())
					.setX(waypoint.getX())
					.setY(waypoint.getY())
					.setZ(waypoint.getZ()).build();
		}
		
		private static SWGObject getPatrolWaypointParent(PatrolRouteWaypoint waypoint) {
			if (waypoint.getBuildingId().isEmpty() || waypoint.getBuildingId().endsWith("_world"))
				return null;
			
			BuildingObject building = BuildingLookup.getBuildingByTag(waypoint.getBuildingId());
			if (building == null) {
				Log.w("PatrolRouteWaypoint: Invalid building id for patrol id: %d and group id: %d", waypoint.getPatrolId(), waypoint.getGroupId());
				return null;
			}
			
			SWGObject cell = building.getCellByNumber(waypoint.getCellId());
			if (cell == null) {
				Log.w("PatrolRouteWaypoint: Invalid cell [%d] for building: %s, patrol id: %d and group id: %d", waypoint.getCellId(), waypoint.getBuildingId(), waypoint.getPatrolId(), waypoint.getGroupId());
				return null;
			}
			
			return cell;
		}
		
	}
	
}
