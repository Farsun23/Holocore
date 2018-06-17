/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
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
package com.projectswg.holocore.services.support.global.commands;

import com.projectswg.common.data.CRC;
import com.projectswg.common.data.combat.*;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.common.data.swgfile.visitors.DatatableData;
import com.projectswg.common.network.packets.SWGPacket;
import com.projectswg.common.network.packets.swg.zone.object_controller.CommandQueueEnqueue;
import com.projectswg.holocore.intents.support.global.network.InboundPacketIntent;
import com.projectswg.holocore.intents.support.global.zone.PlayerEventIntent;
import com.projectswg.holocore.intents.support.global.zone.PlayerTransformedIntent;
import com.projectswg.holocore.resources.support.data.config.ConfigFile;
import com.projectswg.holocore.resources.support.data.server_info.BasicLogStream;
import com.projectswg.holocore.resources.support.data.server_info.DataManager;
import com.projectswg.holocore.resources.support.global.commands.*;
import com.projectswg.holocore.resources.support.global.commands.CommandLauncher.EnqueuedCommand;
import com.projectswg.holocore.resources.support.global.commands.callbacks.*;
import com.projectswg.holocore.resources.support.global.commands.callbacks.admin.*;
import com.projectswg.holocore.resources.support.global.commands.callbacks.admin.qatool.CmdQaTool;
import com.projectswg.holocore.resources.support.global.commands.callbacks.chat.*;
import com.projectswg.holocore.resources.support.global.commands.callbacks.chat.friend.*;
import com.projectswg.holocore.resources.support.global.commands.callbacks.combat.CmdCoupDeGrace;
import com.projectswg.holocore.resources.support.global.commands.callbacks.combat.CmdDuel;
import com.projectswg.holocore.resources.support.global.commands.callbacks.combat.CmdEndDuel;
import com.projectswg.holocore.resources.support.global.commands.callbacks.combat.CmdPVP;
import com.projectswg.holocore.resources.support.global.commands.callbacks.flags.*;
import com.projectswg.holocore.resources.support.global.commands.callbacks.generic.*;
import com.projectswg.holocore.resources.support.global.commands.callbacks.group.*;
import com.projectswg.holocore.resources.support.global.commands.callbacks.survey.CmdRequestCoreSample;
import com.projectswg.holocore.resources.support.global.commands.callbacks.survey.CmdRequestSurvey;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.objects.swg.weapon.WeaponType;
import com.projectswg.holocore.services.support.objects.ObjectStorageService.ObjectLookup;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;
import me.joshlarson.jlcommon.log.Log;

import java.io.File;
import java.util.Locale;

public class CommandService extends Service {
	
	private final CommandContainer commandContainer;
	private final CommandLauncher	commandLauncher;
	private final BasicLogStream	commandLogger;
	
	public CommandService() {
		this.commandContainer = new CommandContainer();
		this.commandLauncher = new CommandLauncher();
		this.commandLogger = new BasicLogStream(new File("log/commands.txt"));
	}
	
	@Override
	public boolean initialize() {
		loadBaseCommands();
		loadCombatCommands();
		registerCallbacks();
		commandLauncher.start();
		return super.initialize();
	}
	
	@Override
	public boolean terminate() {
		commandLauncher.stop();
		return super.terminate();
	}
	
	@IntentHandler
	private void handleInboundPacketIntent(InboundPacketIntent gpi) {
		SWGPacket p = gpi.getPacket();
		if (p instanceof CommandQueueEnqueue) {
			CommandQueueEnqueue controller = (CommandQueueEnqueue) p;
			handleCommandRequest(gpi.getPlayer(), controller);
		}
	}
	
	@IntentHandler
	private void handlePlayerEventIntent(PlayerEventIntent pei) {
		switch (pei.getEvent()) {
			case PE_LOGGED_OUT:
				// No reason to keep their combat queue in the map if they log out
				// This also prevents queued commands from executing after the player logs out
				commandLauncher.removePlayerFromQueue(pei.getPlayer());
				break;
			default:
				break;
		}
	}
	
	@IntentHandler
	private void handlePlayerTransformedIntent(PlayerTransformedIntent pti) {
		CreatureObject creature = pti.getPlayer();
		
		if (creature.isPerforming()) {
			// A performer can transform while dancing...
			return;
		}
		
		commandLauncher.removePlayerFromQueue(creature.getOwner());
	}
	
