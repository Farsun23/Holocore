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
package com.projectswg.holocore.services.combat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.projectswg.common.control.Service;
import com.projectswg.common.data.encodables.oob.ProsePackage;
import com.projectswg.common.data.encodables.oob.StringId;
import com.projectswg.common.data.encodables.tangible.Posture;
import com.projectswg.common.data.encodables.tangible.PvpFaction;
import com.projectswg.common.data.encodables.tangible.PvpStatus;
import com.projectswg.common.data.info.RelationalDatabase;
import com.projectswg.common.data.info.RelationalServerFactory;
import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.location.Location.LocationBuilder;
import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.data.sui.SuiEvent;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.common.debug.Log;
import com.projectswg.common.network.packets.swg.zone.PlayClientEffectObjectMessage;
import com.projectswg.common.utilities.ThreadUtilities;

import com.projectswg.holocore.intents.BuffIntent;
import com.projectswg.holocore.intents.FactionIntent;
import com.projectswg.holocore.intents.PlayerEventIntent;
import com.projectswg.holocore.intents.chat.SystemMessageIntent;
import com.projectswg.holocore.intents.combat.CorpseLootedIntent;
import com.projectswg.holocore.intents.combat.CreatureKilledIntent;
import com.projectswg.holocore.intents.object.DestroyObjectIntent;
import com.projectswg.holocore.intents.object.ObjectCreatedIntent;
import com.projectswg.holocore.intents.object.ObjectTeleportIntent;
import com.projectswg.holocore.resources.objects.SWGObject;
import com.projectswg.holocore.resources.objects.building.BuildingObject;
import com.projectswg.holocore.resources.objects.cell.CellObject;
import com.projectswg.holocore.resources.objects.creature.CreatureObject;
import com.projectswg.holocore.resources.player.Player;
import com.projectswg.holocore.resources.server_info.StandardLog;
import com.projectswg.holocore.resources.sui.SuiButtons;
import com.projectswg.holocore.resources.sui.SuiListBox;
import com.projectswg.holocore.resources.sui.SuiWindow;

/**
 * The {@code CorpseService} removes corpses from the world a while after
 * they've died. It also lets players clone at a cloning facility.
 * @author mads
 */
public final class CorpseService extends Service {
	
	private static final String DB_QUERY = "SELECT * FROM cloning_respawn";
	private static final byte CLONE_TIMER = 30;	// Amount of minutes before a player is forced to clone
	
	private final ScheduledExecutorService executor;
	private final Map<CreatureObject, Future<?>> reviveTimers;
	private final Map<String, FacilityData> facilityDataMap;
	private final List<BuildingObject> cloningFacilities;
	private final Random random;
	
	private final Map<Long, ScheduledFuture<?>> deleteCorpseTasks;
	
	public CorpseService() {
		executor = Executors.newSingleThreadScheduledExecutor(ThreadUtilities.newThreadFactory("corpse-service"));
		reviveTimers = new HashMap<>();
		facilityDataMap = new HashMap<>();
		cloningFacilities = new ArrayList<>();
		random = new Random();
		
		deleteCorpseTasks = new HashMap<>();
		
		registerForIntent(CreatureKilledIntent.class, this::handleCreatureKilledIntent);
		registerForIntent(CorpseLootedIntent.class, this::handleCorpseLootedIntent);
		registerForIntent(ObjectCreatedIntent.class, this::handleObjectCreatedIntent);
		registerForIntent(DestroyObjectIntent.class, this::handleDestroyObjectIntent);
		registerForIntent(PlayerEventIntent.class, this::handlePlayerEventIntent);
		
		loadFacilityData();
	}

	@Override
	public boolean terminate() {
		executor.shutdown();
		return super.terminate();
	}
	
	private void loadFacilityData() {
		long startTime = StandardLog.onStartLoad("cloning facility data");
		loadRespawnData();
		StandardLog.onEndLoad(facilityDataMap.size(), "cloning facility data", startTime);
	}

