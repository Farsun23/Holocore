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
package services.crafting.survey;

import java.util.List;

import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.debug.Log;

import network.packets.swg.zone.crafting.surveying.SurveyMessage;
import network.packets.swg.zone.crafting.surveying.SurveyMessage.ResourceConcentration;
import resources.objects.creature.CreatureObject;
import services.crafting.resource.galactic.GalacticResource;
import services.crafting.resource.galactic.GalacticResourceSpawn;
import services.crafting.resource.galactic.storage.GalacticResourceContainer;

public class SurveySession {
	
	private final CreatureObject creature;
	private final GalacticResource resource;
	
	public SurveySession(CreatureObject creature, GalacticResource resource) {
		this.creature = creature;
		this.resource = resource;
	}
	
	public GalacticResource getResource() {
		return resource;
	}
	
	public void startSession() {
		Log.d("%s start survey session with %s", creature.getObjectName(), resource);
		SurveyMessage surveyMessage = new SurveyMessage();
		loadResourcePoints(surveyMessage, creature, resource, 320);
		creature.getOwner().sendPacket(surveyMessage);
	}
	
	public void stopSession() {
		Log.d("%s ending survey session with %s", creature.getObjectName(), resource);
	}
	
	private void loadResourcePoints(SurveyMessage surveyMessage, CreatureObject creature, GalacticResource resource, int range) {
		double baseLocationX = creature.getX();
		double baseLocationZ = creature.getZ();
		List<GalacticResourceSpawn> spawns = GalacticResourceContainer.getContainer().getTerrainResourceSpawns(resource, creature.getTerrain());
		double interval = range / 4;
		double halfWidth = interval * 2;
		for (double x = baseLocationX - halfWidth; x <= baseLocationX + halfWidth; x += interval) {
			for (double z = baseLocationZ - halfWidth; z <= baseLocationZ + halfWidth; z += interval) {
				surveyMessage.addConcentration(new ResourceConcentration(x, z, getConcentration(spawns, creature.getTerrain(), x, z)));
			}
		}
	}
	
	private double getConcentration(List<GalacticResourceSpawn> spawns, Terrain terrain, double x, double z) {
		double concentration = 0;
		for (GalacticResourceSpawn spawn : spawns) {
			concentration += spawn.getConcentration(terrain, x, z) / 100.0;
		}
		return concentration;
	}
	
}
