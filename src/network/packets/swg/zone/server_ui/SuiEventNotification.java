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
package network.packets.swg.zone.server_ui;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import network.packets.swg.SWGPacket;

public class SuiEventNotification extends SWGPacket {
	
	public static final int CRC = 0x092D3564;
	
	private int windowId;
	private int eventId;
	private int updateCount;
	private List <String> dataStrings;
	
	public SuiEventNotification() { 
		dataStrings = new ArrayList<String>();
	}
	
	public SuiEventNotification(ByteBuffer data) {
		this();
		decode(data);
	}
	
	public void decode(ByteBuffer data) {
		if (!super.decode(data, CRC))
			return;
		windowId = getInt(data);
		eventId = getInt(data);
		int size = getInt(data);
		updateCount = getInt(data);
		for (int i = 0; i < size; i++) {
			dataStrings.add(getUnicode(data));
		}
	}
	
	public ByteBuffer encode() {
		return null;
	}
	
	public int getWindowId() { return this.windowId; }
	public List<String> getDataStrings() { return this.dataStrings; }
	public int getEventId() { return this.eventId; }
	public int getUpdateCount() { return this.updateCount; }
}