	private void loadRespawnData() {
		try (RelationalDatabase respawnDatabase = RelationalServerFactory.getServerData("cloning/cloning_respawn.db", "cloning_respawn")) {
			try (ResultSet set = respawnDatabase.executeQuery(DB_QUERY)) {
				while (set.next()) {
					int tubeCount = set.getInt("tubes");
					TubeData[] tubeData = new TubeData[tubeCount];

					for (int i = 1; i <= tubeCount; i++) {
						String tubeName = "tube" + i;
						tubeData[i - 1] = new TubeData(set.getFloat(tubeName + "_x"), set.getFloat(tubeName + "_z"), set.getFloat(tubeName + "_heading"));
					}

					String stfCellValue = set.getString("stf_name");
					String stfName = stfCellValue.equals("-") ? null : stfCellValue;
					PvpFaction factionRestriction = null;

					switch (stfCellValue) {
						case "FACTION_REBEL":
							factionRestriction = PvpFaction.REBEL;
							break;
						case "FACTION_IMPERIAL":
							factionRestriction = PvpFaction.IMPERIAL;
							break;
					}
					
					FacilityData facilityData = new FacilityData(factionRestriction, set.getFloat("x"), set.getFloat("y"), set.getFloat("z"), set.getString("cell"), FacilityType.valueOf(set.getString("clone_type")), stfName, set.getInt("heading"), tubeData);
					String objectTemplate = set.getString("structure");

					if (facilityDataMap.put(ClientFactory.formatToSharedFile(objectTemplate), facilityData) != null) {
						// Duplicates are not allowed!
						Log.e("Duplicate entry for %s in row %d. Replacing previous entry with new", objectTemplate, set.getRow());
					}
				}
			} catch (SQLException e) {
				Log.e(e);
			}
		}
	}
	
	private void handleCreatureKilledIntent(CreatureKilledIntent i) {
		CreatureObject corpse = i.getCorpse();
		
		if(corpse.isPlayer()) {
			Player corpseOwner = corpse.getOwner();
			new SystemMessageIntent(corpseOwner, new ProsePackage(new StringId("base_player", "prose_victim_dead"), "TT", i.getKiller().getObjectName())).broadcast();
			new SystemMessageIntent(corpseOwner, new ProsePackage(new StringId("base_player", "revive_exp_msg"), "TT", CLONE_TIMER + " minutes.")).broadcast();
			
			scheduleCloneTimer(corpse);
		} else {
			// This is a NPC - schedule corpse for deletion
			ScheduledFuture<?> task = executor.schedule(() -> deleteCorpse(corpse), 120, TimeUnit.SECONDS);
			deleteCorpseTasks.put(corpse.getObjectId(), task);
		}
	}
	
	private void handleCorpseLootedIntent(CorpseLootedIntent i) {
		CreatureObject corpse = i.getCorpse();
		
		ScheduledFuture<?> task = deleteCorpseTasks.get(corpse.getObjectId());
		
		if (task == null) {
			Log.e("There should already be a deleteCorpse task for corpse %s!", corpse.toString());
			executor.schedule(() -> deleteCorpse(corpse), 5, TimeUnit.SECONDS);
			return;
		}
		
		// if existing deleteCorpse task has more than 5 seconds remaining, cancel it
		// if the cancel operation succeeds, schedule another deleteCorpse task for 5 seconds
		if (task.getDelay(TimeUnit.SECONDS) > 5 && task.cancel(false))
			executor.schedule(() -> deleteCorpse(corpse), 5, TimeUnit.SECONDS);
	}
	
	private void handleObjectCreatedIntent(ObjectCreatedIntent i) {
		SWGObject createdObject = i.getObject();
		
		if(!(createdObject instanceof BuildingObject)) {
			return;
		}
		
		BuildingObject createdBuilding = (BuildingObject) createdObject;
		String objectTemplate = createdBuilding.getTemplate();
		
		if(facilityDataMap.containsKey(objectTemplate)) {
			synchronized(cloningFacilities) {
				cloningFacilities.add(createdBuilding);
			}
		}
	}
	
