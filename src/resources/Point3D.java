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
package resources;

import network.packets.Packet;
import resources.encodables.Encodable;

import java.io.Serializable;
import java.nio.ByteBuffer;

public class Point3D implements Encodable, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private double x;
	private double y;
	private double z;
	
	public Point3D() {
		this(0, 0, 0);
	}

	public Point3D(Point3D p) {
		this(p.getX(), p.getY(), p.getZ());
	}
	
	public Point3D(double x, double y, double z) {
		set(x, y, z);
	}
	
	public double getX() {
		return x;
	}
	
	public double getY() {
		return y;
	}
	
	public double getZ() {
		return z;
	}
	
	public void setX(double x) {
		this.x = x;
	}
	
	public void setY(double y) {
		this.y = y;
	}
	
	public void setZ(double z) {
		this.z = z;
	}
	
	public void set(double x, double y, double z) {
		setX(x);
		setY(y);
		setZ(z);
	}
	
	public void rotateAround(double x, double y, double z, Quaternion rot) {
		double oX = rot.getX();
		double oY = rot.getY();
		double oZ = rot.getZ();
		double oW = rot.getW();
		double nX = x + oW*oW*getX() + 2*oY*oW*getZ() - 2*oZ*oW*getY() + oX*oX*getX() + 2*oY*oX*getY() + 2*oZ*oX*getZ() - oZ*oZ*getX() - oY*oY*getX();
		double nY = y + 2*oX*oY*getX() + oY*oY*getY() + 2*oZ*oY*getZ() + 2*oW*oZ*getX() - oZ*oZ*getY() + oW*oW*getY() - 2*oX*oW*getZ() - oX*oX*getY();
		double nZ = z + 2*oX*oZ*getX() + 2*oY*oZ*getY() + oZ*oZ*getZ() - 2*oW*oY*getX() - oY*oY*getZ() + 2*oW*oX*getY() - oX*oX*getZ() + oW*oW*getZ();
		set(nX, nY, nZ);
	}


	@Override
	public byte[] encode() {
		ByteBuffer bb = ByteBuffer.allocate(12);
		Packet.addFloat(bb, (float) x);
		Packet.addFloat(bb, (float) y);
		Packet.addFloat(bb, (float) z);
		return bb.array();
	}

	@Override
	public void decode(ByteBuffer data) {
		x = Packet.getFloat(data);
		y = Packet.getFloat(data);
		z = Packet.getFloat(data);
	}

	public String toString() {
		return String.format("Point3D[%.2f, %.2f, %.2f]", x, y, z);
	}
}
