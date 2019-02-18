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
package com.projectswg.holocore.resources.gameplay.crafting.survey;

import com.projectswg.common.data.encodables.oob.ProsePackage;
import com.projectswg.common.data.encodables.oob.StringId;
import com.projectswg.common.data.encodables.oob.waypoint.WaypointColor;
import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.network.packets.swg.zone.PlayClientEffectObjectMessage;
import com.projectswg.common.network.packets.swg.zone.PlayMusicMessage;
import com.projectswg.common.network.packets.swg.zone.chat.ChatSystemMessage;
import com.projectswg.common.network.packets.swg.zone.chat.ChatSystemMessage.SystemChatType;
import com.projectswg.common.network.packets.swg.zone.crafting.surveying.SurveyMessage;
import com.projectswg.common.network.packets.swg.zone.crafting.surveying.SurveyMessage.ResourceConcentration;
import com.projectswg.holocore.intents.support.objects.swg.ObjectCreatedIntent;
import com.projectswg.holocore.resources.gameplay.crafting.resource.galactic.GalacticResource;
import com.projectswg.holocore.resources.gameplay.crafting.resource.galactic.GalacticResourceSpawn;
import com.projectswg.holocore.resources.gameplay.crafting.resource.galactic.RawResourceType;
import com.projectswg.holocore.resources.gameplay.crafting.resource.galactic.storage.GalacticResourceContainer;
import com.projectswg.holocore.resources.gameplay.crafting.resource.raw.RawResource;
import com.projectswg.holocore.resources.support.objects.ObjectCreator;
import com.projectswg.holocore.resources.support.objects.swg.ServerAttribute;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureState;
import com.projectswg.holocore.resources.support.objects.swg.tangible.TangibleObject;
import com.projectswg.holocore.resources.support.objects.swg.waypoint.WaypointObject;
import com.projectswg.holocore.utilities.ScheduledUtilities;
import me.joshlarson.jlcommon.log.Log;

import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;

public class SurveySession {
	
	private final CreatureObject creature;
	private final TangibleObject surveyTool;
	private final AtomicReference<ScheduledFuture<?>> surveyRequest;
	
	public SurveySession(CreatureObject creature, TangibleObject surveyTool) {
		this.creature = creature;
		this.surveyTool = surveyTool;
		this.surveyRequest = new AtomicReference<>(null);
	}
	
	public synchronized void startSession() {
		
	}
	
	public synchronized void stopSession() {
		ScheduledFuture<?> surveyRequest = this.surveyRequest.get();
		if (surveyRequest != null)
			surveyRequest.cancel(false);
	}
	
	public synchronized void startSurvey(GalacticResource resource) {
		ScheduledFuture<?> prev = surveyRequest.get();
		if (prev != null && !prev.isDone())
			return;
		SurveyToolResolution resolution = getCurrentResolution();
		Location location = creature.getWorldLocation();
		if (!isAllowedToSurvey(resolution))
			return;
		assert resolution != null : "verified in isAllowedToSurvey";
		
		surveyRequest.set(ScheduledUtilities.run(() -> sendSurveyMessage(resolution, location, resource), 4, SECONDS));
		creature.sendSelf(new ChatSystemMessage(SystemChatType.PERSONAL, new ProsePackage(new StringId("survey", "start_survey"), "TO", resource.getName())));
		creature.sendSelf(new PlayMusicMessage(0, getMusicFile(resource), 1, false));
		creature.sendObservers(new PlayClientEffectObjectMessage(getEffectFile(resource), "", creature.getObjectId(), ""));
	}
	
	private void sendSurveyMessage(SurveyToolResolution resolution, Location location, GalacticResource resource) {
		final double baseLocationX = location.getX();
		final double baseLocationZ = location.getZ();
		final double rangeHalf = resolution.getRange()/2.0;
		final double rangeInc = resolution.getRange()/(resolution.getResolution()-1.0);
		
		SurveyMessage surveyMessage = new SurveyMessage();
		List<GalacticResourceSpawn> spawns = GalacticResourceContainer.getContainer().getTerrainResourceSpawns(resource, location.getTerrain());
		double highestX = baseLocationX;
		double highestZ = baseLocationX;
		double highestConcentration = 0;
		
		for (double x = baseLocationX - rangeHalf, xIndex = 0; xIndex < resolution.getResolution(); x += rangeInc, xIndex++) {
			for (double z = baseLocationZ - rangeHalf, zIndex = 0; zIndex < resolution.getResolution(); z += rangeInc, zIndex++) {
				double concentration = getConcentration(spawns, location.getTerrain(), x, z);
				surveyMessage.addConcentration(new ResourceConcentration(x, z, concentration));
				if (concentration > highestConcentration) {
					highestX = x;
					highestZ = z;
					highestConcentration = concentration;
				}
			}
		}
		creature.sendSelf(surveyMessage);
		if (highestConcentration > 0.1) {
			creature.getPlayerObject().getWaypoints().entrySet().stream()
					.filter(e -> "Resource Survey".equals(e.getValue().getName()))
					.filter(e -> e.getValue().getColor() == WaypointColor.ORANGE)
					.filter(e -> e.getValue().getTerrain() == location.getTerrain())
					.map(Entry::getKey)
					.forEach(creature.getPlayerObject()::removeWaypoint);
			createResourceWaypoint(creature, Location.builder(location).setX(highestX).setZ(highestZ).build());
			sendErrorMessage(creature, "survey", "survey_waypoint");
		}
	}
	