	private void handleCommandRequest(Player player, CommandQueueEnqueue request) {
		if (!commandContainer.isCommand(request.getCommandCrc())) {
			if (request.getCommandCrc() != 0)
				Log.e("Invalid command crc: %x", request.getCommandCrc());
			return;
		}
		
		Command command = commandContainer.getCommand(request.getCommandCrc());
		// TODO target and target type checks below. Work with Set<TangibleObject> targets from there
		long targetId = request.getTargetId();
		SWGObject target = targetId != 0 ? ObjectLookup.getObjectById(targetId) : null;
		if (isCommandLogging())
			commandLogger.log("%-25s[from: %s, target: %s]", command.getName(), player.getCreatureObject().getObjectName(), target);
		
		EnqueuedCommand enqueued = new EnqueuedCommand(command, target, request);
		if (!command.getCooldownGroup().equals("defaultCooldownGroup") && command.isAddToCombatQueue()) {
			commandLauncher.addToQueue(player, enqueued);
		} else {
			// Execute it now
			commandLauncher.doCommand(player, enqueued);
		}
	}
	
	private boolean isCommandLogging() {
		return DataManager.getConfig(ConfigFile.DEBUG).getBoolean("COMMAND-LOGGING", true);
	}
	
	private void loadBaseCommands() {
		// First = Higher Priority, Last = Lower Priority ---- Some tables contain duplicates, ORDER MATTERS!
		String [] commandTables = new String[] {
				"command_table", "command_table_ground", "client_command_table",
				"command_table_space", "client_command_table_ground", "client_command_table_space"
		};
		
		commandContainer.clearCommands();
		for (String table : commandTables) {
			loadBaseCommands(table);
		}
	}
	
	private void loadBaseCommands(String table) {
		DatatableData baseCommands = (DatatableData) ClientFactory.getInfoFromFile("datatables/command/" + table + ".iff");
		
		int godLevel = baseCommands.getColumnFromName("godLevel");
		int cooldownGroup = baseCommands.getColumnFromName("cooldownGroup");
		int cooldownGroup2 = baseCommands.getColumnFromName("cooldownGroup2");
		int cooldownTime = baseCommands.getColumnFromName("cooldownTime");
		int cooldownTime2 = baseCommands.getColumnFromName("cooldownTime2");
		int targetType = baseCommands.getColumnFromName("targetType");
		
		for (int row = 0; row < baseCommands.getRowCount(); row++) {
			Object[] cmdRow = baseCommands.getRow(row);
			String commandName = ((String) cmdRow[0]).toLowerCase(Locale.ENGLISH);
			if (commandContainer.isCommand(commandName))
				continue; // skip duplicates - first is higher priority
			
			Command command = new Command(commandName);
			
			switch ((int) cmdRow[1]) {
				case 0:
					command.setDefaultPriority(DefaultPriority.IMMEDIATE);
					break;
				case 1:
					command.setDefaultPriority(DefaultPriority.FRONT);
					break;
				case 2:
				default:
					command.setDefaultPriority(DefaultPriority.NORMAL);
					break;
			}
			command.setDefaultTime((float) cmdRow[6]);
			command.setCharacterAbility((String) cmdRow[7]);
			command.setCombatCommand(false);
			command.setCooldownGroup((String) cmdRow[cooldownGroup]);
			command.setCooldownGroup2((String) cmdRow[cooldownGroup2]);
			command.setCooldownTime((float) cmdRow[cooldownTime]);
			command.setCooldownTime2((float) cmdRow[cooldownTime2]);
			// e(none=0,required=1,optional=2)[none]
			switch ((int) cmdRow[targetType]) {
				case 0:
				default:
					command.setTargetType(TargetType.NONE);
					break;
				case 1:
					command.setTargetType(TargetType.REQUIRED);
					break;
				case 2:
					command.setTargetType(TargetType.OPTIONAL);
					break;
				case 3:
					command.setTargetType(TargetType.LOCATION);
				case 4:
					command.setTargetType(TargetType.ALL);
					break;
			}
			
			// Ziggy: The amount of columns in the table seems to change for each row
			if (cmdRow.length >= 83) {
				Object addToCombatQueue = cmdRow[82];
				
				// Ziggy: Sometimes this column contains a String... uwot SOE?
				if (addToCombatQueue instanceof Boolean) {
					command.setAddToCombatQueue((Boolean) addToCombatQueue);
				}
			}
			
			if (godLevel >= 0) {
				command.setGodLevel((int) cmdRow[godLevel]);
			}
			
			commandContainer.addCommand(command);
		}
	}
	
