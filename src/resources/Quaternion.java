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
import resources.network.NetBufferStream;
import resources.persistable.Persistable;

import java.nio.ByteBuffer;

public class Quaternion implements Encodable, Persistable {
	
	private double x;
	private double y;
	private double z;
	private double w;
	
	public Quaternion(Quaternion q) {
		this(q.x, q.y, q.z, q.w);
	}
	
	public Quaternion(double x, double y, double z, double w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
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

	public double getW() {
		return w;
	}
	
	public double getYaw() {
		return Math.toDegrees(Math.atan2(y*y, w*w));
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

	public void setW(double w) {
		this.w = w;
	}
	
	public void set(double x, double y, double z, double w) {
		setX(x);
		setY(y);
		setZ(z);
		setW(w);
	}
	
	public void set(Quaternion q) {
		set(q.x, q.y, q.z, q.w);
	}
	
	public void setHeading(double degrees) {
		set(0, 0, 0, 1);
		rotateHeading(degrees);
	}
	
	public void rotateHeading(double degrees) {
		rotateDegrees(degrees, 0, 1, 0);
	}
	
	public void rotateDegrees(double degrees, double axisX, double axisY, double axisZ) {
		double rad = Math.toRadians(degrees) / 2;
		double sin = Math.sin(rad);
		w = Math.cos(rad);
		x = sin * axisX;
		y = sin * axisY;
		z = sin * axisZ;
		normalize();
	}
	
	public void rotateByQuaternion(Quaternion q) {
		double nW = w * q.w - x * q.x - y * q.y - z * q.z;
		double nX = w * q.x + x * q.w + y * q.z - z * q.y;
		double nY = w * q.y + y * q.w + z * q.x - x * q.z;
		double nZ = w * q.z + z * q.w + x * q.y - y * q.x;
		set(nX, nY, nZ, nW);
		normalize();
	}
	
	public void normalize() {
		double mag = Math.sqrt(x * x + y * y + z * z + w * w);
		x /= mag;
		y /= mag;
		z /= mag;
		w /= mag;
	}

	@Override
	public byte[] encode() {
		ByteBuffer bb = ByteBuffer.allocate(16);
		Packet.addFloat(bb, (float) x);
		Packet.addFloat(bb, (float) y);
		Packet.addFloat(bb, (float) z);
		Packet.addFloat(bb, (float) w);
		return bb.array();
	}

	@Override
	public void decode(ByteBuffer data) {
		x = Packet.getFloat(data);
		y = Packet.getFloat(data);
		z = Packet.getFloat(data);
		w = Packet.getFloat(data);
	}
	
	@Override
	public void save(NetBufferStream stream) {
		stream.addFloat((float) x);
		stream.addFloat((float) y);
		stream.addFloat((float) z);
		stream.addFloat((float) w);
	}
	
	@Override
	public void read(NetBufferStream stream) {
		x = stream.getFloat();
		y = stream.getFloat();
		z = stream.getFloat();
		w = stream.getFloat();
	}

	public String toString() {
		return String.format("Quaternion[%.3f, %.3f, %.3f, %.3f]", x, y, z, w);
	}
}
