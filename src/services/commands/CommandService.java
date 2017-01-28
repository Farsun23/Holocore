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

import intents.PlayerEventIntent;
import intents.chat.ChatBroadcastIntent;
import intents.chat.ChatCommandIntent;
import intents.network.GalacticPacketIntent;
import intents.player.PlayerTransformedIntent;
import java.io.FileNotFoundException;
import network.packets.Packet;
import network.packets.swg.zone.object_controller.CommandQueueDequeue;
import network.packets.swg.zone.object_controller.CommandQueueEnqueue;
import resources.client_info.ClientFactory;
import resources.client_info.visitors.DatatableData;
import resources.combat.AttackType;
import resources.combat.DamageType;
import resources.combat.ValidTarget;
import resources.commands.CombatCommand;
import resources.commands.Command;
import resources.commands.ICmdCallback;
import resources.commands.callbacks.*;
import resources.common.CRC;
import resources.control.Intent;
import resources.control.Service;
import resources.encodables.ProsePackage;
import resources.encodables.StringId;
import resources.objects.SWGObject;
import resources.objects.weapon.WeaponType;
import resources.player.AccessLevel;
import resources.player.Player;
import resources.server_info.Log;
import utilities.Scripts;
import services.galaxy.GalacticManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import network.packets.swg.zone.object_controller.CommandTimer;
import resources.combat.DelayAttackEggPosition;
import resources.combat.HitType;
import resources.commands.DefaultPriority;
import resources.objects.creature.CreatureObject;
import resources.server_info.SynchronizedMap;
import utilities.ThreadUtilities;


public class CommandService extends Service {
	
	private final Map <Integer, Command>			commands;			// NOTE: CRC's are all lowercased for commands!
	private final Map <String, Integer>				commandCrcLookup;
	private final Map <String, List<Command>>		commandByScript;
	private final Map <Player, BlockingQueue<QueuedCommand>>		combatQueueMap;
	private final Map <CreatureObject, Set<String>>			cooldownMap;
	private final ScheduledExecutorService executorService;
	
	public CommandService() {
		commands = new HashMap<>();
		commandCrcLookup = new HashMap<>();
		commandByScript = new HashMap<>();
		combatQueueMap = new SynchronizedMap<>();
		cooldownMap = new HashMap<>();
		executorService = Executors.newSingleThreadScheduledExecutor(ThreadUtilities.newThreadFactory("command-service"));
		
		registerForIntent(GalacticPacketIntent.class, gpi -> handleGalacticPacketIntent(gpi));
		registerForIntent(PlayerEventIntent.class, pei -> handlePlayerEventIntent(pei));
		registerForIntent(PlayerTransformedIntent.class, pti -> handlePlayerTransformedIntent(pti));
	}
	
	@Override
	public boolean initialize() {
		loadBaseCommands();
		loadCombatCommands();
		registerCallbacks();
		
		executorService.scheduleAtFixedRate(() -> pollQueues(), 1, 1, TimeUnit.SECONDS);
		
		return super.initialize();
	}

	@Override
	public boolean terminate() {
		executorService.shutdown();
		return super.terminate();
	}
	
	private void handleGalacticPacketIntent(GalacticPacketIntent gpi) {
		Packet p = gpi.getPacket();
		if (p instanceof CommandQueueEnqueue) {
			CommandQueueEnqueue controller = (CommandQueueEnqueue) p;
			handleCommandRequest(gpi.getPlayer(), gpi.getGalacticManager(), controller);
		}
	}
	
	private void handlePlayerEventIntent(PlayerEventIntent pei) {
		switch(pei.getEvent()) {
			case PE_LOGGED_OUT:
				// No reason to keep their combat queue in the map if they log out
				// This also prevents queued commands from executing after the player logs out
				combatQueueMap.remove(pei.getPlayer());
				break;
			default:
				break;
		}
	}
	
	private void handlePlayerTransformedIntent(PlayerTransformedIntent pti) {
		CreatureObject creature = pti.getPlayer();

		if (creature.isPerforming()) {
			// A performer can transform while dancing...
			return;
		}

		Player player = creature.getOwner();
		Queue<QueuedCommand> combatQueue = combatQueueMap.get(player);

		if (combatQueue != null) {
			combatQueue.clear();
		}
	}
	