	private void handleDestroyObjectIntent(DestroyObjectIntent i) {
		synchronized(cloningFacilities) {
			SWGObject destroyedObject = i.getObject();
			
			if(!(destroyedObject instanceof BuildingObject)) {
				return;
			}
			
			cloningFacilities.remove(destroyedObject);
		}
	}
	
	private void handlePlayerEventIntent(PlayerEventIntent i) {
		switch(i.getEvent()) {
			case PE_DISAPPEAR: {
				CreatureObject creature = i.getPlayer().getCreatureObject();
				Future<?> reviveTimer = reviveTimers.remove(creature);
				
				if (reviveTimer != null) {
					// They had an active timer when they disappeared
					reviveTimer.cancel(false);
				}
				break;
			}
			
			case PE_FIRST_ZONE: {
				CreatureObject creature = i.getPlayer().getCreatureObject();

				if (creature.getPosture() == Posture.DEAD && !reviveTimers.containsKey(creature)) {
					// They're dead but they have no active revive timer.
					// In this case, they didn't clone before the application was shut down and started back up.
					scheduleCloneTimer(creature);
				}
				break;
			}
			default:
				break;
		}
	}
	
	private void scheduleCloneTimer(CreatureObject corpse) {
		Terrain corpseTerrain = corpse.getTerrain();
		List<BuildingObject> availableFacilities = getAvailableFacilities(corpse);
		
		if (availableFacilities.isEmpty()) {
			Log.e("No cloning facility is available for terrain %s - %s has nowhere to properly clone", corpseTerrain, corpse);
			return;
		}

		SuiWindow cloningWindow = createSuiWindow(availableFacilities, corpse);

		cloningWindow.display(corpse.getOwner());
		synchronized (reviveTimers) {
			reviveTimers.put(corpse, executor.schedule(() -> expireCloneTimer(corpse, availableFacilities, cloningWindow), CLONE_TIMER, TimeUnit.MINUTES));
		}
	}
	
	private SuiWindow createSuiWindow(List<BuildingObject> availableFacilities, CreatureObject corpse) {
		SuiListBox suiWindow = new SuiListBox(SuiButtons.OK, "@base_player:revive_title", "@base_player:clone_prompt_header");
		
		for (BuildingObject cloningFacility : availableFacilities) {
			FacilityData facilityData = facilityDataMap.get(cloningFacility.getTemplate());
			String name;
			
			if (facilityData.getStfName() != null)
				name = facilityData.getStfName();
			else if (!cloningFacility.getCurrentCity().isEmpty())
				name = cloningFacility.getCurrentCity();
			else
				name = String.format("%s[%d, %d]", cloningFacility.getTerrain(), (int) cloningFacility.getX(), (int) cloningFacility.getZ());
			
			suiWindow.addListItem(name);
		}
		
		suiWindow.addCallback("handleFacilityChoice", (SuiEvent event, Map<String, String> parameters) -> {
			int selectionIndex = SuiListBox.getSelectedRow(parameters);

			if (event != SuiEvent.OK_PRESSED || selectionIndex >= availableFacilities.size() || selectionIndex < 0) {
				suiWindow.display(corpse.getOwner());
				return;
			}

			if (reviveCorpse(corpse, availableFacilities.get(selectionIndex)) != CloneResult.SUCCESS) {
				suiWindow.display(corpse.getOwner());
			}
		});

		return suiWindow;
	}
	
	/**
	 * Only used for NPCs!
	 * @param creatureCorpse non-player creature to delete from the world
	 */
	private void deleteCorpse(CreatureObject creatureCorpse) {
		if(creatureCorpse.isPlayer()) {
			Log.e("Cannot delete the corpse of a player!", creatureCorpse);
		} else {
			new DestroyObjectIntent(creatureCorpse).broadcast();
			deleteCorpseTasks.remove(creatureCorpse.getObjectId());
			Log.d("Corpse of NPC %s was deleted from the world", creatureCorpse);
		}
	}
	
