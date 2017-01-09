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
package services.galaxy;

import java.util.Map;

import intents.network.ConnectionClosedIntent;
import intents.network.ConnectionOpenedIntent;
import intents.network.GalacticPacketIntent;
import intents.network.InboundPacketIntent;
import resources.control.Assert;
import resources.control.Intent;
import resources.control.Manager;
import resources.player.Player;
import resources.server_info.SynchronizedMap;
import services.CoreManager;
import services.chat.ChatManager;
import services.dev.DeveloperService;
import services.galaxy.travel.TravelService;
import services.objects.ObjectManager;
import services.objects.UniformBoxService;
import services.player.PlayerManager;
import utilities.IntentChain;

public class GalacticManager extends Manager {
	
	private final ObjectManager objectManager;
	private final PlayerManager playerManager;
	private final GameManager gameManager;
	private final ChatManager chatManager;
	private final TravelService travelService;
	private final DeveloperService developerService;
	private final UniformBoxService uniformBox;
	private final Map<Long, IntentChain> prevIntentMap;
	
	public GalacticManager() {
		objectManager = new ObjectManager();
		playerManager = new PlayerManager();
		gameManager = new GameManager();
		chatManager = new ChatManager();
		travelService = new TravelService();
		developerService = new DeveloperService();
		uniformBox = new UniformBoxService();
		prevIntentMap = new SynchronizedMap<>();
		
		addChildService(objectManager);
		addChildService(playerManager);
		addChildService(gameManager);
		addChildService(chatManager);
		addChildService(travelService);
		addChildService(developerService);
		addChildService(uniformBox);
		
		registerForIntent(InboundPacketIntent.TYPE);
		registerForIntent(ConnectionOpenedIntent.TYPE);
		registerForIntent(ConnectionClosedIntent.TYPE);
	}
	
	@Override
	public boolean initialize() {
		resetPopulationCount();
		return super.initialize();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		if (i instanceof InboundPacketIntent) {
			Player player = playerManager.getPlayerFromNetworkId(((InboundPacketIntent) i).getNetworkId());
			Assert.notNull(player);
			GalacticPacketIntent g = new GalacticPacketIntent(((InboundPacketIntent) i).getPacket(), player);
			g.setGalacticManager(this);
			prevIntentMap.get(player.getNetworkId()).broadcastAfter(g);
		} else if (i instanceof ConnectionClosedIntent) {
			prevIntentMap.remove(((ConnectionClosedIntent) i).getNetworkId()).reset();
		} else if (i instanceof ConnectionOpenedIntent) {
			IntentChain chain = new IntentChain();
			chain.waitUntilComplete(i);
			prevIntentMap.put(((ConnectionOpenedIntent) i).getNetworkId(), chain);
		}
	}
	
	public ObjectManager getObjectManager() {
		return objectManager;
	}
	
	public PlayerManager getPlayerManager() {
		return playerManager;
	}
	
	private void resetPopulationCount() {
		CoreManager.getGalaxy().setPopulation(0);
	}
	
}
