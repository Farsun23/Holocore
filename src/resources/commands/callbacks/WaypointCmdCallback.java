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
package resources.commands.callbacks;

import intents.chat.ChatBroadcastIntent;
import resources.Location;
import resources.Terrain;
import resources.commands.ICmdCallback;
import resources.objects.SWGObject;
import resources.objects.player.PlayerObject;
import resources.objects.waypoint.WaypointObject;
import resources.objects.waypoint.WaypointObject.WaypointColor;
import resources.player.Player;
import services.galaxy.GalacticManager;
import services.objects.ObjectManager;

public class WaypointCmdCallback implements ICmdCallback {

	@Override
	public void execute(GalacticManager galacticManager, Player player, SWGObject target, String args) {
		PlayerObject ghost = player.getPlayerObject();
		if (ghost == null)
			return;

		String[] cmdArgs = args.split(" ");

		WaypointColor color = WaypointColor.BLUE;
		Terrain terrain = null;
		String name = null;
		float x = Float.NaN;
		float z = Float.NaN;

		// Validate parameters, format for proper command arguments as it's split at whitespace
		for (int i = 0; i < cmdArgs.length; i++) {
			switch(i) {
				// This could be either for just a named waypoint at current spot (1 param) or a planet arg (6 param)
				case 0:
					if (Terrain.getTerrainFromName(cmdArgs[0]) != null) {
						// Terrain's name could also be part of the waypoint name, check to see if next few args are coords
						try {
							x = Float.parseFloat(cmdArgs[1]);
							if (cmdArgs.length >= 3) // Just to be sure.. Maybe someone wanted some numbers in the name.
								z = Float.parseFloat(cmdArgs[2]);
						} catch (NumberFormatException e) {
							// This is just a named waypoint.
							cmdArgs = new String[]{args};
						}
					} else {
						// This is just a named waypoint.
						cmdArgs = new String[]{args};
					}
					break;
				// This could be either for a name (3 param) or a z coordinate (6 param)
				case 3:
					try {
						z = Float.parseFloat(cmdArgs[3]);
						// Ensure 100% this is a 6 argument command as the first param MUST be the planet name
						if (Terrain.getTerrainFromName(cmdArgs[0]) != null)
							cmdArgs = args.split(" ", 6);
						else cmdArgs = args.split(" ", 4);
					} catch (NumberFormatException e) {
						// This is intended for a name, should be 4 params
						cmdArgs = args.split(" ", 4);
					}
					break;
				default: break;
			}
		}

		// Was there an error message saying the format was wrong?
		
		switch(cmdArgs.length) {
			case 1: // name
				name = cmdArgs[0];
				break;
			case 2: // x y
				x = floatValue(cmdArgs[0]);
				if (Float.isNaN(x))
					break;
				z = floatValue(cmdArgs[1]);
				break;
			case 3: // x y z
				x = floatValue(cmdArgs[0]);
				if (Float.isNaN(x))
					break;
				//y = floatValue(cmdArgs[1]);
				z = floatValue(cmdArgs[2]);
				break;
			case 4: // x y z name
				x = floatValue(cmdArgs[0]);
				if (Float.isNaN(x))
					break;
				//y = floatValue(cmdArgs[1]);
				z = floatValue(cmdArgs[2]);
				if (Float.isNaN(z))
					break;
				name = cmdArgs[3];
				break;
			case 6: // planet x y z color name
				terrain = Terrain.getTerrainFromName(cmdArgs[0]);
				if (terrain == null)
					break;
				x = floatValue(cmdArgs[1]);
				if (Float.isNaN(x))
					break;
				//y = floatValue(cmdArgs[2]);
				z = floatValue(cmdArgs[3]);
				if (Float.isNaN(z))
					break;
				color = WaypointColor.fromString(cmdArgs[4]);
				name = cmdArgs[5];
				break;
			default: break;
		}

		Location location = new Location(player.getCreatureObject().getLocation());

		if (!Float.isNaN(x))
			location.setX(x);

		if (!Float.isNaN(z))
			location.setZ(z);

		if (terrain != null) {
			if (terrain != location.getTerrain()) {
				location.setTerrain(terrain);
			}
		}

		if (name == null)
			name = "Waypoint";

		if (Float.isNaN(x) || Float.isNaN(z))
			return;

		WaypointObject waypoint = createWaypoint(galacticManager.getObjectManager(), color, name, location);
		ghost.addWaypoint(waypoint);
	}

	private WaypointObject createWaypoint(ObjectManager objManager, WaypointColor color, String name, Location location) {
		WaypointObject waypoint = (WaypointObject) objManager.createObject("object/waypoint/shared_waypoint.iff", location, false);
		waypoint.setColor(color);
		waypoint.setName(name);
		return waypoint;
	}
	
	private float floatValue(String str) {
		try {
			return Float.parseFloat(str);
		} catch (NumberFormatException | NullPointerException e) {
			return Float.NaN;
		}
	}
}