	/**
	 * 
	 * @param corpse
	 * @return a sorted list of {@code BuildingObject}, ordered by distance
	 * to {@code corpse}. Order is reversed, so the closest facility is
	 * first.
	 */
	private List<BuildingObject> getAvailableFacilities(CreatureObject corpse) {
		synchronized (cloningFacilities) {
			Location corpseLocation = corpse.getWorldLocation();
			return cloningFacilities.stream()
					.filter(facilityObject -> isValidTerrain(facilityObject, corpse) && isFactionAllowed(facilityObject, corpse))
					.sorted(Comparator.comparingDouble(facility -> corpseLocation.distanceTo(facility.getLocation())))
					.collect(Collectors.toList());
			
		}
	}
	
	// TODO below doesn't apply to a a player that died in a heroic. Cloning on Dathomir should be possible if you die during the Axkva Min heroic.
	private boolean isValidTerrain(BuildingObject cloningFacility, CreatureObject corpse)  {
		return cloningFacility.getTerrain() == corpse.getTerrain();
	}
	
	private boolean isFactionAllowed(BuildingObject cloningFacility, CreatureObject corpse) {
		FacilityData facilityData = facilityDataMap.get(cloningFacility.getTemplate());
		PvpFaction factionRestriction = facilityData.getFactionRestriction();
		
		return factionRestriction == null || factionRestriction == corpse.getPvpFaction();
	}
	
	private CloneResult reviveCorpse(CreatureObject corpse, BuildingObject selectedFacility) {
		FacilityData facilityData = facilityDataMap.get(selectedFacility.getTemplate());

		if (facilityData == null) {
			Log.e("%s could not clone at facility %s because the object template is not in cloning_respawn.sdb", corpse, selectedFacility);
			return CloneResult.TEMPLATE_MISSING;
		}

		String cellName = facilityData.getCell();
		CellObject cellObject = selectedFacility.getCellByName(cellName);

		if (cellObject == null) {
			Log.e("Cell %s was invalid for cloning facility %s", cellName, selectedFacility);
			return CloneResult.INVALID_CELL;
		}
		
		// Cancel the forced cloning timer
		synchronized (reviveTimers) {
			Future<?> timer = reviveTimers.remove(corpse);
			
			if (timer != null) {
				timer.cancel(false);
			}
		}
		
		teleport(corpse, cellObject, getCloneLocation(facilityData, selectedFacility));
		return CloneResult.SUCCESS;
	}
	
	private Location getCloneLocation(FacilityData facilityData, BuildingObject selectedFacility) {
		LocationBuilder cloneLocation = Location.builder();
		Location facilityLocation = selectedFacility.getLocation();
		TubeData[] tubeData = facilityData.getTubeData();
		int tubeCount = tubeData.length;

		if (tubeCount > 0) {
			TubeData randomData = tubeData[random.nextInt(tubeCount)];
			cloneLocation.setTerrain(facilityLocation.getTerrain());
			cloneLocation.setPosition(randomData.getTubeX(), 0, randomData.getTubeZ());
			cloneLocation.setOrientation(facilityLocation.getOrientationX(), facilityLocation.getOrientationY(), facilityLocation.getOrientationZ(), facilityLocation.getOrientationW());
			cloneLocation.rotateHeading(randomData.getTubeHeading());
		} else {
			cloneLocation.setTerrain(facilityLocation.getTerrain());
			cloneLocation.setPosition(facilityData.getX(), facilityData.getY(), facilityData.getZ());
			cloneLocation.rotateHeading(facilityData.getHeading());
		}
		
		return cloneLocation.build();
	}
	
	private void teleport(CreatureObject corpse, CellObject cellObject, Location cloneLocation) {
		if (corpse.getPvpFaction() != PvpFaction.NEUTRAL) {
			new FactionIntent(corpse, PvpStatus.ONLEAVE).broadcast();
		}
		
		new ObjectTeleportIntent(corpse, cellObject, cloneLocation).broadcast();
		corpse.setPosture(Posture.UPRIGHT);
		corpse.setTurnScale(1);
		corpse.setMovementScale(1);
		corpse.setHealth(corpse.getMaxHealth());
		corpse.sendObserversAndSelf(new PlayClientEffectObjectMessage("clienteffect/player_clone_compile.cef", "", corpse.getObjectId(), ""));
		
		BuffIntent cloningSickness = new BuffIntent("cloning_sickness", corpse, corpse, false);
		new BuffIntent("incapWeaken", corpse, corpse, true).broadcastAfterIntent(cloningSickness);
		cloningSickness.broadcast();
	}
	
