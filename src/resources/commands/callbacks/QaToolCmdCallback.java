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

package resources.commands.callbacks;

import intents.chat.ChatBroadcastIntent;
import intents.experience.ExperienceIntent;
import intents.network.CloseConnectionIntent;
import intents.object.CreateStaticItemIntent;
import intents.object.DestroyObjectIntent;
import intents.object.ObjectTeleportIntent;
import intents.player.DeleteCharacterIntent;

import java.io.FileNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import resources.Location;
import resources.Terrain;
import resources.commands.ICmdCallback;
import resources.network.DisconnectReason;
import resources.objects.SWGObject;
import resources.objects.building.BuildingObject;
import resources.objects.cell.CellObject;
import resources.objects.creature.CreatureObject;
import resources.player.Player;
import resources.server_info.Log;
import resources.sui.ISuiCallback;
import resources.sui.SuiButtons;
import resources.sui.SuiEvent;
import resources.sui.SuiInputBox;
import resources.sui.SuiListBox;
import resources.sui.SuiMessageBox;
import services.galaxy.GalacticManager;
import services.objects.ObjectManager;
import services.objects.StaticItemService.ObjectCreationHandler;
import services.player.PlayerManager;
import utilities.Scripts;

import com.projectswg.common.info.RelationalServerData;
import com.projectswg.common.info.RelationalServerFactory;

/**
 * Created by Waverunner on 8/19/2015
 */
public class QaToolCmdCallback implements ICmdCallback {
	private static final String TITLE = "QA Tool";
	private static final String PROMPT = "Select the action that you would like to do";
	
	private GalacticManager galacticManager;
	
	@Override
	public void execute(GalacticManager galacticManager, Player player, SWGObject target, String args) {
		if (this.galacticManager == null)
			this.galacticManager = galacticManager;
		
		if (args != null && !args.isEmpty()) {
			String[] command = args.split(" ");
			
			switch (command[0]) {
				case "item":
					if (command.length > 1)
						handleCreateItem(player, command[1]);
					else
						displayItemCreator(player);
					break;
				case "help":
					displayHelp(player);
					break;
				case "force-delete":
					forceDelete(galacticManager.getObjectManager(), player, target);
					break;
				case "recover":
					recoverPlayer(galacticManager.getObjectManager(), galacticManager.getPlayerManager(), player, args.substring(args.indexOf(' ') + 1));
					break;
				case "details":
					try {
						Scripts.invoke("commands/helper/qatool/details", "sendDetails", player, target, args.split(" "));
					} catch (FileNotFoundException ex) {
						Log.e("sendDetails qatool script not found!");
					}
					break;
				case "xp":
					if(command.length == 3)
						grantXp(player, command[1], command[2]);
					else
						sendSystemMessage(player, "QATool XP: Expected format: /qatool xp <xpType> <xpGained>");
					break;
				default:
					displayMainWindow(player);
					break;
			}
		} else {
			displayMainWindow(player);
		}
		Log.i("%s has accessed the QA Tool", player.getUsername());
	}
	
	/* Windows */
	
	private void displayMainWindow(Player player) {
		SuiListBox window = new SuiListBox(SuiButtons.OK_CANCEL, TITLE, PROMPT);
		window.addListItem("Item Creator");
		
		window.addCallback("handleQaTool", new QaListBoxSuiCallback());
		window.display(player);
	}
	
	private void displayItemCreator(Player creator) {
		SuiInputBox inputBox = new SuiInputBox(SuiButtons.OK_CANCEL, "Item Creator", "Enter the name of the item you wish to create");
		inputBox.addOkButtonCallback("handleCreateItem", (player, actor, event, parameters) -> handleCreateItem(player, SuiInputBox.getEnteredText(parameters)));
		inputBox.addCancelButtonCallback("displayMainWindow", (player, actor, event, parameters) -> displayMainWindow(player));
		inputBox.display(creator);
	}
	
	/* Handlers */
	
	private void handleCreateItem(Player player, String itemName) {
		CreatureObject creature = player.getCreatureObject();
		if (creature == null)
			return;
		
		SWGObject inventory = creature.getSlottedObject("inventory");
		if (inventory == null)
			return;

		Log.i("%s attempted to create item %s", player, itemName);
		new CreateStaticItemIntent(creature, inventory, new ObjectCreationHandler() {
			@Override
			public void success(SWGObject[] createdObjects) {
				new ChatBroadcastIntent(player, "@system_msg:give_item_success").broadcast();
			}

			@Override
			public void containerFull() {
				new ChatBroadcastIntent(player, "@system_msg:give_item_failure").broadcast();
			}

			@Override
			public boolean isIgnoreVolume() {
				return false;
			}
		}, itemName).broadcast();
	}
	
	private void forceDelete(final ObjectManager objManager, final Player player, final SWGObject target) {
		SuiMessageBox inputBox = new SuiMessageBox(SuiButtons.OK_CANCEL, "Force Delete?", "Are you sure you want to delete this object?");
		inputBox.addOkButtonCallback("handleDeleteObject", (caller, actor, event, parameters) -> {
			if (target instanceof CreatureObject && ((CreatureObject) target).isPlayer()) {
				Log.i("[%s] Requested deletion of character: %s", player.getUsername(), target.getObjectName());
				new DeleteCharacterIntent((CreatureObject) target).broadcast();
				Player owner = target.getOwner();
				if (owner != null)
					new CloseConnectionIntent(owner.getNetworkId(), DisconnectReason.APPLICATION).broadcast();
				return;
			}
			Log.i("[%s] Requested deletion of object: %s", player.getUsername(), target);
			if (target != null) {
				new DestroyObjectIntent(target).broadcast();
			}
		});
		inputBox.display(player);
	}
	
