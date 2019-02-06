package com.projectswg.holocore.resources.support.data.server_info.loader;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public abstract class DataLoader {
	
	DataLoader() {
		
	}
	
	protected abstract void load() throws IOException;
	
	public static void freeMemory() {
		for (CachedLoader loader : CachedLoader.values()) {
			loader.freeMemory();
		}
	}
	
	public static BuffLoader buffs() {
		return (BuffLoader) CachedLoader.BUFFS.load();
	}
	
	public static ExpertiseLoader expertise() {
		return (ExpertiseLoader) CachedLoader.EXPERTISE.load();
	}
	
	public static ExpertiseTreeLoader expertiseTrees() {
		return (ExpertiseTreeLoader) CachedLoader.EXPERTISE_TREES.load();
	}
	
	public static SkillLoader skills() {
		return (SkillLoader) CachedLoader.SKILLS.load();
	}
	
	public static PlayerLevelLoader playerLevels() {
		return (PlayerLevelLoader) CachedLoader.PLAYER_LEVELS.load();
	}
	
	public static BuildoutLoader buildouts() {
		return BuildoutLoader.load(List.of());
	}
	
	public static BuildoutLoader buildouts(String ... events) {
		return BuildoutLoader.load(List.of(events));
	}
	
	public static BuildoutLoader buildouts(Collection<String> events) {
		return BuildoutLoader.load(events);
	}
	
	public static BuildingCellLoader buildingCells() {
		return (BuildingCellLoader) CachedLoader.BUILDING_CELLS.load();
	}
	
	public static NpcLoader npcs() {
		return (NpcLoader) CachedLoader.NPC_LOADER.load();
	}
	
	public static NpcCombatProfileLoader npcCombatProfiles() {
		return (NpcCombatProfileLoader) CachedLoader.NPC_COMBAT_PROFILES.load();
	}
	
	public static NpcPatrolRouteLoader npcPatrolRoutes() {
		return (NpcPatrolRouteLoader) CachedLoader.NPC_PATROL_ROUTES.load();
	}
	
	public static NpcWeaponLoader npcWeapons() {
		return (NpcWeaponLoader) CachedLoader.NPC_WEAPONS.load();
	}
	
	public static NpcWeaponRangeLoader npcWeaponRanges() {
		return (NpcWeaponRangeLoader) CachedLoader.NPC_WEAPON_RANGES.load();
	}
	
	public static NpcStatLoader npcStats() {
		return (NpcStatLoader) CachedLoader.NPC_STATS.load();
	}
	
	public static NpcStaticSpawnLoader npcStaticSpawns() {
		return (NpcStaticSpawnLoader) CachedLoader.STATIC_SPAWNS.load();
	}
	
	public static ObjectDataLoader objectData() {
		return (ObjectDataLoader) CachedLoader.OBJECT_DATA.load();
	}
	
	public static PerformanceLoader performances() {
		return (PerformanceLoader) CachedLoader.PERFORMANCES.load();
	}
	
	public static CommandLoader commands() {
		return (CommandLoader) CachedLoader.COMMANDS.load();
	}
	
	public static SlotDefinitionLoader slotDefinitions() {
		return (SlotDefinitionLoader) CachedLoader.SLOT_DEFINITIONS.load();
	}
	
	public static TerrainZoneInsertionLoader zoneInsertions() {
		return (TerrainZoneInsertionLoader) CachedLoader.ZONE_INSERTIONS.load();
	}
	
	public static TravelCostLoader travelCosts() {
		return (TravelCostLoader) CachedLoader.TRAVEL_COSTS.load();
	}
	
	public static VehicleLoader vehicles() {
		return (VehicleLoader) CachedLoader.VEHICLES.load();
	}
	
}
