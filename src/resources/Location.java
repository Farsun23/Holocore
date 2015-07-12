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

import java.io.Serializable;


public class Location implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private Terrain terrain = null;
	
	private double x = Double.NaN;
	private double y = Double.NaN;
	private double z = Double.NaN;
	
	private double oX = Double.NaN;
	private double oY = Double.NaN;
	private double oZ = Double.NaN;
	private double oW = Double.NaN;
	
	public Location() {
		
	}
	
	public Location(double x, double y, double z, Terrain terrain) {
		this.terrain = terrain;
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public Location(Location l) {
		this.terrain = l.terrain;
		this.x = l.x;
		this.y = l.y;
		this.z = l.z;
		this.oX = l.oX;
		this.oY = l.oY;
		this.oZ = l.oZ;
		this.oW = l.oW;
	}
	
	public void setTerrain(Terrain terrain) { this.terrain = terrain; }
	public void setX(double x) { this.x = x; }
	public void setY(double y) { this.y = y; }
	public void setZ(double z) { this.z = z; }
	public void setOrientationX(double oX) { this.oX = oX; }
	public void setOrientationY(double oY) { this.oY = oY; }
	public void setOrientationZ(double oZ) { this.oZ = oZ; }
	public void setOrientationW(double oW) { this.oW = oW; }
	public void setPosition(double x, double y, double z) {
		setX(x);
		setY(y);
		setZ(z);
	}
	public void setOrientation(double oX, double oY, double oZ, double oW) {
		setOrientationX(oX);
		setOrientationY(oY);
		setOrientationZ(oZ);
		setOrientationW(oW);
	}
	
	public Terrain getTerrain() { return terrain; }
	public double getX() { return x; }
	public double getY() { return y; }
	public double getZ() { return z; }
	public double getOrientationX() { return oX; }
	public double getOrientationY() { return oY; }
	public double getOrientationZ() { return oZ; }
	public double getOrientationW() { return oW; }
	
	public boolean isWithinDistance(Location l, double x, double y, double z) {
		double xD = Math.abs(this.x - l.getX());
		double yD = Math.abs(this.y - l.getY());
		double zD = Math.abs(this.z - l.getZ());
		return xD <= x && yD <= y && zD <= z;
	}
	
	public void translatePosition(double x, double y, double z) {
		this.x += x;
		this.y += y;
		this.z += z;
	}
	
	public void translateOrientation(double oX, double oY, double oZ, double oW) {
		this.oX += oX;
		this.oY += oY;
		this.oZ += oZ;
		this.oW += oW;
	}
	
	public Location translate(double x, double y, double z) {
		Location loc = new Location(this);
		loc.translatePosition(x, y, z);
		return loc;
	}
	
	public Location translate(Location l) {
		Location ret = new Location(this);
		double k0 = l.oW * l.oW - 0.5f;
		double k1;
		double rx, ry, rz;
		
		// k1 = Q.V
		k1 = x * l.oX;
		k1 += y * l.oY;
		k1 += z * l.oZ;
		
		// (qq-1/2)V+(Q.V)Q
		rx = x * k0 + l.oX * k1;
		ry = y * k0 + l.oY * k1;
		rz = z * k0 + l.oZ * k1;
		
		// (Q.V)Q+(qq-1/2)V+q(QxV)
		rx += l.oW * (l.oY * z - l.oZ * y);
		ry += l.oW * (z * x - l.oX * z);
		rz += l.oW * (l.oX * y - l.oY * x);
		
		//  2((Q.V)Q+(qq-1/2)V+q(QxV))
		rx += rx;
		ry += ry;
		rz += rz;
		ret.setPosition(rx, ry, rz);
		ret.translatePosition(l.x, l.y, l.z);
		ret.setOrientationW(l.oW*oW - l.oX*oX - l.oY*oY - l.oZ*oZ);
		ret.setOrientationX(l.oW*oX + l.oX*oW + l.oY*oZ - l.oZ*oY);
		ret.setOrientationY(l.oW*oY + l.oY*oW + l.oZ*oX - l.oX*oZ);
		ret.setOrientationZ(l.oW*oZ + l.oZ*oW + l.oX*oY - l.oY*oX);
		return ret;
	}
	
	/**
	 * Sets the orientation to be facing the specified heading
	 * @param heading the heading to face, in degrees
	 */
	public void setHeading(double heading) {
		setOrientation(0, 0, 0, 1);
		rotateHeading(heading);
	}
	
	/**
	 * Rotates the orientation by the specified angle along the Y-axis
	 * @param angle the angle to rotate by in degrees
	 */
	public void rotateHeading(double angle) {
		rotate(angle, 0, 1, 0);
	}
	
	/**
	 * Rotates the orientation by the specified angle along the specified axises
	 * @param angle the angle to rotate by in degrees
	 * @param axisX the amount of rotation about the x-axis
	 * @param axisY the amount of rotation about the x-axis
	 * @param axisZ the amount of rotation about the x-axis
	 */
	public void rotate(double angle, double axisX, double axisY, double axisZ) {
		double sin = Math.sin(Math.toRadians(angle) / 2);
		oW = Math.cos(Math.toRadians(angle) / 2);
		oX = sin * axisX;
		oY = sin * axisY;
		oZ = sin * axisZ;
		// Normalize
		double mag = Math.sqrt(oW*oW + oX*oX + oY*oY + oZ*oZ);
		oW /= mag;
		oX /= mag;
		oY /= mag;
		oZ /= mag;
	}
	
	public boolean mergeWith(Location l) {
		boolean changed = false;
		if (terrain == null || terrain != l.getTerrain()) {
			terrain = l.getTerrain();
			changed = true;
		}
		changed = mergeLocation(l.getX(), l.getY(), l.getZ()) || true;
		changed = mergeOrientation(l) || true;
		return changed;
	}
	
	public boolean mergeLocation(double lX, double lY, double lZ) {
		boolean changed = false;
		if (Double.isNaN(x) || x != lX) {
			x = lX;
			changed = true;
		}
		if (Double.isNaN(y) || y != lY) {
			y = lY;
			changed = true;
		}
		if (Double.isNaN(z) || z != lZ) {
			z = lZ;
			changed = true;
		}
		return changed;
	}
	
	private boolean mergeOrientation(Location l) {
		boolean changed = false;
		if (!Double.isNaN(l.getOrientationX()) && (Double.isNaN(oX) || oX != l.getOrientationX())) {
			oX = l.getOrientationX();
			changed = true;
		}
		if (!Double.isNaN(l.getOrientationY()) && (Double.isNaN(oY) || oY != l.getOrientationY())) {
			oY = l.getOrientationY();
			changed = true;
		}
		if (!Double.isNaN(l.getOrientationZ()) && (Double.isNaN(oZ) || oZ != l.getOrientationZ())) {
			oZ = l.getOrientationZ();
			changed = true;
		}
		if (!Double.isNaN(l.getOrientationW()) && (Double.isNaN(oW) || oW != l.getOrientationW())) {
			oW = l.getOrientationW();
			changed = true;
		}
		return changed;
	}
	
	public double getSpeed(Location l, double deltaTime) {
		double dist = Math.sqrt(square(x-l.x) + square(y-l.y) + square(z-l.z));
		return dist / deltaTime;
	}
	
	public double getYaw() {
		double w = oW;
		double y = oY;
		double angle = 0;
		
		if (w * w + y * y > 0) {
			if (w > 0 && y < 0)
				w *= -1;
			angle = 2 * Math.acos(w);
		} else {
			angle = 0;
		}
		
		return angle;
	}
	
	private double square(double x) {
		return x*x;
	}
	
	public boolean isNaN() {
		return Double.isNaN(x) && Double.isNaN(y) && Double.isNaN(z) && Double.isNaN(oX) && Double.isNaN(oY) && Double.isNaN(oZ) && Double.isNaN(oW);
	}
	
	public boolean equals(Location l) {
		if (terrain != l.terrain)
			return false;
		if (!isEqual(l.x, x))
			return false;
		if (!isEqual(l.y, y))
			return false;
		if (!isEqual(l.z, z))
			return false;
		if (!isEqual(l.oX, oX))
			return false;
		if (!isEqual(l.oY, oY))
			return false;
		if (!isEqual(l.oZ, oZ))
			return false;
		if (!isEqual(l.oW, oW))
			return false;
		return true;
	}
	
	public int hashCode() {
		return hash(x)*13 + hash(y)*17 + hash(z)*19 + hash(oX)*23 + hash(oY)*29 + hash(oZ)*31 + hash(oW)*37;
	}
	
	private int hash(double x) {
		long v = Double.doubleToLongBits(x);
		return (int)(v^(v>>>32));
	}
	
	private boolean isEqual(double x, double y) {
		if (Double.isNaN(x))
			return Double.isNaN(y);
		if (Double.isNaN(y))
			return false;
		return x == y;
	}
	
	public String toString() {
		return "Location[TRN=" + terrain + ", POS=(" + x + ", " + y + ", " + z + ") ORTN=(" + oX + ", " + oY + ", " + oZ + ", " + oW + ")]";
	}
}
