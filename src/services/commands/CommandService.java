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
package services.commands;

import intents.chat.ChatBroadcastIntent;
import intents.chat.ChatCommandIntent;
import intents.network.GalacticPacketIntent;


import network.packets.Packet;
import network.packets.swg.zone.object_controller.CommandQueueEnqueue;
import resources.client_info.ClientFactory;
import resources.client_info.visitors.DatatableData;
import resources.commands.Command;
import resources.commands.ICmdCallback;
import resources.commands.callbacks.*;
import resources.common.CRC;
import resources.control.Intent;
import resources.control.Service;
import resources.encodables.ProsePackage;
import resources.encodables.StringId;
import resources.objects.SWGObject;
import resources.player.AccessLevel;
import resources.player.Player;
import resources.server_info.Log;
import utilities.Scripts;
import services.galaxy.GalacticManager;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class CommandService extends Service {
	
	private final Map <Integer, Command>			commands;			// NOTE: CRC's are all lowercased for commands!
	private final Map <String, Integer>				commandCrcLookup;
	private final Map <String, List<Command>>		commandByScript;
	
	public CommandService() {
		commands = new HashMap<>();
		commandCrcLookup = new HashMap<>();
		commandByScript = new HashMap<>();
	}
	
	@Override
	public boolean initialize() {
		registerForIntent(GalacticPacketIntent.TYPE);
		loadBaseCommands();
		registerCallbacks();
		return super.initialize();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		if (i instanceof GalacticPacketIntent) {
			GalacticPacketIntent gpi = (GalacticPacketIntent) i;
			Packet p = gpi.getPacket();
			Player player = gpi.getPlayerManager().getPlayerFromNetworkId(gpi.getNetworkId());
			if (player != null) {
				if (p instanceof CommandQueueEnqueue) {
					CommandQueueEnqueue controller = (CommandQueueEnqueue) p;
					handleCommandRequest(player, gpi.getGalacticManager(), controller);
				}
			}
		}
	}
	
	private void handleCommandRequest(Player player, GalacticManager galacticManager, CommandQueueEnqueue request) {
		if (!commandExists(request.getCommandCrc())) {
			Log.e("CommandService", "Invalid command crc: %x", request.getCommandCrc());
			return;
		}
		
		Command command = getCommand(request.getCommandCrc());
		String [] arguments = request.getArguments().split(" ");
		SWGObject target = null;
		if (request.getTargetId() != 0) {
			target = galacticManager.getObjectManager().getObjectById(request.getTargetId());
		}
		
		executeCommand(galacticManager, player, command, target, request.getArguments());
		new ChatCommandIntent(request.getTargetId(), request.getCommandCrc(), arguments).broadcast();
	}
	
	private void executeCommand(GalacticManager galacticManager, Player player, Command command, SWGObject target, String args) {
		if (player.getCreatureObject() == null) {
			Log.e("CommandService", "No creature object associated with the player '%s'!", player.getUsername());
			return;
		}

		if(player.getAccessLevel().getValue() < command.getGodLevel()) {
			String commandAccessLevel = AccessLevel.getFromValue(command.getGodLevel()).toString();
			String playerAccessLevel = player.getAccessLevel().toString();
			Log.i("CommandService", "[%s] attempted to use the command \"%s\", but did not have the minimum access level. Access Level Required: %s, Player Access Level: %s",
					player.getCharacterName(), command.getName(), commandAccessLevel, playerAccessLevel);
			String errorProseString1 = "use that command";
			String errorProseString2 = commandAccessLevel.toString();
			new ChatBroadcastIntent(player, new ProsePackage("StringId", new StringId("cmd_err", "state_must_have_prose"), "TO", errorProseString1, "TU", errorProseString2)).broadcast();
			return;
		}

		if(!command.getCharacterAbility().isEmpty() && !player.getCreatureObject().hasAbility(command.getCharacterAbility())){
			Log.i("CommandService", "[%s] attempted to use the command \"%s\", but did not have the required ability. Ability Required: %s",
					player.getCharacterName(), command.getName(), command.getCharacterAbility());
			String errorProseString = String.format("use the %s command", command.getName());
			new ChatBroadcastIntent(player, new ProsePackage("StringId", new StringId("cmd_err", "ability_prose"), "TO", errorProseString)).broadcast();
			return;
		}

		// TODO: Check if the player has the ability
		// TODO: Cool-down checks
		// TODO: Handle for different target
		// TODO: Handle for different targetType
		
		if (command.hasJavaCallback()) {
			try {
				((ICmdCallback) command.getJavaCallback().newInstance()).execute(galacticManager, player, target, args);
			} catch (InstantiationException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		else
			Scripts.invoke("commands/generic/" + command.getDefaultScriptCallback(), "execute", galacticManager, player, target, args);
	}
	
	private void loadBaseCommands() {
		final String [] commandTables = new String [] {"command_table", "client_command_table", "command_table_ground"};
		
		clearCommands();
		for (String table : commandTables) {
			loadBaseCommands(table);
		}
	}
	
	private void loadBaseCommands(String table) {
		DatatableData baseCommands = (DatatableData) ClientFactory.getInfoFromFile("datatables/command/"+table+".iff");

		int godLevel = baseCommands.getColumnFromName("godLevel");
		for (int row = 0; row < baseCommands.getRowCount(); row++) {
			Object [] cmdRow = baseCommands.getRow(row);

			Command command = new Command((String) cmdRow[0]);
			command.setCrc(CRC.getCrc(command.getName().toLowerCase(Locale.ENGLISH)));
			command.setScriptHook((String) cmdRow[2]);
			command.setCppHook((String)cmdRow[4]);
			command.setDefaultTime((float) cmdRow[6]);
			command.setCharacterAbility((String) cmdRow[7]);

			if(godLevel >= 0){
				command.setGodLevel((int) cmdRow[godLevel]);
			}

			addCommand(command);
		}
	}
	
	private <T extends ICmdCallback> Command registerCallback(String command, Class<T> callback) {
		Command comand = getCommand(command);
		registerCallback(comand, callback);
		return comand;
	}

	private <T extends ICmdCallback> void registerCallback(Command command, Class<T> callback) {
		try {
			if (callback.getConstructor() == null)
				throw new IllegalArgumentException("Incorrectly registered callback class. Class must extend ICmdCallback and have an empty constructor: " + callback.getName());
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		command.setJavaCallback(callback);

		List<Command> scriptCommands = getCommandsByScript(command.getDefaultScriptCallback());
		for(Command unregistered : scriptCommands){
			if(unregistered != command && !unregistered.hasJavaCallback()){
				registerCallback(unregistered, command.getJavaCallback());
			}
		}

	}
	
	private void registerCallbacks() {

		registerCallback("waypoint", WaypointCmdCallback.class);
		registerCallback("requestWaypointAtPosition", RequestWaypointCmdCallback.class);
		registerCallback("server", ServerCmdCallback.class);
		registerCallback("getAttributesBatch", AttributesCmdCallback.class);
		registerCallback("socialInternal", SocialInternalCmdCallback.class);
		registerCallback("sitServer", SitOnObjectCmdCallback.class);
		registerCallback("stand", StandCmdCallback.class);
		registerCallback("teleport", AdminTeleportCallback.class);
		registerCallback("prone", ProneCmdCallback.class);
		registerCallback("kneel", KneelCmdCallback.class);
		registerCallback("jumpServer", JumpCmdCallback.class);
		registerCallback("serverDestroyObject", ServerDestroyObjectCmdCallback.class);
		registerCallback("findFriend", FindFriendCallback.class);
		registerCallback("setPlayerAppearance", PlayerAppearanceCallback.class);
		registerCallback("revertPlayerAppearance", RevertAppearanceCallback.class);
		registerCallback("qatool", QaToolCmdCallback.class);
		registerCallback("goto", GotoCmdCallback.class);
	}
	
	private void clearCommands() {
		synchronized (commands) {
			commands.clear();
		}
		synchronized (commandCrcLookup) {
			commandCrcLookup.clear();
		}
	}
	
	private Command getCommand(String name) {
		synchronized (commandCrcLookup) {
			return getCommand(commandCrcLookup.get(name));
		}
	}

	private List<Command> getCommandsByScript(String script) {
		synchronized (commandByScript){
			return commandByScript.get(script);
		}
	}
	
	private Command getCommand(int crc) {
		synchronized (commands) {
			return commands.get(crc);
		}
	}
	
	private boolean commandExists(int crc) {
		synchronized (commands) {
			return commands.containsKey(crc);
		}
	}
	
	private void addCommand(Command command) {
		synchronized (commands) {
			commands.put(command.getCrc(), command);
		}
		synchronized (commandCrcLookup) {
			commandCrcLookup.put(command.getName(), command.getCrc());
		}
		synchronized (commandByScript){
			String script = command.getDefaultScriptCallback();
			List<Command> commands = commandByScript.get(script);

			if(commands == null){
				commands = new LinkedList<Command>();
				commandByScript.put(script, commands);
			}
			commands.add(command);
		}
	}
	
}