	private CombatCommand createAsCombatCommand(Command c) {
		CombatCommand cc = new CombatCommand(c.getName());
		cc.setDefaultTime(c.getDefaultTime());
		cc.setCharacterAbility(c.getCharacterAbility());
		cc.setGodLevel(c.getGodLevel());
		cc.setCombatCommand(true);
		cc.setCooldownGroup(c.getCooldownGroup());
		cc.setCooldownGroup2(c.getCooldownGroup2());
		cc.setCooldownTime(c.getCooldownTime());
		cc.setCooldownTime2(c.getCooldownTime2());
		cc.setMaxRange(c.getMaxRange());
		cc.setTargetType(c.getTargetType());
		return cc;
	}
	
	private void loadCombatCommands() {
		DatatableData combatCommands = (DatatableData) ClientFactory.getInfoFromFile("datatables/combat/combat_data.iff");
		int validTarget = combatCommands.getColumnFromName("validTarget");
		int forceCombat = combatCommands.getColumnFromName("forcesCharacterIntoCombat");
		int attackType = combatCommands.getColumnFromName("attackType");
		int healthCost = combatCommands.getColumnFromName("healthCost");
		int actionCost = combatCommands.getColumnFromName("actionCost");
		int damageType = combatCommands.getColumnFromName("damageType");
		int ignoreDistance = combatCommands.getColumnFromName("ignore_distance");
		int pvpOnly = combatCommands.getColumnFromName("pvp_only");
		int attackRolls = combatCommands.getColumnFromName("attack_rolls");
		int animDefault = combatCommands.getColumnFromName("animDefault");
		int percentAddFromWeapon = combatCommands.getColumnFromName("percentAddFromWeapon");
		int addedDamage = combatCommands.getColumnFromName("addedDamage");
		int buffNameTarget = combatCommands.getColumnFromName("buffNameTarget");
		int buffNameSelf = combatCommands.getColumnFromName("buffNameSelf");
		int maxRange = combatCommands.getColumnFromName("maxRange");
		int hitType = combatCommands.getColumnFromName("hitType");
		int delayAttackEggTemplate = combatCommands.getColumnFromName("delayAttackEggTemplate");
		int delayAttackParticle = combatCommands.getColumnFromName("delayAttackParticle");
		int initialDelayAttackInterval = combatCommands.getColumnFromName("initialDelayAttackInterval");
		int delayAttackInterval = combatCommands.getColumnFromName("delayAttackInterval");
		int delayAttackLoops = combatCommands.getColumnFromName("delayAttackLoops");
		int delayAttackEggPosition = combatCommands.getColumnFromName("delayAttackEggPosition");
		int coneLength = combatCommands.getColumnFromName("coneLength");
		int healAttrib = combatCommands.getColumnFromName("healAttrib");
		// animDefault anim_unarmed anim_onehandmelee anim_twohandmelee anim_polearm
		// anim_pistol anim_lightRifle anim_carbine anim_rifle anim_heavyweapon
		// anim_thrown anim_onehandlightsaber anim_twohandlightsaber anim_polearmlightsaber
		for (int row = 0; row < combatCommands.getRowCount(); row++) {
			Object[] cmdRow = combatCommands.getRow(row);
			
			Command c = commandContainer.getCommand(CRC.getCrc(((String) cmdRow[0]).toLowerCase(Locale.ENGLISH)));
			if (c == null)
				continue;
			CombatCommand cc = createAsCombatCommand(c);
			commandContainer.removeCommand(c);
			cc.setValidTarget(ValidTarget.getValidTarget((Integer) cmdRow[validTarget]));
			cc.setForceCombat(((int) cmdRow[forceCombat]) != 0);
			cc.setAttackType(AttackType.getAttackType((Integer) cmdRow[attackType]));
			cc.setHealthCost((float) cmdRow[healthCost]);
			cc.setActionCost((float) cmdRow[actionCost]);
			cc.setDamageType(DamageType.getDamageType((Integer) cmdRow[damageType]));
			cc.setIgnoreDistance(((int) cmdRow[ignoreDistance]) != 0);
			cc.setPvpOnly(((int) cmdRow[pvpOnly]) != 0);
			cc.setAttackRolls((int) cmdRow[attackRolls]);
			cc.setDefaultAnimation(getAnimationList((String) cmdRow[animDefault]));
			cc.setPercentAddFromWeapon((float) cmdRow[percentAddFromWeapon]);
			cc.setAddedDamage((int) cmdRow[addedDamage]);
			cc.setBuffNameTarget((String) cmdRow[buffNameTarget]);
			cc.setBuffNameSelf((String) cmdRow[buffNameSelf]);
			cc.setMaxRange((float) cmdRow[maxRange]);
			cc.setHitType(HitType.getHitType((Integer) cmdRow[hitType]));
			cc.setDelayAttackEggTemplate((String) cmdRow[delayAttackEggTemplate]);
			cc.setDelayAttackParticle((String) cmdRow[delayAttackParticle]);
			cc.setInitialDelayAttackInterval((float) cmdRow[initialDelayAttackInterval]);
			cc.setDelayAttackInterval((float) cmdRow[delayAttackInterval]);
			cc.setDelayAttackLoops((int) cmdRow[delayAttackLoops]);
			cc.setEggPosition(DelayAttackEggPosition.getEggPosition((int) cmdRow[delayAttackEggPosition]));
			cc.setConeLength((float) cmdRow[coneLength]);
			cc.setHealAttrib(HealAttrib.getHealAttrib((Integer) cmdRow[healAttrib]));
			cc.setAnimations(WeaponType.UNARMED, getAnimationList((String) cmdRow[animDefault + 1]));
			cc.setAnimations(WeaponType.ONE_HANDED_MELEE, getAnimationList((String) cmdRow[animDefault + 2]));
			cc.setAnimations(WeaponType.TWO_HANDED_MELEE, getAnimationList((String) cmdRow[animDefault + 3]));
			cc.setAnimations(WeaponType.POLEARM_MELEE, getAnimationList((String) cmdRow[animDefault + 4]));
			cc.setAnimations(WeaponType.POLEARM_MELEE, getAnimationList((String) cmdRow[animDefault + 5]));
			cc.setAnimations(WeaponType.PISTOL, getAnimationList((String) cmdRow[animDefault + 6]));
			cc.setAnimations(WeaponType.LIGHT_RIFLE, getAnimationList((String) cmdRow[animDefault + 7]));
			cc.setAnimations(WeaponType.CARBINE, getAnimationList((String) cmdRow[animDefault + 8]));
			cc.setAnimations(WeaponType.RIFLE, getAnimationList((String) cmdRow[animDefault + 9]));
			cc.setAnimations(WeaponType.THROWN, getAnimationList((String) cmdRow[animDefault + 10]));
			cc.setAnimations(WeaponType.ONE_HANDED_SABER, getAnimationList((String) cmdRow[animDefault + 11]));
			cc.setAnimations(WeaponType.TWO_HANDED_SABER, getAnimationList((String) cmdRow[animDefault + 12]));
			cc.setAnimations(WeaponType.POLEARM_SABER, getAnimationList((String) cmdRow[animDefault + 13]));
			commandContainer.addCommand(cc);
		}
	}
	
