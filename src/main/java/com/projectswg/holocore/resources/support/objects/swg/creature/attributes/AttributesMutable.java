/***********************************************************************************
 * Copyright (c) 2019 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/

package com.projectswg.holocore.resources.support.objects.swg.creature.attributes;

import com.projectswg.common.data.encodables.mongo.MongoData;
import com.projectswg.common.data.encodables.mongo.MongoPersistable;
import com.projectswg.common.encoding.Encodable;
import com.projectswg.common.network.NetBuffer;
import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.persistable.Persistable;
import com.projectswg.holocore.resources.support.data.collections.SWGList;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;

public class AttributesMutable implements Attributes, Encodable, Persistable, MongoPersistable {
	
	/*
	0 - health
	1 - health regen
	2 - action
	3 - action regen
	4 - mind
	5 - mind regen
	 */
	
	private final SWGObject obj;
	private SWGList<Integer> ham;
	
	public AttributesMutable(SWGObject obj, int type, int update) {
		this.obj = obj;
		this.ham = new SWGList<>(type, update);
		for (int i = 0; i < 6; i++)
			ham.add(0);
		ham.clearDeltaQueue();
	}
	
	@Override
	public final synchronized int getHealth() {
		return ham.get(0);
	}
	
	public final synchronized void setHealth(int health) {
		if (getHealth() == health)
			return;
		ham.set(0, health);
		ham.sendDeltaMessage(obj);
	}
	
	public final synchronized void modifyHealth(int health, int max) {
		setHealth(Math.max(0, Math.min(max, getHealth()+health)));
	}
	
	@Override
	public final synchronized int getHealthRegen() {
		return ham.get(1);
	}
	
	public final synchronized void setHealthRegen(int healthRegen) {
		if (getHealthRegen() == healthRegen)
			return;
		ham.set(1, healthRegen);
		ham.sendDeltaMessage(obj);
	}
	
	public final synchronized void modifyHealthRegen(int healthRegen, int max) {
		setHealthRegen(Math.max(0, Math.min(max, getHealthRegen()+healthRegen)));
	}
	
	@Override
	public final synchronized int getAction() {
		return ham.get(2);
	}
	
	public final synchronized void setAction(int action) {
		if (getAction() == action)
			return;
		ham.set(2, action);
		ham.sendDeltaMessage(obj);
	}
	
	public final synchronized void modifyAction(int action, int max) {
		setAction(Math.max(0, Math.min(max, getAction()+action)));
	}
	
	@Override
	public final synchronized int getActionRegen() {
		return ham.get(3);
	}
	
	public final synchronized void setActionRegen(int actionRegen) {
		if (getActionRegen() == actionRegen)
			return;
		ham.set(3, actionRegen);
		ham.sendDeltaMessage(obj);
	}
	
	public final synchronized void modifyActionRegen(int actionRegen, int max) {
		setActionRegen(Math.max(0, Math.min(max, getActionRegen()+actionRegen)));
	}
	
	@Override
	public final synchronized int getMind() {
		return ham.get(4);
	}
	
	public final synchronized void setMind(int mind) {
		if (getMind() == mind)
			return;
		ham.set(4, mind);
		ham.sendDeltaMessage(obj);
	}
	
	public final synchronized void modifyMind(int mind, int max) {
		setMind(Math.max(0, Math.min(max, getMind()+mind)));
	}
	
	@Override
	public final synchronized int getMindRegen() {
		return ham.get(5);
	}
	
	public final synchronized void setMindRegen(int mindRegen) {
		if (getMindRegen() == mindRegen)
			return;
		ham.set(5, mindRegen);
		ham.sendDeltaMessage(obj);
	}
	
	public final synchronized void modifyMindRegen(int mindRegen, int max) {
		setMindRegen(Math.max(0, Math.min(max, getMindRegen()+mindRegen)));
	}
	
	@Override
	public Attributes getImmutable() {
		return new AttributesImmutable(this);
	}
	
	@Override
	public void readMongo(MongoData data) {
		setHealth(data.getInteger("health", 0));
		setHealthRegen(data.getInteger("healthRegen", 0));
		setAction(data.getInteger("action", 0));
		setActionRegen(data.getInteger("actionRegen", 0));
		setMind(data.getInteger("mind", 0));
		setMindRegen(data.getInteger("mindRegen", 0));
	}
	
	@Override
	public void saveMongo(MongoData data) {
		data.putInteger("health", getHealth());
		data.putInteger("healthRegen", getHealthRegen());
		data.putInteger("action", getAction());
		data.putInteger("actionRegen", getActionRegen());
		data.putInteger("mind", getMind());
		data.putInteger("mindRegen", getMindRegen());
	}
	
	@Override
	public void read(NetBufferStream stream) {
		stream.getByte();
		setHealth(stream.getInt());
		setHealthRegen(stream.getInt());
		setAction(stream.getInt());
		setActionRegen(stream.getInt());
		setMind(stream.getInt());
		setMindRegen(stream.getInt());
	}
	
	@Override
	public void save(NetBufferStream stream) {
		stream.addByte(0);
		stream.addInt(getHealth());
		stream.addInt(getHealthRegen());
		stream.addInt(getAction());
		stream.addInt(getActionRegen());
		stream.addInt(getMind());
		stream.addInt(getMindRegen());
	}
	
	@Override
	public void decode(NetBuffer data) {
		SWGList<Integer> hamDecoded = SWGList.getSwgList(data, 0, 0, Integer.class);
		setHealth(hamDecoded.get(0));
		setHealthRegen(hamDecoded.get(1));
		setAction(hamDecoded.get(2));
		setActionRegen(hamDecoded.get(3));
		setMind(hamDecoded.get(4));
		setMindRegen(hamDecoded.get(5));
	}
	
	@Override
	public byte[] encode() {
		return ham.encode();
	}
	
	@Override
	public int getLength() {
		return ham.getLength();
	}
	
}
