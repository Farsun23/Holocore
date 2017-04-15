/*******************************************************************************
 * Copyright (c) 2015 /// Project SWG /// www.projectswg.com
 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies.
 * Our goal is to create an emulator which will provide a server for players to
 * continue playing a game similar to the one they used to play. We are basing
 * it on the final publish of the game prior to end-game events.
 *
 * This file is part of Holocore.
 *
 * --------------------------------------------------------------------------------
 *
 * Holocore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Holocore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/

package services.group;

import java.nio.ByteBuffer;

import com.projectswg.common.encoding.Encodable;

import network.packets.Packet;
import resources.player.Player;

/**
 * Created by Waverunner on 10/4/2015
 */
public class GroupInviterData implements Encodable {
	
	private long id;
	private Player sender;
	private String name;
	private long counter;
	
	public GroupInviterData() {
		this(0, null, null, 0);
	}
	
	public GroupInviterData(long id, Player sender, String name, long counter) {
		this.id = id;
		this.sender = sender;
		this.name = name;
		this.counter = counter;
	}

	@Override
	public byte[] encode() {
		ByteBuffer bb = ByteBuffer.allocate(name.length() + 18);

		Packet.addLong(bb, id);
		Packet.addAscii(bb, name);
		Packet.addLong(bb, counter);

		return bb.array();
	}

	@Override
	public void decode(ByteBuffer data) {
		id = Packet.getLong(data);
		name = Packet.getAscii(data);
		counter = Packet.getLong(data);
	}


	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getCounter() {
		return counter;
	}

	public void incrementCounter() {
		counter++;
	}

	public Player getSender() {
		return sender;
	}

	public void setSender(Player sender) {
		this.sender = sender;
	}
}