	private String[] getAnimationList(String cell) {
		if (cell.isEmpty())
			return new String[0];
		return cell.split(",");
	}
	
	private void registerCallback(String commandName, ICmdCallback callback) {
		commandName = commandName.toLowerCase(Locale.ENGLISH);
		Command command = commandContainer.getCommand(commandName);
		assert command != null;
		command.setJavaCallback(callback);
	}
	
	private void registerCallbacks() {
		registerCallback("waypoint", new WaypointCmdCallback());
		registerCallback("requestWaypointAtPosition", new RequestWaypointCmdCallback());
		registerCallback("getAttributesBatch", new AttributesCmdCallback());
		registerCallback("socialInternal", new SocialInternalCmdCallback());
		registerCallback("sitServer", new SitOnObjectCmdCallback());
		registerCallback("stand", new StandCmdCallback());
		registerCallback("teleport", new AdminTeleportCallback());
		registerCallback("prone", new ProneCmdCallback());
		registerCallback("kneel", new KneelCmdCallback());
		registerCallback("jumpServer", new JumpCmdCallback());
		registerCallback("serverDestroyObject", new ServerDestroyObjectCmdCallback());
		registerCallback("findFriend", new FindFriendCallback());

		registerCallback("startDance", new StartDanceCallback());
		registerCallback("requestBiography", new RequestBiographyCmdCallback());
		registerCallback("flourish", new FlourishCmdCallback());
		registerCallback("changeDance", new ChangeDanceCallback());
		registerCallback("transferItemMisc", new TransferItemCallback());
		registerCallback("transferItemArmor", new TransferItemCallback());
		registerCallback("transferItemWeapon", new TransferItemCallback());
		registerCallback("logout", new LogoutCmdCallback());
		registerCallback("requestDraftSlots", new RequestDraftSlotsCallback());
		
		addAdminScripts();
		addChatScripts();
		addCombatScripts();
		addFlagScripts();
		addGenericScripts();
		addGroupScripts();
		addSurveyScripts();
	}
	
