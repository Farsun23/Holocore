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
package services.sui;

import com.projectswg.common.control.Service;
import com.projectswg.common.debug.Log;
import groovy.util.ResourceException;
import groovy.util.ScriptException;
import intents.network.GalacticPacketIntent;
import intents.sui.SuiWindowIntent;
import network.packets.Packet;
import network.packets.swg.SWGPacket;
import network.packets.swg.zone.server_ui.SuiCreatePageMessage;
import network.packets.swg.zone.server_ui.SuiEventNotification;
import network.packets.swg.zone.server_ui.SuiForceClosePage;
import resources.player.Player;
import resources.sui.ISuiCallback;
import resources.sui.SuiBaseWindow;
import resources.sui.SuiComponent;
import resources.sui.SuiEvent;
import utilities.Scripts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SuiService extends Service {

	private final Map<Long, List<SuiBaseWindow>> windows;

	public SuiService() {
		windows = new ConcurrentHashMap<>();
		
		registerForIntent(GalacticPacketIntent.class, gpi -> handleGalacticPacketIntent(gpi));
		registerForIntent(SuiWindowIntent.class, swi -> handleSuiWindowIntent(swi));
	}
	
	private void handleGalacticPacketIntent(GalacticPacketIntent gpi) {
		Packet p = gpi.getPacket();
		if (p instanceof SWGPacket)
			processSwgPacket(gpi.getPlayer(), (SWGPacket) p);
	}
	
	private void processSwgPacket(Player player, SWGPacket p) {
		switch(p.getPacketType()) {
			case SUI_EVENT_NOTIFICATION:
				if (p instanceof SuiEventNotification)
					handleSuiEventNotification(player, (SuiEventNotification) p);
				break;
			default:
				break;
		}
	}
	
	private void handleSuiWindowIntent(SuiWindowIntent swi) {
		switch(swi.getEvent()) {
			case NEW: displayWindow(swi.getPlayer(), swi.getWindow()); break;
			case CLOSE: {
				SuiBaseWindow window = swi.getWindow();
				if (window != null) closeWindow(swi.getPlayer(), swi.getWindow());
				else closeWindow(swi.getPlayer(), swi.getWindowId());
			} break;
			default: break;
		}
	}
	
	private void handleSuiEventNotification(Player player, SuiEventNotification p) {
		List<SuiBaseWindow> activeWindows = windows.get(player.getNetworkId());
		if (activeWindows == null || activeWindows.size() <= 0) {
			Log.w("There are no active windows for %s!", player);
			return;
		}

		SuiBaseWindow window = getWindowById(activeWindows, p.getWindowId());
		
		if (window == null) {
			Log.w("Received window ID %d that is not assigned to the player %s", p.getWindowId(), player);
			return;
		}

		SuiComponent component = window.getSubscriptionByIndex(p.getEventIndex());

		if (component == null) {
			Log.w("SuiWindow %s retrieved null subscription from supplied event index %d", window, p.getEventIndex());
			return;
		}

		List<String> suiSubscribedProperties = component.getSubscribedProperties();

		if (suiSubscribedProperties == null)
			return;

		List<String> eventNotificationProperties = p.getSubscribedToProperties();
		int eventPropertySize = eventNotificationProperties.size();

		if (suiSubscribedProperties.size() < eventPropertySize)
			return;

		String callback = component.getSubscribeToEventCallback();
		SuiEvent event = SuiEvent.valueOf(component.getSubscribedToEventType());

		Map<String, String> parameters = new HashMap<>();
		for (int i = 0; i < eventPropertySize; i++) {
			parameters.put(suiSubscribedProperties.get(i), eventNotificationProperties.get(i));
		}

		if (window.hasCallbackFunction(callback)) {
			String script = window.getCallbackScript(callback);
			try {
				Scripts.invoke(script, callback, player, player.getCreatureObject(), event, parameters);
			} catch (ResourceException | ScriptException e) {
				Log.e("Callback script %s not found", script);
			}
		} else if (window.hasJavaCallback(callback)) {
			ISuiCallback suiCallback = window.getJavaCallback(callback);
			suiCallback.handleEvent(player, player.getCreatureObject(), event, parameters);
		}

		// Both of these events "closes" the sui window for the client, so we have no need for the server to continue tracking the window.
		if (event == SuiEvent.OK_PRESSED || event == SuiEvent.CANCEL_PRESSED)
			activeWindows.remove(window);
	}
	
	private void displayWindow(Player player, SuiBaseWindow window) {
		int id = createWindowId();
		window.setId(id);

		SuiCreatePageMessage packet = new SuiCreatePageMessage(window);
		player.sendPacket(packet);

		long networkId = player.getNetworkId();
		List<SuiBaseWindow> activeWindows = windows.get(networkId);
		if (activeWindows == null) {
			activeWindows = new ArrayList<>();
			windows.put(networkId, activeWindows);
		}
		activeWindows.add(window);
	}

	private void closeWindow(Player player, SuiBaseWindow window) {
		int id = window.getId();

		List<SuiBaseWindow> activeWindows = windows.get(player.getNetworkId());

		if (activeWindows == null) {
			Log.w("Tried to close window id %d for player %s but it doesn't exist in the active windows.", id, player);
			return;
		}

		if (!activeWindows.remove(window)) {
			Log.w("Tried to close window id %d for player %s but it doesn't exist in the active windows.", id, player);
			return;
		}

		player.sendPacket(new SuiForceClosePage(id));
	}

	private void closeWindow(Player player, int windowId) {
		List<SuiBaseWindow> activeWindows = windows.get(player.getNetworkId());
		SuiBaseWindow window = activeWindows.get(windowId);
		if (window == null) {
			Log.w("Cannot close window with id %d as it doesn't exist in player %s active windows", windowId, player);
			return;
		}

		closeWindow(player, window);
	}

	private SuiBaseWindow getWindowById(List<SuiBaseWindow> windows, int id) {
		for (SuiBaseWindow window : windows) {
			if (window.getId() == id)
				return window;
		}
		return null;
	}

	public static int createWindowId() {
		return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
	}
}
