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
package network.packets.swg.zone.baselines;

import java.nio.ByteBuffer;


public class CREO6 extends Baseline {
	
	private long objectId = 0;
	
	public CREO6(long objectId) {
		this.objectId = objectId;
	}
	
	public void decodeBaseline(ByteBuffer data) {
		
	}
	
	public ByteBuffer encodeBaseline() {
		//		ByteBuffer data = ByteBuffer.allocate(260);
		//		addShort(data, 0x23);
		//		addFloat(data, 0);
		//		addInt(data, 0);
		//		addInt(data, 0);
		//
		//		addLong(data, 0);
		//		addLong(data, 0);
		//		addLong(data, 0);
		//		addLong(data, 0);
		//		addInt(data, 0);
		//
		//		addByte(data, 0);
		//		addShort(data, 90);
		//		addInt(data, (byte) 0xD007);
		//		addShort(data, 0);
		//		addAscii(data, "neutral");
		//		//addLong(data, objectId + 5);	// WEAO ID 
		//		addLong(data, 0);
		//
		//		addLong(data, 0);
		//		addInt(data, 0);
		//		addLong(data, 0);
		//		addLong(data, 0);
		//		addLong(data, 0);
		//		addByte(data, 0);
		//		addInt(data, 0);
		//		addInt(data, 0);
		//		addInt(data, 0);
		//		addShort(data, 0);
		//
		//		addInt(data, 6);
		//		addInt(data, 6);	// Current Combat stats list
		//		
		//		addInt(data, 350);
		//		addInt(data, 350);
		//		addInt(data, 350);
		//		addInt(data, 350);
		//		addInt(data, 350);
		//		addInt(data, 350);
		//		
		//
		//		addInt(data, 6);
		//		addInt(data, 6);	// Maximum Combat stats list
		//
		//		addInt(data, 350);
		//		addInt(data, 350);
		//		addInt(data, 350);
		//		addInt(data, 350);
		//		addInt(data, 350);
		//		addInt(data, 350);
		//
		//		
		//		addInt(data, 1);
		//		addInt(data, 1);
		//		
		//		addShort(data, 0);
		//		addInt(data, 4);
		//		addLong(data, objectId + 3);
		//		addInt(data, 0x73ba5001);
		//		addByte(data, 0);
		//		
		//		addNetInt(data, 1);
		//		
		//		addLong(data, 0);
		//
		//		addShort(data, 0);
		//		
		//		return data;
		byte [] data1 = {
				(byte) 0x23, (byte) 0x00, (byte) 0x4E, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x5A, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0x63, (byte) 0x6F, (byte) 0x6E, (byte) 0x76, (byte) 0x65, (byte) 0x72, (byte) 0x73, (byte) 0x61, (byte) 0x74, (byte) 0x69, (byte) 0x6F, (byte) 0x6E, (byte) 0x00, (byte) 0xAE, (byte) 0x5E, (byte) 0xA9, (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x3B, (byte) 0x09, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xEA, (byte) 0x0B, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xE8, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x3B, (byte) 0x09, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xEA, (byte) 0x0B, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xE8, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFC, (byte) 0xAD, (byte) 0x5E, (byte) 0xA9, (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x1C, (byte) 0x79, (byte) 0x10, (byte) 0x21, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xAE, (byte) 0x5E, (byte) 0xA9, (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x42, (byte) 0xE7, (byte) 0xD0, (byte) 0x7D, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x0C, (byte) 0x5F, (byte) 0xA7, (byte) 0x68, (byte) 0x00, (byte) 0xAE, (byte) 0x5E, (byte) 0xA9, (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x4F, (byte) 0x41, (byte) 0x45, (byte) 0x57, (byte) 0x03, (byte) 0x6E, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0B, (byte) 0x00, (byte) 0x77, (byte) 0x65, (byte) 0x61, (byte) 0x70, (byte) 0x6F, (byte) 0x6E, (byte) 0x5F, (byte) 0x6E, (byte) 0x61, (byte) 0x6D, (byte) 0x65, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0E, (byte) 0x00, (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x61, (byte) 0x75, (byte) 0x6C, (byte) 0x74, (byte) 0x5F, (byte) 0x77, (byte) 0x65, (byte) 0x61, (byte) 0x70, (byte) 0x6F, (byte) 0x6E, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xE8, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x3F, (byte) 0x0A, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xA0, (byte) 0x40, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x00, (byte) 0x0C, (byte) 0x5F, (byte) 0xA7, (byte) 0x68, (byte) 0x00, (byte) 0xAE, (byte) 0x5E, (byte) 0xA9, (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x4F, (byte) 0x41, (byte) 0x45, (byte) 0x57, (byte) 0x06, (byte) 0x50, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0x00, (byte) 0x4E, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0B, (byte) 0x00, (byte) 0x77, (byte) 0x65, (byte) 0x61, (byte) 0x70, (byte) 0x6F, (byte) 0x6E, (byte) 0x5F, (byte) 0x6E, (byte) 0x61, (byte) 0x6D, (byte) 0x65, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0E, (byte) 0x00, (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x61, (byte) 0x75, (byte) 0x6C, (byte) 0x74, (byte) 0x5F, (byte) 0x77, (byte) 0x65, (byte) 0x61, (byte) 0x70, (byte) 0x6F, (byte) 0x6E, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
				
		};
		byte[] data2 = new byte[] {
				
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 				
		};
		return ByteBuffer.allocate(data1.length + data2.length).put(data1).put(data2);
	}
	
	public void setObjectId(long objectId) {
		this.objectId = objectId;
	}
	
	public long getObjectId() {
		return objectId;
	}
}