	private SurveyToolResolution getCurrentResolution() {
		Integer counterSetting = (Integer) surveyTool.getServerAttribute(ServerAttribute.SURVEY_TOOL_RANGE);
		if (counterSetting == null)
			return null; // Must be set before using - invariant enforced within ObjectSurveyToolRadial.java
		int counter = counterSetting;
		
		List<SurveyToolResolution> resolutions = SurveyToolResolution.getOptions(creature);
		for (SurveyToolResolution resolution : resolutions) {
			if (resolution.getCounter() == counter)
				return resolution;
		}
		
		// Attempted to set a resolution that wasn't valid
		return resolutions.isEmpty() ? null : resolutions.get(resolutions.size()-1);
	}
	
	private double getConcentration(List<GalacticResourceSpawn> spawns, Terrain terrain, double x, double z) {
		int concentration = 0;
		for (GalacticResourceSpawn spawn : spawns) {
			concentration += spawn.getConcentration(terrain, x, z);
		}
		if (concentration < 10) // Minimum density
			return 0;
		return concentration / 100.0;
	}
	
	private boolean isAllowedToSurvey(SurveyToolResolution resolution) {
		switch (creature.getPosture()) {
			case SITTING:
				sendErrorMessage(creature, "error_message", "survey_sitting");
				return false;
			case UPRIGHT:
				break;
			default:
				sendErrorMessage(creature, "error_message", "survey_standing");
				return false;
		}
		if (creature.getInstanceLocation().getInstanceNumber() != 0) {
			sendErrorMessage(creature, "error_message", "no_survey_instance");
			return false;
		}
		if (creature.getParent() != null) {
			if (creature.isStatesBitmask(CreatureState.RIDING_MOUNT))
				sendErrorMessage(creature, "error_message", "survey_on_mount");
			else
				sendErrorMessage(creature, "error_message", "survey_in_structure");
			return false;
		}
		if (resolution == null) {
			creature.sendSelf(new ChatSystemMessage(SystemChatType.PERSONAL, "No survey tool resolution has been set"));
			return false;
		}
		if (surveyTool.getParent() != creature.getInventory()) {
			creature.sendSelf(new ChatSystemMessage(SystemChatType.PERSONAL, "The survey tool is not in your inventory"));
			return false;
		}
		return true;
	}
	
	private static String getMusicFile(GalacticResource resource) {
		RawResource rawResource = resource.getRawResource();
		if (RawResourceType.MINERAL.isResourceType(rawResource))
			return "sound/item_mineral_tool_survey.snd";
		if (RawResourceType.WATER.isResourceType(rawResource))
			return "sound/item_moisture_tool_survey.snd";
		if (RawResourceType.CHEMICAL.isResourceType(rawResource))
			return "sound/item_liquid_tool_survey.snd";
		if (RawResourceType.FLORA_STRUCTURAL.isResourceType(rawResource))
			return "sound/item_lumber_tool_survey.snd";
		if (RawResourceType.GAS.isResourceType(rawResource))
			return "sound/item_gas_tool_survey.snd";
		if (RawResourceType.ENERGY_RENEWABLE_UNLIMITED_SOLAR.isResourceType(rawResource))
			return "sound/item_moisture_tool_survey.snd";
		if (RawResourceType.ENERGY_RENEWABLE_UNLIMITED_WIND.isResourceType(rawResource))
			return "sound/item_moisture_tool_survey.snd";
		Log.w("Unknown raw resource survey music file: %s with type %s", rawResource, rawResource.getResourceType());
		return "";
	}
	
	private static String getEffectFile(GalacticResource resource) {
		RawResource rawResource = resource.getRawResource();
		if (RawResourceType.MINERAL.isResourceType(rawResource))
			return "clienteffect/survey_tool_mineral.cef";
		if (RawResourceType.WATER.isResourceType(rawResource))
			return "clienteffect/survey_tool_moisture.cef";
		if (RawResourceType.CHEMICAL.isResourceType(rawResource))
			return "clienteffect/survey_tool_liquid.cef";
		if (RawResourceType.FLORA_STRUCTURAL.isResourceType(rawResource))
			return "clienteffect/survey_tool_lumber.cef";
		if (RawResourceType.GAS.isResourceType(rawResource))
			return "clienteffect/survey_tool_gas.cef";
		if (RawResourceType.ENERGY_RENEWABLE_UNLIMITED_SOLAR.isResourceType(rawResource))
			return "clienteffect/survey_tool_moisture.cef";
		if (RawResourceType.ENERGY_RENEWABLE_UNLIMITED_WIND.isResourceType(rawResource))
			return "clienteffect/survey_tool_moisture.cef";
		Log.w("Unknown raw resource survey effect file: %s with type %s", rawResource, rawResource.getResourceType());
		return "";
	}
	
	private static void createResourceWaypoint(CreatureObject creature, Location location) {
		WaypointObject waypoint = (WaypointObject) ObjectCreator.createObjectFromTemplate("object/waypoint/shared_waypoint.iff");
		waypoint.setPosition(location.getTerrain(), location.getX(), location.getY(), location.getZ());
		waypoint.setColor(WaypointColor.ORANGE);
		waypoint.setName("Resource Survey");
		ObjectCreatedIntent.broadcast(waypoint);
		creature.getPlayerObject().addWaypoint(waypoint);
	}
	
	private static void sendErrorMessage(CreatureObject creature, String file, String key) {
		creature.sendSelf(new ChatSystemMessage(SystemChatType.PERSONAL, new ProsePackage(new StringId(file, key))));
	}
	
}