	private void handleCommandRequest(Player player, GalacticManager galacticManager, CommandQueueEnqueue request) {
		if (!commandExists(request.getCommandCrc())) {
			if (request.getCommandCrc() != 0)
				Log.e("CommandService", "Invalid command crc: %x", request.getCommandCrc());
			return;
		}
		
		Command command = getCommand(request.getCommandCrc());
		// TODO target and target type checks below. Work with Set<TangibleObject> targets from there
		long targetId = request.getTargetId();
		final SWGObject target = targetId != 0 ? galacticManager.getObjectManager().getObjectById(targetId) : null;
		
		if (!command.getCooldownGroup().equals("defaultCooldownGroup") && command.isAddToCombatQueue()) {
			// Schedule for later execution
			BlockingQueue<QueuedCommand> combatQueue = combatQueueMap.get(player);

			if (combatQueue == null) {
				combatQueue = new PriorityBlockingQueue<>();	// Has natural ordering. QueuedCommand implements Comparable for this purpose.
				combatQueueMap.put(player, combatQueue);
			}

			if (!combatQueue.offer(new QueuedCommand(command, galacticManager, target, request))) {
				// Ziggy: Shouldn't happen, unless the Queue implementation is changed
				Log.e(this, "Unable to enqueue command %s from %s because the combat queue is full", command.getName(), player.getCreatureObject());
			}
		} else {
			// Execute it now
			doCommand(galacticManager, player, command, target, request);
		}
	}
	
	private void pollQueues() {
		// Takes the head of each queue and executes the command
		combatQueueMap.forEach((player, combatQueue) -> {
			QueuedCommand queueHead = combatQueue.poll();

			if (queueHead != null) { // Can be null if the combat queue is empty
				doCommand(queueHead.getGalacticManager(), player, queueHead.getCommand(), queueHead.getTarget(), queueHead.getRequest());
			}
		});
	}
	
	private void doCommand(GalacticManager galacticManager, Player player, Command command, SWGObject target, CommandQueueEnqueue request) {
		CreatureObject creature = player.getCreatureObject();
		// TODO implement locomotion and state checks up here. See action and error in CommandQueueDequeue!
		
		// TODO target and targetType checks
		
		// Let's check if this ability is on cooldown
		String cooldownGroup = command.getCooldownGroup();
		String cooldownGroup2 = command.getCooldownGroup2();
		
		synchronized (cooldownMap) {
			Set<String> cooldowns = cooldownMap.get(creature);

			if (cooldowns == null) {
				// This is the first time they're using a cooldown command
				cooldowns = new HashSet<>();
				cooldownMap.put(creature, cooldowns);
			} else if (cooldowns.contains(cooldownGroup) || cooldowns.contains(cooldownGroup2)) {
				// This ability is currently on cooldown
				sendCommandDequeue(player, command, request, 0, 0);
				return;
			}
		}
		
		sendCommandDequeue(player, command, request, 0, 0);
		
		String argumentString = request.getArguments();
		String[] arguments = argumentString.split(" ");
		executeCommand(galacticManager, player, command, target, argumentString);
		new ChatCommandIntent(creature, target, command, arguments).broadcast();
		
		// TODO custom cooldown times. Scripts might be a good idea.
		
		startCooldownGroup(creature, request.getCounter(), command.getCrc(), cooldownGroup, command.getCooldownTime());
		startCooldownGroup(creature, request.getCounter(), command.getCrc(), cooldownGroup2, command.getCooldownTime2());
	}
	
	private void sendCommandDequeue(Player player, Command command, CommandQueueEnqueue request, int action, int error) {
		CommandQueueDequeue dequeue = new CommandQueueDequeue(player.getCreatureObject().getObjectId());
		dequeue.setCounter(request.getCounter());
		dequeue.setAction(action);
		dequeue.setError(error);
		dequeue.setTimer(command.getExecuteTime());
		player.sendPacket(dequeue);
	}
	
	private void startCooldownGroup(CreatureObject creature, int sequenceId, int commandNameCrc, String cooldownGroup, float cooldownTime) {
		if(!cooldownGroup.isEmpty() && !cooldownGroup.equals("defaultCooldownGroup")) {
			synchronized(cooldownMap) {
				if(cooldownMap.get(creature).add(cooldownGroup)) {
					CommandTimer commandTimer = new CommandTimer(creature.getObjectId());
					commandTimer.setCooldownGroupCrc(CRC.getCrc(cooldownGroup));
					commandTimer.setCooldownMax(cooldownTime);
					commandTimer.setCommandNameCrc(commandNameCrc);
					commandTimer.setSequenceId(sequenceId);
					creature.sendSelf(commandTimer);
					
					executorService.schedule(() -> removeCooldown(creature, cooldownGroup), (long) (cooldownTime * 1000), TimeUnit.MILLISECONDS);
				}
			}
		}
	}
	
