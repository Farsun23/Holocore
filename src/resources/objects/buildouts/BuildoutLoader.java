/***********************************************************************************
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
package resources.objects.buildouts;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import resources.Terrain;
import resources.client_info.ClientFactory;
import resources.client_info.visitors.CrcStringTableData;
import resources.client_info.visitors.DatatableData;
import resources.objects.SWGObject;
import resources.server_info.Log;

public class BuildoutLoader {
	
	private static final ClientFactory clientFactory = new ClientFactory();
	private static final CrcStringTableData crcTable = (CrcStringTableData) clientFactory.getInfoFromFile("misc/object_template_crc_string_table.iff");
	
	public static List <SWGObject> loadAllBuildouts() {
		List <SWGObject> objects = new LinkedList<SWGObject>();
		for (Terrain t : getTerrainsToLoad())
			objects.addAll(loadBuildoutsForTerrain(t));
		return objects;
	}
	
	public static List <SWGObject> loadBuildoutsForTerrain(Terrain terrain) {
		DatatableData table = (DatatableData) clientFactory.getInfoFromFile("datatables/buildout/buildout_scenes.iff");
		for (int row = 0; row < table.getRowCount(); row++) {
			if (table.getCell(row, 0).equals(terrain.name().toLowerCase(Locale.ENGLISH))) {
				TerrainBuildoutLoader loader = new TerrainBuildoutLoader(clientFactory, crcTable, terrain);
				loader.load(row);
				return loader.getObjects();
			}
		}
		System.err.println("Could not find buildouts for terrain: " + terrain);
		Log.e("BuildoutLoader", "Could not find buildouts for terrain: %s", terrain);
		return new LinkedList<>();
	}
	
	private static List <Terrain> getTerrainsToLoad() {
		DatatableData table = (DatatableData) clientFactory.getInfoFromFile("datatables/buildout/buildout_scenes.iff");
		List <Terrain> terrains = new LinkedList<Terrain>();
		for (int row = 0; row < table.getRowCount(); row++) {
			Terrain t = Terrain.getTerrainFromName((String) table.getCell(row, 0));
			if (t != null)
				terrains.add(t);
			else
				System.err.println("Couldn't find terrain: " + table.getCell(row, 0));
		}
		return terrains;
	}
	
}
