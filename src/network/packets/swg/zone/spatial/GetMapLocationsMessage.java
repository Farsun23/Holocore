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
package network.packets.swg.zone.spatial;

import network.packets.swg.SWGPacket;

import java.nio.ByteBuffer;

public class GetMapLocationsMessage extends SWGPacket {
	public static final int CRC = getCrc("GetMapLocationsMessage");
	
	private String planet;
	private int versionStatic;
	private int versionDynamic;
	private int versionPersist;

	public GetMapLocationsMessage() {
	}

	public void decode(ByteBuffer data) {
		if (!super.decode(data, CRC))
			return;
		planet = getAscii(data);
		versionStatic = getInt(data);
		versionDynamic = getInt(data);
		versionPersist = getInt(data);
	}
	
	public ByteBuffer encode() {
		ByteBuffer data = ByteBuffer.allocate(19 + planet.length());
		addShort(data, 28);
		addInt  (data, CRC);
		addAscii(data, planet);
		addInt(data, versionStatic);
		addInt(data, versionDynamic);
		addInt(data, versionPersist);
		return data;
	}
	
	public String getPlanet() { return planet; }

	public int getVersionDynamic() {
		return versionDynamic;
	}

	public int getVersionStatic() {
		return versionStatic;
	}

	public int getVersionPersist() {
		return versionPersist;
	}

	@Override
	public String toString() {
		return String.format("[GetMapLocationsMessage] planet=%s static=%d dynamic=%d persist=%d",
				planet, versionStatic, versionDynamic, versionPersist);
	}
}