	private void recoverPlayer(ObjectManager objManager, PlayerManager playerManager, Player player, String args) {
		String name = args;
		String[] nameParts = name.split(" ");
		String loc = "";
		if (nameParts.length == 2) {
			name = nameParts[0];
			loc = nameParts[1];
		} else if (nameParts.length == 3) {
			name = nameParts[0] + " " + nameParts[1];
			loc = nameParts[1];
		} else {
			sendSystemMessage(player, "Invalid arguments! Expected <playername> [opt]<terrain>");
		}
		name = name.trim();
		recoverPlayer(objManager, playerManager, player, name, loc);
	}
	
	private void recoverPlayer(ObjectManager objManager, PlayerManager playerManager, Player player, String name, String loc) {
		Player recoveree = playerManager.getPlayerByCreatureFirstName(name);
		
		if (recoveree == null) {
			sendSystemMessage(player, "Could not find player by first name: '" + name + "'");
			return;
		}
		
		sendSystemMessage(player, teleportToRecoveryLocation(objManager, recoveree.getCreatureObject(), loc));
	}
	
	private String teleportToRecoveryLocation(ObjectManager objManager, SWGObject obj, String loc) {
		final String whereClause = "(player_spawns.id = ?) AND (player_spawns.building_id = '' OR buildings.building_id = player_spawns.building_id)";
		try (RelationalServerData data = RelationalServerFactory.getServerData("player/player_spawns.db", "building/buildings", "player_spawns")) {
			try (ResultSet set = data.selectFromTable("player_spawns, buildings", new String[] { "player_spawns.*", "buildings.object_id" }, whereClause, loc)) {
				if (!set.next())
					return "No such location found: " + loc;
				return teleportToRecovery(objManager, obj, loc, set);
			} catch (SQLException e) {
				Log.e(e);
				return "Exception thrown. Failed to teleport: [" + e.getErrorCode() + "] " + e.getMessage();
			}
		}
	}
	
	private String teleportToRecovery(ObjectManager objManager, SWGObject obj, String loc, ResultSet set) throws SQLException {
		String building = set.getString("building_id");
		Terrain t = Terrain.getTerrainFromName(set.getString("terrain"));
		Location l = new Location(set.getDouble("x"), set.getDouble("y"), set.getDouble("z"), t);
		if (building.isEmpty())
			new ObjectTeleportIntent(obj, l).broadcast();
		else
			return teleportToRecoveryBuilding(objManager, obj, set.getLong("object_id"), set.getString("cell"), l);
		return "Sucessfully teleported " + obj.getObjectName() + " to " + loc;
	}
	
	private String teleportToRecoveryBuilding(ObjectManager objManager, SWGObject obj, long buildingId, String cellName, Location l) {
		SWGObject parent = objManager.getObjectById(buildingId);
		if (parent == null || !(parent instanceof BuildingObject)) {
			String err = String.format("Invalid parent! Either null or not a building: %s  BUID: %d", parent, buildingId);
			Log.e(err);
			return err;
		}
		CellObject cell = ((BuildingObject) parent).getCellByName(cellName);
		if (cell == null) {
			String err = String.format("Invalid cell! Cell does not exist: %s  B-Template: %s  BUID: %d", cellName, parent.getTemplate(), buildingId);
			Log.e(err);
			return err;
		}
		new ObjectTeleportIntent(obj, cell, l).broadcast();
		return "Successfully teleported " + obj.getObjectName() + " to " + buildingId + "/" + cellName + " " + l;
	}
	
	private void displayHelp(Player player) {
		String prompt = "The following are acceptable arguments that can be used as shortcuts to the various QA tools:\n" + "item <template> -- Generates a new item and adds it to your inventory, not providing template parameter will display Item Creator window\n" + "help -- Displays this window\n";
		createMessageBox(player, "QA Tool - Help", prompt);
	}
	
	private void grantXp(Player player, String xpType, String xpGainedArg) {
		try {
			int xpGained = Integer.valueOf(xpGainedArg);
			new ExperienceIntent(player.getCreatureObject(), xpType, xpGained).broadcast();
			Log.i("XP command: %s gave themselves %d %s XP", player.getUsername(), xpGained, xpType);
		} catch (NumberFormatException e) {
			sendSystemMessage(player, String.format("XP command: %s is not a number", xpGainedArg));
			Log.e("XP command: %s gave a non-numerical XP gained argument of %s", player.getUsername(), xpGainedArg);
		}
	}
	
	/* Utility Methods */
	
	private void createMessageBox(Player player, String title, String prompt) {
		new SuiMessageBox(SuiButtons.OK, title, prompt).display(player);
	}
	
	private void sendSystemMessage(Player player, String message) {
		new ChatBroadcastIntent(player, message).broadcast();
	}
	
	/* Callbacks */
	
	private class QaListBoxSuiCallback implements ISuiCallback {
		@Override
		public void handleEvent(Player player, SWGObject actor, SuiEvent event, Map<String, String> parameters) {
			if (event != SuiEvent.OK_PRESSED)
				return;
			
			int selection = SuiListBox.getSelectedRow(parameters);
			
			switch (selection) {
				case 0:
					displayItemCreator(player);
					break;
				default:
					break;
			}
		}
	}
}
