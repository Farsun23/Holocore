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
package resources.sui;

import intents.sui.SuiWindowIntent;
import intents.sui.SuiWindowIntent.SuiWindowEvent;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import resources.player.Player;
import network.packets.swg.zone.server_ui.SuiCreatePageMessage.SuiWindowComponent;

public class SuiWindow {

	private int id;
	private String script;
	private Player owner;
	private long rangeObjId;
	private float maxDistance = 0;
	private List<SuiWindowComponent> components = new ArrayList<SuiWindowComponent>();
	private Map<Integer, String> scriptCallbacks;
	private Map<Integer, ISuiCallback> javaCallbacks;
	
	public SuiWindow(String script, Player owner) {
		this.script = script;
		this.owner = owner;
	}

	public final void clearDataSource(String dataSource) {
		SuiWindowComponent component = createComponent((byte) 1);
		
		component.getNarrowParams().add(dataSource);
		components.add(component);
	}
	
	public final void addChildWidget(String property, String value) {
		SuiWindowComponent component = createComponent((byte) 2);
		
		addNarrowParams(component, property);
		
		component.getWideParams().add(value);
		components.add(component);
	}
	
	public final void setProperty(String property, String value) {
		SuiWindowComponent component = createComponent((byte) 3);
		
		addNarrowParams(component, property);
		
		component.getWideParams().add(value);
		components.add(component);
	}
	
	public final void addDataItem(String name, String value) {
		SuiWindowComponent component = createComponent((byte) 4);
		
		addNarrowParams(component, name);
		
		component.getWideParams().add(value);
		components.add(component);
	}
	
	private final void addCallbackComponent(String source, Trigger trigger, List<String> returnParams) {
		SuiWindowComponent component = createComponent((byte) 5);
		
		component.getNarrowParams().add(source);
		component.getNarrowParams().add(new String(new byte[] {trigger.getByte()}, Charset.forName("UTF-8")));
		component.getNarrowParams().add("handleSUI");
		
		for (String returnParam : returnParams) {
			addNarrowParams(component, returnParam);
		}
		
		components.add(component);
	}
	
	public final void addDataSource(String name, String value) {
		SuiWindowComponent component = createComponent((byte) 6);
		
		addNarrowParams(component, name);
		
		component.getWideParams().add(value);
		components.add(component);
	}
	
	public final void clearDataSourceContainer(String dataSource) {
		SuiWindowComponent component = createComponent((byte) 7);
		
		addNarrowParams(component, dataSource);
		
		components.add(component);
	}
	
	public final void addTableDataSource(String dataSource, String value) {
		SuiWindowComponent component = createComponent((byte) 8);
		
		addNarrowParams(component, dataSource);
		
		component.getWideParams().add(value);
		components.add(component);
	}
	
	public final void addCallback(int eventId, String source, Trigger trigger, List<String> returnParams, ISuiCallback callback) {
		addCallbackComponent(source, trigger, returnParams);
		
		if (javaCallbacks == null)
			javaCallbacks = new HashMap<Integer, ISuiCallback>();
		javaCallbacks.put(eventId, callback);
	}
	
	public final void addCallback(int eventId, String source, Trigger trigger, List<String> returnParams, String callbackScript) {
		addCallbackComponent(source, trigger, returnParams);
		
		if (scriptCallbacks == null)
			scriptCallbacks = new HashMap<Integer, String>();
		scriptCallbacks.put(eventId, callbackScript);
	}

	private SuiWindowComponent createComponent(byte type) {
		SuiWindowComponent component = new SuiWindowComponent();
		component.setType(type);
		
		return component;
	}
	
	private void addNarrowParams(SuiWindowComponent component, String property) {
		for (String s : property.split(":")) {
			component.getNarrowParams().add(s);
		}
	}
	
	public final void display() { new SuiWindowIntent(owner, this, SuiWindowEvent.NEW).broadcast(); }
	public final void display(Player player) { new SuiWindowIntent(player, this, SuiWindowEvent.NEW).broadcast();}
	
	public final long getRangeObjId() { return rangeObjId; }
	public final void setRangeObjId(long rangeObjId) { this.rangeObjId = rangeObjId; }
	public final int getId() { return id; }
	public final void setId(int id) { this.id = id; }
	public final String getScript() { return script; }
	public final Player getOwner() { return owner; }
	public final float getMaxDistance() { return maxDistance; }
	public final void setMaxDistance(float maxDistance) { this.maxDistance = maxDistance; }
	public final List<SuiWindowComponent> getComponents() { return components; }
	public final ISuiCallback getJavaCallback(int eventType) { return ((javaCallbacks == null) ? null : javaCallbacks.get(eventType)); }
	public final String getScriptCallback(int eventType) { return ((scriptCallbacks == null) ? null : scriptCallbacks.get(eventType)); }
	
	public enum Trigger {
		UPDATE	((byte) 4),
		OK		((byte) 9),
		CANCEL	((byte) 10);
		
		private byte b;
		
		Trigger(byte b) {
			this.b = b;
		}
		
		public byte getByte() {
			return b;
		}
	}
}