	/**
	 * Picks the closest cloning facility and clones {@code cloneRequestor}
	 * there. If an error occurs upon attempting to clone, it will pick the next
	 * facility until no errors occur. If allAlso closes the cloning SUI window.
	 * @param cloneRequestor
	 * @param facilitiesInTerrain list of {@code BuildingObject} that represent
	 * in-game cloning facilities
	 * @return {@code true} if forceful cloning was succesful and {@code false}
	 * if {@code cloneRequestor} cannot be cloned at any of the given facilities
	 * in {@code facilitiesInTerrain}
	 */
	private boolean forceClone(CreatureObject cloneRequestor, List<BuildingObject> facilitiesInTerrain) {
		for (BuildingObject facility : facilitiesInTerrain) {
			if (reviveCorpse(cloneRequestor, facility) == CloneResult.SUCCESS) {
				return true;
			}
		}
		
		return false;
	}
	
	private void expireCloneTimer(CreatureObject corpse, List<BuildingObject> facilitiesInTerrain, SuiWindow suiWindow) {
		if(reviveTimers.remove(corpse) != null) {
			Player corpseOwner = corpse.getOwner();
		
			new SystemMessageIntent(corpseOwner, "@base_player:revive_expired").broadcast();
			suiWindow.close(corpseOwner);
			forceClone(corpse, facilitiesInTerrain);
		} else {
			Log.w("Could not expire timer for %s because none was active", corpse);
		}
	}
	
	private static class FacilityData {
		private final PvpFaction factionRestriction;
		private final float x, y, z;
		private final String cell;
		private final FacilityType facilityType;
		private final String stfName;
		private final int heading;
		private final TubeData[] tubeData;

		public FacilityData(PvpFaction factionRestriction, float x, float y, float z, String cell, FacilityType facilityType, String stfName, int tubeHeading, TubeData[] tubeData) {
			this.factionRestriction = factionRestriction;
			this.x = x;
			this.y = y;
			this.z = z;
			this.cell = cell;
			this.facilityType = facilityType;
			this.stfName = stfName;
			this.heading = tubeHeading;
			this.tubeData = tubeData;
		}

		public PvpFaction getFactionRestriction() {
			return factionRestriction;
		}

		public float getX() {
			return x;
		}

		public float getY() {
			return y;
		}

		public float getZ() {
			return z;
		}

		public String getCell() {
			return cell;
		}

		public FacilityType getFacilityType() {
			return facilityType;
		}

		public String getStfName() {
			return stfName;
		}

		public int getHeading() {
			return heading;
		}

		public TubeData[] getTubeData() {
			return tubeData;
		}
	}
	
	private enum FacilityType {
		STANDARD,
		RESTRICTED,
		PLAYER_CITY,
		CAMP,
		PRIVATE_INSTANCE,
		FACTION_IMPERIAL,
		FACTION_REBEL,
		PVP_REGION_ADVANCED_IMPERIAL,
		PVP_REGION_ADVANCED_REBEL
	}
	
	private static class TubeData {
		private final float tubeX, tubeZ, tubeHeading;

		public TubeData(float tubeX, float tubeZ, float tubeHeading) {
			this.tubeX = tubeX;
			this.tubeZ = tubeZ;
			this.tubeHeading = tubeHeading;
		}

		public float getTubeX() {
			return tubeX;
		}

		public float getTubeZ() {
			return tubeZ;
		}

		public float getTubeHeading() {
			return tubeHeading;
		}
	}
	
	private enum CloneResult {
		INVALID_SELECTION, TEMPLATE_MISSING, INVALID_CELL, SUCCESS
	}
	
}
