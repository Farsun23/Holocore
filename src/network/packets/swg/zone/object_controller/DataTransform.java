package network.packets.swg.zone.object_controller;

import java.nio.ByteBuffer;

import resources.Location;


public class DataTransform extends ObjectController {
	
	public static final int CRC = 0x0071;
	
	private int updateCounter = 0;
	private Location l;
	private float speed = 0;
	
	public DataTransform(long objectId, int counter, Location l, float speed) {
		super(objectId, CRC);
		if (l == null)
			l = new Location();
		this.l = l;
	}
	
	public DataTransform(ByteBuffer data) {
		super(CRC);
		this.l = new Location();
		decode(data);
	}
	
	public void decode(ByteBuffer data) {
		decodeHeader(data);
		getInt(data);
		updateCounter = getInt(data);
		l.setOrientationX(getFloat(data));
		l.setOrientationY(getFloat(data));
		l.setOrientationZ(getFloat(data));
		l.setOrientationW(getFloat(data));
		l.setX(getFloat(data));
		l.setY(getFloat(data));
		l.setZ(getFloat(data));
		speed = getFloat(data);
	}
	
	public ByteBuffer encode() {
		ByteBuffer data = ByteBuffer.allocate(HEADER_LENGTH + 36);
		encodeHeader(data);
		addInt(data, updateCounter);
		addLocation(data, l);
		addFloat(data, speed);
		return data;
	}
	
	public void setUpdateCounter(int counter) { this.updateCounter = counter; }
	public void setLocation(Location l) { this.l = l; }
	public void setSpeed(float speed) { this.speed = speed; }
	
	public int getUpdateCounter() { return updateCounter; }
	public Location getLocation() { return l; }
	public float getSpeed() { return speed; }
	
	public double getMovementAngle() {
		byte movementAngle = (byte) 0.0f;
		double wOrient = l.getOrientationW();
		double yOrient = l.getOrientationY();
		double sq = Math.sqrt(1 - (wOrient*wOrient));
		
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
