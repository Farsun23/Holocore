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
package services.combat;

import intents.BuffIntent;
import intents.chat.ChatBroadcastIntent;
import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import network.packets.swg.zone.object_controller.ShowFlyText;
import network.packets.swg.zone.object_controller.ShowFlyText.Scale;
import network.packets.swg.zone.object_controller.combat.CombatAction;
import intents.chat.ChatCommandIntent;
import intents.combat.CreatureKilledIntent;
import intents.combat.DeathblowIntent;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Future;
import network.packets.swg.zone.object_controller.combat.CombatSpam;
import resources.Posture;
import resources.combat.AttackInfo;
import resources.combat.CombatStatus;
import resources.combat.DamageType;
import resources.combat.HitLocation;
import resources.combat.TrailLocation;
import resources.commands.CombatCommand;
import resources.common.CRC;
import resources.common.RGB;
import resources.control.Intent;
import resources.control.Manager;
import resources.encodables.ProsePackage;
import resources.encodables.StringId;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.objects.tangible.TangibleObject;
import resources.objects.weapon.WeaponObject;
import resources.server_info.Log;
import utilities.ThreadUtilities;

public class CombatManager extends Manager {

	private static final byte INCAP_TIMER = 10;	// Amount of seconds to be incapacitated
	
	private final Map<Long, CombatCreature> inCombat;
	private final Set<CreatureObject> regeneratingHealthCreatures;	// Only allowed outside of combat
	private final Set<CreatureObject> regeneratingActionCreatures;	// Always allowed
	private final Map<CreatureObject, Future<?>> incapacitatedCreatures;
	private final Random random;
	private final CorpseService corpseService;
	private final CombatXpService combatXpService;
	
	private ScheduledExecutorService executor;
	
	// TODO upon first combat, cache skillmod-related calculations
	// TODO upon receiving SkillModIntent, the relevant calculation(s) must be updated
	// TODO remove calculations if they haven't been accessed for a while?
	// TODO remove calculations if the creature disappears
	
	public CombatManager() {
		registerForIntent(DeathblowIntent.TYPE);
		inCombat = new HashMap<>();
		regeneratingHealthCreatures = new HashSet<>();
		regeneratingActionCreatures = new HashSet<>();
		incapacitatedCreatures = new HashMap<>();
		random = new Random();
		
		corpseService = new CorpseService();
		combatXpService = new CombatXpService();
		addChildService(corpseService);
		addChildService(combatXpService);
	}
	
	@Override
	public boolean initialize() {
		registerForIntent(ChatCommandIntent.TYPE);
		executor = Executors.newSingleThreadScheduledExecutor(ThreadUtilities.newThreadFactory("combat-service"));
		return super.initialize();
	}
	
