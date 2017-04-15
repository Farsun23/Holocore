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
package network.packets.swg.zone.object_controller;

import java.nio.ByteBuffer;

import com.projectswg.common.data.location.Location;

public class DataTransform extends ObjectController {
	
	public static final int CRC = 0x0071;
	
	private int timestamp;
	private int updateCounter = 0;
	private Location l;
	private float speed = 0;
	private float lookAtYaw = 0;
	private boolean useLookAtYaw;
	
	public DataTransform(long objectId) {
		super(objectId, CRC);
	}
	
	public DataTransform(DataTransform transform) {
		super(transform.getObjectId(), CRC);
		timestamp = transform.getTimestamp();
		updateCounter = transform.getUpdateCounter();
		l = new Location(transform.getLocation());
		speed = transform.getSpeed();
		lookAtYaw = transform.getLookAtYaw();
		useLookAtYaw = transform.isUseLookAtYaw();
	}
	
	public DataTransform(long objectId, int counter, Location l, float speed) {
		super(objectId, CRC);
		if (l == null)
			l = new Location();
		this.l = l;
		this.speed = speed;
		this.updateCounter = counter;
	}
	
	public DataTransform(ByteBuffer data) {
		super(CRC);
		this.l = new Location();
		decode(data);
	}
	
	@Override
	public void decode(ByteBuffer data) {
		decodeHeader(data);
		timestamp = getInt(data);
		updateCounter = getInt(data);
		l = getEncodable(data, Location.class);
		speed = getFloat(data);
		lookAtYaw = getFloat(data);
		useLookAtYaw = getBoolean(data);
	}
	
	@Override
	public ByteBuffer encode() {
		ByteBuffer data = ByteBuffer.allocate(HEADER_LENGTH + 45);
		encodeHeader(data);
		addInt(data, timestamp);
		addInt(data, updateCounter);
		addEncodable(data, l);
		addFloat(data, speed);
		addFloat(data, lookAtYaw);
		addBoolean(data, useLookAtYaw);
		return data;
	}
	
	public void setTimestamp(int timestamp) {
		this.timestamp = timestamp;
	}
	
	public void setUpdateCounter(int counter) {
		this.updateCounter = counter;
	}
	
	public void setLocation(Location l) {
		this.l = l;
	}
	
	public void setSpeed(float speed) {
		this.speed = speed;
	}
	
	public int getUpdateCounter() {
		return updateCounter;
	}
	
	public Location getLocation() {
		return l;
	}
	
	public float getSpeed() {
		return speed;
	}
	
	public boolean isUseLookAtYaw() {
		return useLookAtYaw;
	}
	
	public void setUseLookAtYaw(boolean useLookAtYaw) {
		this.useLookAtYaw = useLookAtYaw;
	}
	
	public float getLookAtYaw() {
		return lookAtYaw;
	}
	
	public void setLookAtYaw(float lookAtYaw) {
		this.lookAtYaw = lookAtYaw;
	}
	
	public int getTimestamp() {
		return timestamp;
	}
	
	public byte getMovementAngle() {
		byte movementAngle = (byte) 0.0f;
		double wOrient = l.getOrientationW();
		double yOrient = l.getOrientationY();
		double sq = Math.sqrt(1 - (wOrient * wOrient));
		
		if (sq != 0) {
			if (l.getOrientationW() > 0 && l.getOrientationY() < 0) {
				wOrient *= -1;
				yOrient *= -1;
			}
			movementAngle = (byte) ((yOrient / sq) * (2 * Math.acos(wOrient) / 0.06283f));
		}
		
		return movementAngle;
	}
	
}