	private void removeCooldown(CreatureObject creature, String cooldownGroup) {
		synchronized (cooldownMap) {
			Set<String> cooldownGroups = cooldownMap.get(creature);
			if (cooldownGroups.remove(cooldownGroup)) {
				
			} else {
				Log.w(this, "%s doesn't have cooldown group %s!", creature, cooldownGroup);
			}
		}
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
			new ChatBroadcastIntent(player, new ProsePackage("StringId", new StringId("cmd_err", "state_must_have_prose"), "TO", errorProseString1, "TU", commandAccessLevel)).broadcast();
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
				command.getJavaCallback().newInstance().execute(galacticManager, player, target, args);
			} catch (InstantiationException | IllegalAccessException e) {
				Log.e(this, e);
			}
		}
		else
			try {
				Scripts.invoke("commands/generic/" + command.getDefaultScriptCallback(), "executeCommand", galacticManager, player, target, args);
			} catch (FileNotFoundException ex) {
			}
	}
	
	private void loadBaseCommands() {
		// First = Higher Priority, Last = Lower Priority ---- Some tables contain duplicates, ORDER MATTERS!
		final String [] commandTables = new String [] {
			"command_table", "command_table_ground", "client_command_table",
			"command_table_space", "client_command_table_ground", "client_command_table_space"
		};
		
		clearCommands();
		for (String table : commandTables) {
			loadBaseCommands(table);
		}
	}
	
	private void loadBaseCommands(String table) {
		DatatableData baseCommands = (DatatableData) ClientFactory.getInfoFromFile("datatables/command/"+table+".iff");

		int godLevel = baseCommands.getColumnFromName("godLevel");
		int cooldownGroup = baseCommands.getColumnFromName("cooldownGroup");
		int cooldownGroup2 = baseCommands.getColumnFromName("cooldownGroup2");
		int cooldownTime = baseCommands.getColumnFromName("cooldownTime");
		int cooldownTime2 = baseCommands.getColumnFromName("cooldownTime2");

		for (int row = 0; row < baseCommands.getRowCount(); row++) {
			Object [] cmdRow = baseCommands.getRow(row);
			String commandName = ((String) cmdRow[0]).toLowerCase(Locale.ENGLISH);
			Command command = new Command(commandName);
			
			command.setCrc(CRC.getCrc(commandName));
			command.setDefaultPriority(DefaultPriority.getDefaultPriority((int) cmdRow[1]));
			command.setScriptHook((String) cmdRow[2]);
			command.setCppHook((String)cmdRow[4]);
			command.setDefaultTime((float) cmdRow[6]);
			command.setCharacterAbility((String) cmdRow[7]);
			command.setCombatCommand(false);
			command.setCooldownGroup((String) cmdRow[cooldownGroup]);
			command.setCooldownGroup2((String) cmdRow[cooldownGroup2]);
			command.setCooldownTime((float) cmdRow[cooldownTime]);
			command.setCooldownTime2((float) cmdRow[cooldownTime2]);
			
			// Ziggy: The amount of columns in the table seems to change for each row
			if(cmdRow.length >= 83) {
				Object addToCombatQueue = cmdRow[82];

				// Ziggy: Sometimes this column contains a String... uwot SOE?
				if(addToCombatQueue instanceof Boolean) {
					command.setAddToCombatQueue((Boolean) addToCombatQueue);
				}
			}
			
			if(godLevel >= 0){ 
				command.setGodLevel((int) cmdRow[godLevel]);
			}
			
			addCommand(command);
		}
	}
	
	private CombatCommand createAsCombatCommand(Command c) {
		CombatCommand cc = new CombatCommand(c.getName());
		cc.setCrc(c.getCrc());
		cc.setScriptHook(c.getScriptHook());
		cc.setCppHook(c.getScriptHook());
		cc.setDefaultTime(c.getDefaultTime());
		cc.setCharacterAbility(c.getCharacterAbility());
		cc.setGodLevel(c.getGodLevel());
		cc.setCombatCommand(true);
		cc.setCooldownGroup(c.getCooldownGroup());
		cc.setCooldownGroup2(c.getCooldownGroup2());
		cc.setCooldownTime(c.getCooldownTime());
		cc.setCooldownTime2(c.getCooldownTime2());
		cc.setMaxRange(c.getMaxRange());
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
		// animDefault	anim_unarmed	anim_onehandmelee	anim_twohandmelee	anim_polearm
		// anim_pistol	anim_lightRifle	anim_carbine	anim_rifle	anim_heavyweapon
		// anim_thrown	anim_onehandlightsaber	anim_twohandlightsaber	anim_polearmlightsaber
		for (int row = 0; row < combatCommands.getRowCount(); row++) {
			Object [] cmdRow = combatCommands.getRow(row);
			
			Command c = commands.get(CRC.getCrc(((String) cmdRow[0]).toLowerCase(Locale.ENGLISH)));
			if (c == null)
				continue;
			CombatCommand cc = createAsCombatCommand(c);
			commands.remove(c.getCrc());
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
			cc.setAnimations(WeaponType.UNARMED, getAnimationList((String) cmdRow[animDefault+1]));
			cc.setAnimations(WeaponType.ONE_HANDED_MELEE, getAnimationList((String) cmdRow[animDefault+2]));
			cc.setAnimations(WeaponType.TWO_HANDED_MELEE, getAnimationList((String) cmdRow[animDefault+3]));
			cc.setAnimations(WeaponType.POLEARM_MELEE, getAnimationList((String) cmdRow[animDefault+4]));
			cc.setAnimations(WeaponType.POLEARM_MELEE, getAnimationList((String) cmdRow[animDefault+5]));
			cc.setAnimations(WeaponType.PISTOL, getAnimationList((String) cmdRow[animDefault+6]));
			cc.setAnimations(WeaponType.LIGHT_RIFLE, getAnimationList((String) cmdRow[animDefault+7]));
			cc.setAnimations(WeaponType.CARBINE, getAnimationList((String) cmdRow[animDefault+8]));
			cc.setAnimations(WeaponType.RIFLE, getAnimationList((String) cmdRow[animDefault+9]));
			cc.setAnimations(WeaponType.THROWN, getAnimationList((String) cmdRow[animDefault+10]));
			cc.setAnimations(WeaponType.ONE_HANDED_SABER, getAnimationList((String) cmdRow[animDefault+11]));
			cc.setAnimations(WeaponType.TWO_HANDED_SABER, getAnimationList((String) cmdRow[animDefault+12]));
			cc.setAnimations(WeaponType.POLEARM_SABER, getAnimationList((String) cmdRow[animDefault+13]));
			addCommand(cc);
		}
	}
	
	private String [] getAnimationList(String cell) {
		if (cell.isEmpty())
			return new String[0];
		return cell.split(",");
	}
	
	private <T extends ICmdCallback> Command registerCallback(String command, Class<T> callback) {
		command = command.toLowerCase(Locale.ENGLISH);
		Command comand = getCommand(command);
		registerCallback(comand, callback);
		return comand;
	}

	private <T extends ICmdCallback> void registerCallback(Command command, Class<T> callback) {
		try {
			if (callback.getConstructor() == null)
				throw new IllegalArgumentException("Incorrectly registered callback class. Class must extend ICmdCallback and have an empty constructor: " + callback.getName());
		} catch (NoSuchMethodException e) {
			Log.e(this, e);
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
		registerCallback("startDance", StartDanceCallback.class);
		registerCallback("requestBiography", RequestBiographyCmdCallback.class);
		registerCallback("flourish", FlourishCmdCallback.class);
		registerCallback("changeDance", ChangeDanceCallback.class);
		registerCallback("transferItemMisc", TransferItemCallback.class);
		registerCallback("transferItemArmor", TransferItemCallback.class);
		registerCallback("transferItemWeapon", TransferItemCallback.class);
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
	
	private boolean addCommand(Command command) {
		if (commands.containsKey(command.getCrc()))
			return false;

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
				commands = new LinkedList<>();
				commandByScript.put(script, commands);
			}
			commands.add(command);
		}
		return true;
	}
	
	private static class QueuedCommand implements Comparable<QueuedCommand> {

		private final Command command;
		private final GalacticManager galacticManager;
		private final SWGObject target;
		private final CommandQueueEnqueue request;

		public QueuedCommand(Command command, GalacticManager galacticManager, SWGObject target, CommandQueueEnqueue request) {
			this.command = command;
			this.galacticManager = galacticManager;
			this.target = target;
			this.request = request;
		}
		
		@Override
		public int compareTo(QueuedCommand o) {
			return command.getDefaultPriority().compareTo(o.getCommand().getDefaultPriority());
		}

		public Command getCommand() {
			return command;
		}

		public GalacticManager getGalacticManager() {
			return galacticManager;
		}

		public SWGObject getTarget() {
			return target;
		}

		public CommandQueueEnqueue getRequest() {
			return request;
		}
		
	}
}
