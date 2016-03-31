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
package resources.encodables.player;

import network.packets.swg.zone.baselines.Baseline;
import resources.common.CRC;
import resources.encodables.Encodable;
import resources.network.NetBuffer;
import resources.objects.SWGObject;
import resources.objects.tangible.TangibleObject;
import resources.objects.weapon.WeaponObject;
import resources.player.Player;
import services.objects.ObjectCreator;

import java.io.Serializable;
import java.nio.ByteBuffer;

public class Equipment implements Encodable, Serializable {
	private static final long serialVersionUID = 1L;
	
	private TangibleObject 	weapon;
	private byte []			customizationString;
	private int				arrangementId;
	private long			objectId;
	private CRC				template;
	
	public Equipment() {
		this(0, null);
	}
	
	public Equipment(long objectId, String template) {
		this.objectId = objectId;
		this.template = new CRC(template);
		this.customizationString = new byte[0];
		this.arrangementId = 4;
		this.objectId = 0;
		this.weapon = null;
	}
	
	public Equipment(WeaponObject weapon) {
		this(weapon.getObjectId(), weapon.getTemplate());
		this.weapon = weapon;
	}
	
	@Override
	public byte [] encode() {
		byte [] weaponData = new byte[0];
		if (weapon != null)
			weaponData = getWeaponData();
		
		NetBuffer buffer = NetBuffer.allocate(19 + weaponData.length);
		
		buffer.addArray(customizationString); // TODO: Create encodable class for customization string
		buffer.addInt(arrangementId);
		buffer.addLong(objectId);
		buffer.addEncodable(template);
		buffer.addBoolean(weapon != null);
		buffer.addRawArray(weaponData);
		
		return buffer.array();
	}

	@Override
	public void decode(ByteBuffer bb) {
		NetBuffer data = NetBuffer.wrap(bb);
		customizationString	= data.getArray(); // TODO: Create encodable class for customization string
		arrangementId		= data.getInt();
		objectId			= data.getLong();
		template			= data.getEncodable(CRC.class);
		if (data.getBoolean())
			this.weapon = createWeaponFromData(data);
	}

	public byte[] getCustomizationString() {return customizationString;}
	public void setCustomizationString(byte[] customizationString) { this.customizationString = customizationString; }

	public int getArrangementId() { return arrangementId; }
	public void setArrangementId(int arrangementId) { this.arrangementId = arrangementId; }

	public long getObjectId() { return objectId; }
	public void setObjectId(long objectId) { this.objectId = objectId; }

	public String getTemplate() { return template.getString(); }
	public void setTemplate(String template) { this.template = new CRC(template); }

	private byte[] getWeaponData() {
		Player target = weapon.getOwner();
		ByteBuffer data3 = weapon.createBaseline3(target).encode();
		data3.position(0);

		ByteBuffer data6 = weapon.createBaseline6(target).encode();
		data6.position(0);
		
		ByteBuffer ret = ByteBuffer.allocate(data3.remaining() + data6.remaining());
		ret.put(data3);
		ret.put(data6);
		return ret.array();
	}
	
	private TangibleObject createWeaponFromData(NetBuffer data) {
		SWGObject weapon = ObjectCreator.createObjectFromTemplate(objectId, template.getString());
		
		Baseline b3 = new Baseline();
		b3.decode(data.getBuffer());
		Baseline b6 = new Baseline();
		b6.decode(data.getBuffer());
		
		weapon.parseBaseline(b3);
		weapon.parseBaseline(b6);
		if (weapon instanceof TangibleObject)
			return (TangibleObject) weapon;
		System.err.println("Unknown Equipment Type: " + weapon.getClass().getSimpleName());
		return null;
	}
	
	@Override
	public String toString() {
		if (weapon != null)
			return "Equipment: " + weapon;
		return "Equipment: " + template;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SWGObject))
			return super.equals(o);

		return ((SWGObject) o).getObjectId() == objectId;
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(objectId);
	}
}