	private void addAdminScripts() {
		registerCallback("dumpZoneInformation", new CmdDumpZoneInformation());
		registerCallback("goto", new CmdGoto());
		registerCallback("qatool", new CmdQaTool());
		registerCallback("revertPlayerAppearance", new CmdRevertPlayerAppearance());
		registerCallback("setGodMode", new CmdSetGodMode());
		registerCallback("setPlayerAppearance", new CmdSetPlayerAppearance());
		registerCallback("server", new CmdServer());
		
		registerCallback("createStaticItem", new CmdCreateStaticItem());
		registerCallback("credits", new CmdMoney());
		registerCallback("setSpeed", new CmdSetSpeed());
	}
	
	private void addChatScripts() {
		registerCallback("broadcast", new CmdBroadcast());
		registerCallback("broadcastArea", new CmdBroadcastArea());
		registerCallback("broadcastGalaxy", new CmdBroadcastGalaxy());
		registerCallback("broadcastPlanet", new CmdBroadcastPlanet());
		registerCallback("planet", new CmdPlanetChat());
		registerCallback("spatialChatInternal", new CmdSpatialChatInternal());
		addChatFriendScripts();
	}
	
	private void addChatFriendScripts() {
		registerCallback("addFriend", new CmdAddFriend());
		registerCallback("addIgnore", new CmdAddIgnore());
		registerCallback("getFriendList", new CmdGetFriendList());
		registerCallback("removeFriend", new CmdRemoveFriend());
		registerCallback("removeIgnore", new CmdRemoveIgnore());
	}
	
	private void addCombatScripts() {
		registerCallback("coupDeGrace", new CmdCoupDeGrace());
		registerCallback("deathBlow", new CmdCoupDeGrace());
		registerCallback("duel", new CmdDuel());
		registerCallback("endDuel", new CmdEndDuel());
		registerCallback("pvp", new CmdPVP());
	}
	
	private void addFlagScripts() {
		registerCallback("toggleAwayFromKeyBoard", new CmdToggleAwayFromKeyboard());
		registerCallback("toggleDisplayingFactionRank", new CmdToggleDisplayingFactionRank());
		registerCallback("newbiehelper", new CmdToggleHelper());
		registerCallback("lfg", new CmdToggleLookingForGroup());
		registerCallback("lfw", new CmdToggleLookingForWork());
		registerCallback("ooc", new CmdToggleOutOfCharacter());
		registerCallback("rolePlay", new CmdToggleRolePlay());
	}
	
	private void addGenericScripts() {
		registerCallback("grantSkill", new CmdGrantSkill());
		registerCallback("stopDance", new CmdStopDance());
		registerCallback("stopwatching", new CmdStopWatching());
		registerCallback("watch", new CmdWatch());
		registerCallback("openContainer", new CmdOpenContainer());
		registerCallback("purchaseTicket", new CmdPurchaseTicket());
		registerCallback("setBiography", new CmdSetBiography());
		registerCallback("setCurrentSkillTitle", new CmdSetCurrentSkillTitle());
		registerCallback("setMoodInternal", new CmdSetMoodInternal());
		registerCallback("setWaypointActiveStatus", new CmdSetWaypointActiveStatus());
		registerCallback("setWaypointName", new CmdSetWaypointName());
	}
	
	private void addGroupScripts() {
		registerCallback("groupChat", new CmdGroupChat());
		registerCallback("decline", new CmdGroupDecline());
		registerCallback("disband", new CmdGroupDisband());
		registerCallback("invite", new CmdGroupInvite());
		registerCallback("join", new CmdGroupJoin());
		registerCallback("dismissGroupMember", new CmdGroupKick());
		registerCallback("leaveGroup", new CmdGroupLeave());
		registerCallback("groupLoot", new CmdGroupLootSet());
		registerCallback("makeLeader", new CmdGroupMakeLeader());
		registerCallback("makeMasterLooter", new CmdGroupMakeMasterLooter());
		registerCallback("uninvite", new CmdGroupUninvite());
	}
	
	private void addSurveyScripts() {
		registerCallback("requestCoreSample", new CmdRequestCoreSample());
		registerCallback("requestSurvey", new CmdRequestSurvey());
	}
	
}