	@Override
	public boolean start() {
		executor.scheduleAtFixedRate(() -> periodicChecks(), 0, 5, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(() -> periodicRegeneration(), 1, 1, TimeUnit.SECONDS);
		return super.start();
	}
	
	@Override
	public boolean terminate() {
		if (executor != null) {
			executor.shutdownNow();
			try {
				executor.awaitTermination(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				
			}
		}
		return super.terminate();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		switch(i.getType()) {
			case ChatCommandIntent.TYPE: processChatCommand((ChatCommandIntent) i); break;
			case DeathblowIntent.TYPE: procesDeathblow((DeathblowIntent) i); break;
		}
	}
	
	private void periodicChecks() {
		Set<CombatCreature> removeSet = new HashSet<>();
		synchronized (inCombat) {
			for (CombatCreature combat : inCombat.values()) {
				if (combat.getTimeSinceCombat() >= 10E3) { // 10 sec
					removeSet.add(combat);
				}
			}
		}
		for (CombatCreature combat : removeSet)
			removeFromCombat(combat);
	}
	
	private void removeFromCombat(CombatCreature combat) {
		synchronized (inCombat) {
			inCombat.remove(combat.getCreature().getObjectId());
		}
		exitCombat(combat.getCreature());
	}
	
	private void periodicRegeneration() {
		synchronized (regeneratingActionCreatures) {
			Iterator<CreatureObject> iterator = regeneratingActionCreatures.iterator();

			while (iterator.hasNext()) {
				regenerationActionTick(iterator.next(), iterator);
			}
		}

		synchronized (regeneratingHealthCreatures) {
			Iterator<CreatureObject> iterator = regeneratingHealthCreatures.iterator();

			while (iterator.hasNext()) {
				regenerationHealthTick(iterator.next(), iterator);
			}
		}
	}
	
	private void regenerationActionTick(CreatureObject creatureObject, Iterator<CreatureObject> iterator) {
		if(creatureObject.getAction() < creatureObject.getMaxAction()) {
			int modification = 13;
			int level = creatureObject.getLevel();
			
			if(level > 1) {
				modification += 4 * level;
			}
			
			
			if(creatureObject.modifyAction(modification) == 0) {
				// Their action didn't change, meaning they're maxed out
				iterator.remove();
			}
		} else {
			// Maxed out - remove 'em
			iterator.remove();
		}
	}
	
	private void regenerationHealthTick(CreatureObject creatureObject, Iterator<CreatureObject> iterator) {
		if(creatureObject.getHealth() < creatureObject.getMaxHealth()) {
			int modification = 40;
			int level = creatureObject.getLevel();
			
			if(level > 1) {
				modification += 4 * level;
			}
			
			if(creatureObject.modifyHealth(modification) == 0) {
				// Their health didn't change, meaning they're maxed out
				iterator.remove();
			}
		} else {
			// Maxed out - remove 'em
			iterator.remove();
		}
	}
	
	private void processChatCommand(ChatCommandIntent intent) {
		if (!intent.getCommand().isCombatCommand() || !(intent.getCommand() instanceof CombatCommand))
			return;
		CombatCommand c = (CombatCommand) intent.getCommand();
		CreatureObject source = intent.getSource();
		SWGObject target = intent.getTarget();
		
		// Regardless of HitType, the command might have action cost
		addActionCost(source, c);
		
		// TODO implement support for remaining HitTypes
		switch (c.getHitType()) {
			case ATTACK: handleAttack(source, target, c); break;
			case BUFF: handleBuff(source, target, c); break;
			default: handleStatus(source, CombatStatus.UNKNOWN); break;
		}
	}
	
	private void updateCombatList(CreatureObject creature) {
		CombatCreature combat;
		synchronized (inCombat) {
			combat = inCombat.get(creature.getObjectId());
		}
		if (combat == null) {
			combat = new CombatCreature(creature);
			synchronized (inCombat) {
				inCombat.put(creature.getObjectId(), combat);
			}
		}
		combat.updateLastCombat();
	}
	
	private void handleAttack(CreatureObject source, SWGObject target, CombatCommand command) {
		if (!handleStatus(source, canPerform(source, target, command)))
			return;
		
		WeaponObject weapon = source.getEquippedWeapon();
		
		for(int i = 0; i < command.getAttackRolls(); i++) {
			AttackInfo info = new AttackInfo();
			
			if (target instanceof CreatureObject) {
				switch (command.getAttackType()) {
					case SINGLE_TARGET: doCombatSingle(source, (CreatureObject) target, info, weapon, command); break;
				}
			}
		}
	}
	
	private void handleBuff(CreatureObject source, SWGObject target, CombatCommand combatCommand) {
		addBuff(source, source, combatCommand.getBuffNameSelf());
		
		// Only CreatureObjects have buffs
		if(target instanceof CreatureObject)
			addBuff(source, (CreatureObject) target, combatCommand.getBuffNameTarget());
	}
	
	private void doCombatSingle(CreatureObject source, CreatureObject target, AttackInfo info, WeaponObject weapon, CombatCommand command) {
		// TODO single target only defence rolls against target
		// TODO single target only offence rolls for source
		
		// TODO below logic should be in CommandService when target checks are implemented in there
		Set<CreatureObject> targets = new HashSet<>();
		
		targets.add(target);
		
		doCombat(source, targets, weapon, info, command);
	}
	
	private void doCombat(CreatureObject source, Set<CreatureObject> targets, WeaponObject weapon, AttackInfo info, CombatCommand command) {
		updateCombatList(source);
		
		CombatAction action = new CombatAction(source.getObjectId());
		String anim = command.getRandomAnimation(weapon.getType());
		action.setActionCrc(CRC.getCrc(anim));
		action.setAttacker(source);
		action.setClientEffectId((byte) 0);
		action.setCommandCrc(command.getCrc());
		action.setTrail(TrailLocation.WEAPON);
		action.setUseLocation(false);
		
		for (CreatureObject target : targets) {
			updateCombatList(target);
		
			if (!source.isInCombat())
				enterCombat(source);
			if (!target.isInCombat())
				enterCombat(target);
			target.addDefender(source);
			source.addDefender(target);
			
			CombatSpam combatSpam = new CombatSpam(source.getObjectId());
			combatSpam.setAttacker(source);
			combatSpam.setDefender(target);
			combatSpam.setInfo(info);
			combatSpam.setAttackName(new StringId("cmd_n", command.getName()));
			combatSpam.setWeapon(weapon.getObjectId());
			
			// Combat log message appears for both the attacker and the defender
			source.sendSelf(combatSpam);
			target.sendSelf(combatSpam);

			if (!info.isSuccess()) {	// Single target negate, like dodge or parry!
				return;
			}
			
			addBuff(source, target, command.getBuffNameTarget());	// Add target buff
			
			int rawDamage = calculateWeaponDamage(source, weapon, command) + command.getAddedDamage();
			
			info.setRawDamage(rawDamage);
			info.setFinalDamage(rawDamage);	// Final damage will be modified by armour and defensive rolls later
			info.setDamageType(weapon.getDamageType());
			
			// TODO block roll for defenders
			// TODO Critical hit roll for attacker
			// TODO armour
			
			int finalDamage = info.getFinalDamage();
			
			action.addDefender((CreatureObject) target, true, (byte) 0, HitLocation.HIT_LOCATION_BODY, (short) finalDamage);
			
			if (target.getHealth() <= finalDamage)
				doCreatureDeath(target, source);
			else
				target.modifyHealth(-finalDamage);
		}
		
		source.sendObserversAndSelf(action);
	}
	
	private void enterCombat(CreatureObject creature) {
		creature.setInCombat(true);
		
		// If this creature is currently regenerating health, they should stop doing so now
		synchronized(regeneratingHealthCreatures) {
			regeneratingHealthCreatures.remove(creature);
		}
	}
	
	private void exitCombat(CreatureObject creature) {
		creature.setInCombat(false);
		creature.clearDefenders();
		
		// Once out of combat, we can regenerate health - unless we're dead or incapacitated!
		switch(creature.getPosture()) {
			case DEAD:
			case INCAPACITATED:
				// We can't regenerate HAM if we're incapcitated or dead
				synchronized (regeneratingActionCreatures) {
					regeneratingActionCreatures.remove(creature);
				}

				synchronized (regeneratingHealthCreatures) {
					regeneratingHealthCreatures.remove(creature);
				}
				break;
			default:
				synchronized (regeneratingActionCreatures) {
					regeneratingActionCreatures.add(creature);
				}
				
				synchronized (regeneratingHealthCreatures) {
					regeneratingHealthCreatures.add(creature);
				}
				break;
		}
	}
	
	private void doCreatureDeath(CreatureObject corpse, CreatureObject killer) {
		corpse.setHealth(0);
		killer.removeDefender(corpse);
		
		if(!killer.hasDefenders()) {
			exitCombat(killer);
		}
		
		// The creature should not be able to move or turn.
		corpse.setTurnScale(0);
		corpse.setMovementScale(0);
		
		if(corpse.isPlayer()) {
			if (corpse.hasBuff("incapWeaken")) {
				killCreature(killer, corpse);
			} else {
				incapacitatePlayer(killer, corpse);
			}
		} else {
			killCreature(killer, corpse);
		}
		
		exitCombat(corpse);
	}
	
	private void incapacitatePlayer(CreatureObject incapacitator, CreatureObject incapacitated) {
		incapacitated.setPosture(Posture.INCAPACITATED);
		incapacitated.setCounter(INCAP_TIMER);
		
		Log.i(this, "%s was incapacitated", incapacitated);
		
		// Once the incapacitation counter expires, revive them.
		synchronized(incapacitatedCreatures) {
			incapacitatedCreatures.put(incapacitated, executor.schedule(() -> expireIncapacitation(incapacitated), INCAP_TIMER, TimeUnit.SECONDS));
		}
		
		new BuffIntent("incapWeaken", incapacitator, incapacitated, false).broadcast();
		new ChatBroadcastIntent(incapacitator.getOwner(), new ProsePackage(new StringId("base_player", "prose_target_incap"), "TT", incapacitated.getName())).broadcast();
		new ChatBroadcastIntent(incapacitated.getOwner(), new ProsePackage(new StringId("base_player", "prose_victim_incap"), "TT", incapacitator.getName())).broadcast();
	}
	
	private void expireIncapacitation(CreatureObject incapacitatedPlayer) {
		synchronized(incapacitatedCreatures) {
			incapacitatedCreatures.remove(incapacitatedPlayer);
			reviveCreature(incapacitatedPlayer);
		}
	}
	
	private void reviveCreature(CreatureObject revivedCreature) {
		if(revivedCreature.isPlayer())
			revivedCreature.setCounter(0);
		
		revivedCreature.setPosture(Posture.UPRIGHT);
		
		// The creature is now able to turn around and move
		revivedCreature.setTurnScale(1);
		revivedCreature.setMovementScale(1);
		
		// Give 'em a percentage of their health and schedule them for HAM regeneration.
		revivedCreature.setHealth((int) (revivedCreature.getBaseHealth() * 0.1));	// Restores 10% health of their base health
		synchronized(regeneratingHealthCreatures) {
			regeneratingHealthCreatures.add(revivedCreature);
		}
		
		synchronized(regeneratingActionCreatures) {
			regeneratingActionCreatures.add(revivedCreature);
		}
		
		Log.i(this, "% was revived", revivedCreature);
	}
	
	private void killCreature(CreatureObject killer, CreatureObject corpse) {
		corpse.setPosture(Posture.DEAD);
		Log.i(this, "%s was killed by %s", corpse, killer);
		new CreatureKilledIntent(killer, corpse).broadcast();
	}
	
	private void procesDeathblow(DeathblowIntent i) {
		CreatureObject killer = i.getKiller();
		CreatureObject corpse = i.getCorpse();

		// Only deathblowing players is allowed!
		if (!corpse.isPlayer()) {
			return;
		}
		
		// They must be enemies
		if (!corpse.isEnemy(killer)) {
			return;
		}
		
		// The target of the deathblow must be incapacitated!
		if (corpse.getPosture() != Posture.INCAPACITATED) {
			return;
		}
		
		// If they're deathblown while incapacitated, their incapacitation expiration timer should cancel
		synchronized (incapacitatedCreatures) {
			Future<?> incapacitationTimer = incapacitatedCreatures.remove(corpse);

			if (incapacitationTimer != null) {
				if (incapacitationTimer.cancel(false)) {	// If the task is running, let them get back up
					killCreature(killer, corpse);
				}
			} else {
				// Can't happen with the current code, but in case it's ever refactored...
				Log.e(this, "Incapacitation timer for player %s being deathblown unexpectedly didn't exist!", "");
			}
		}
	}
	
	private boolean handleStatus(CreatureObject source, CombatStatus status) {
		switch (status) {
			case SUCCESS:
				return true;
			case NO_TARGET:
				showFlyText(source, "@combat_effects:target_invalid_fly", Scale.MEDIUM, Color.WHITE, ShowFlyText.Flag.PRIVATE);
				return false;
			case TOO_FAR:
				showFlyText(source, "@combat_effects:range_too_far", Scale.MEDIUM, Color.CYAN, ShowFlyText.Flag.PRIVATE);
				return false;
			case INVALID_TARGET:
				showFlyText(source, "@combat_effects:target_invalid_fly", Scale.MEDIUM, Color.CYAN, ShowFlyText.Flag.PRIVATE);
				return false;
			default:
				showFlyText(source, "@combat_effects:action_failed", Scale.MEDIUM, Color.WHITE, ShowFlyText.Flag.PRIVATE);
				return false;
		}
	}
	
	private CombatStatus canPerform(CreatureObject source, SWGObject target, CombatCommand c) {
		if (source.getEquippedWeapon() == null)
			return CombatStatus.NO_WEAPON;
		
		if (target == null || source.equals(target))
			return CombatStatus.SUCCESS;
		
		if (!(target instanceof TangibleObject))
			return CombatStatus.INVALID_TARGET;
		
		if (!source.isEnemy((TangibleObject) target)) {
			return CombatStatus.INVALID_TARGET;
		}
		
		if(target instanceof CreatureObject) {
			switch(((CreatureObject) target).getPosture()) {
				case DEAD:
				case INCAPACITATED:
					return CombatStatus.INVALID_TARGET;
				default:
					break;
			}
		}
		
		switch (c.getAttackType()) {
			case AREA:
			case TARGET_AREA:
				return canPerformArea(source, c);
			case SINGLE_TARGET:
				return canPerformSingle(source, target, c);
			default:
				return CombatStatus.UNKNOWN;
		}
	}
	
	private CombatStatus canPerformSingle(CreatureObject source, SWGObject target, CombatCommand c) {
		if (target == null || !(target instanceof TangibleObject))
			return CombatStatus.NO_TARGET;
		
		WeaponObject weapon = source.getEquippedWeapon();
		double dist = source.getWorldLocation().distanceTo(target.getWorldLocation());
		float commandRange = c.getMaxRange();
		float range = commandRange > 0 ? commandRange : weapon.getMaxRange();
		
		if (dist > range)
			return CombatStatus.TOO_FAR;
		
		return CombatStatus.SUCCESS;
	}
	
	private CombatStatus canPerformArea(CreatureObject source, CombatCommand c) {
		// TODO implement AoE
		return CombatStatus.UNKNOWN;
	}
	
	private void addActionCost(CreatureObject source, CombatCommand command) {
		double actionCost = command.getActionCost();
		int currentAction = source.getAction();
		
		if (actionCost <= 0 || actionCost > currentAction) {
			return;
		}
		
		synchronized (regeneratingActionCreatures) {
			source.setAction((int) (currentAction - actionCost));
			regeneratingActionCreatures.add(source);
		}
	}
	
	private int calculateWeaponDamage(CreatureObject source, WeaponObject weapon, CombatCommand command) {
		int minDamage = weapon.getMinDamage();
		int weaponDamage = random.nextInt((weapon.getMaxDamage() - minDamage) + 1) + minDamage;
		
		return (int) (weaponDamage * command.getPercentAddFromWeapon());
	}
	
	private void addBuff(CreatureObject caster, CreatureObject receiver, String buffName) {
		if (buffName.isEmpty()) {
			return;
		}
		
		new BuffIntent(buffName, caster, receiver, false).broadcast();
	}
	
	private void showFlyText(TangibleObject obj, String text, Scale scale, Color c, ShowFlyText.Flag ... flags) {
		obj.sendSelf(new ShowFlyText(obj.getObjectId(), text, scale, new RGB(c), flags));
	}
	
	private static class CombatCreature {
		
		private final CreatureObject creature;
		private long lastCombat;
		
		public CombatCreature(CreatureObject creature) {
			this.creature = creature;
		}
		
		public void updateLastCombat() {
			lastCombat = System.nanoTime();
		}
		
		public double getTimeSinceCombat() {
			return (System.nanoTime() - lastCombat) / 1E6;
		}
		
		public CreatureObject getCreature() {
			return creature;
		}
		
	}
	
}
