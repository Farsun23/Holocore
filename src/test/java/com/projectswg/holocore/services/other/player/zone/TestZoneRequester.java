/************************************************************************************
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
package com.projectswg.holocore.services.other.player.zone;

import com.projectswg.common.network.packets.SWGPacket;
import com.projectswg.common.network.packets.swg.ErrorMessage;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.global.zone.ZoneRequester;
import com.projectswg.holocore.resources.support.objects.swg.player.PlayerObject;
import com.projectswg.holocore.runners.TestRunnerNoIntents;
import com.projectswg.holocore.test_resources.GenericCreatureObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class TestZoneRequester extends TestRunnerNoIntents {
	
	@Test
	public void testNullCreatureObject() {
		ZoneRequester zr = new ZoneRequester();
		TZRPlayer player = new TZRPlayer();
		Assert.assertFalse(zr.onZoneRequested(null, player, 0));
		Assert.assertTrue(player.isSentError());
	}
	
	@Test
	public void testInvalidCreatureObject() {
		ZoneRequester zr = new ZoneRequester();
		TZRPlayer player = new TZRPlayer();
		Assert.assertFalse(zr.onZoneRequested(new PlayerObject(1), player, 0));
		Assert.assertTrue(player.isSentError());
	}
	
	@Test
	public void testNullPlayerObject() {
		ZoneRequester zr = new ZoneRequester();
		TZRPlayer player = new TZRPlayer();
		GenericCreatureObject creature = new GenericCreatureObject(5);
		creature.clearSlot("ghost");
		Assert.assertFalse(zr.onZoneRequested(creature, player, 5));
		Assert.assertTrue(player.isSentError());
	}
	
	@Test
	public void testValidCreatureObject() {
		ZoneRequester zr = new ZoneRequester();
		TZRPlayer player = new TZRPlayer();
		GenericCreatureObject creature = new GenericCreatureObject(5);
		creature.setOwner(null);
		Assert.assertTrue(zr.onZoneRequested(creature, player, 5));
		Assert.assertFalse(player.isSentError());
	}
	
	private static class TZRPlayer extends Player {
		
		private final AtomicBoolean sentError = new AtomicBoolean(false);
		
		@Override
		public void sendPacket(SWGPacket ... packets) {
			if (packets.length == 1 && packets[0] instanceof ErrorMessage)
				sentError.set(true);
		}
		
		public boolean isSentError() {
			return sentError.get();
		}
		
	}
	
}
