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
package network.packets.swg.zone;

import network.packets.swg.SWGPacket;
import resources.Posture;

import java.nio.ByteBuffer;

public class UpdatePostureMessage extends SWGPacket {
	public static final int CRC = getCrc("UpdatePostureMessage");

	private int posture = 0;
	private long objId = 0;
	
	public UpdatePostureMessage() {
		
	}
	
	public UpdatePostureMessage(Posture posture, long objId) {
		this.posture = posture.getId();
		this.objId = objId;
	}
	
	public UpdatePostureMessage(int posture, long objId) {
		this.posture = posture;
		this.objId = objId;
	}
	
	public void decode(ByteBuffer data) {
		if (!super.decode(data, CRC))
			return;
		posture = getByte(data);
		objId = getLong(data);
	}
	
	public ByteBuffer encode() {
		int length = 16;
		ByteBuffer data = ByteBuffer.allocate(length);
		addShort(data, 3);
		addInt  (data, CRC);
		addByte (data, posture);
		addLong (data, objId);
		return data;
	}
	
	public int getPosture() {
		return posture;
	}
	
	public long getObjectId() {
		return objId;
	}
	
	public void setPosture(int posture) {
		this.posture = posture;
	}
	
	public void setObjId(long objId) {
		this.objId = objId;
	}
}
