package services.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import network.packets.swg.zone.object_controller.combat.CombatAction;
import intents.chat.ChatBroadcastIntent;
import intents.chat.ChatCommandIntent;
import resources.combat.AttackInfoLight;
import resources.combat.AttackType;
import resources.combat.CombatStatus;
import resources.combat.HitLocation;
import resources.combat.TrailLocation;
import resources.commands.CombatCommand;
import resources.control.Intent;
import resources.control.Service;
import resources.encodables.ProsePackage;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.objects.weapon.WeaponObject;
import resources.server_info.Log;
import utilities.Scripts;
import utilities.ThreadUtilities;

public class CombatService extends Service {
	
	private ScheduledExecutorService executor;
	
	private Map<Long, CombatCreature> inCombat;
	
	public CombatService() {
		inCombat = new HashMap<>();
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
		return super.start();
	}
	
	@Override
	public boolean terminate() {
		executor.shutdownNow();
		try {
			executor.awaitTermination(3, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			
		}
		return super.terminate();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		if (i instanceof ChatCommandIntent)
			processChatCommand((ChatCommandIntent) i);
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
		combat.getCreature().setInCombat(false);
		combat.getCreature().clearDefenders();
	}
	
	private void processChatCommand(ChatCommandIntent cci) {
		if (!cci.getCommand().isCombatCommand() || !(cci.getCommand() instanceof CombatCommand))
			return;
		CombatCommand c = (CombatCommand) cci.getCommand();
		CombatStatus status = canPerform(cci.getSource(), cci.getTarget(), c);
		if (!handleStatus(cci.getSource(), status))
			return;
		Object res = Scripts.invoke("commands/combat/"+c.getName(), "doCombat", cci.getSource(), cci.getTarget(), c);
		if (res == null) {
			handleStatus(cci.getSource(), CombatStatus.UNKNOWN);
			return;
		}
		updateCombatList(cci.getSource());
		if (cci.getTarget() instanceof CreatureObject)
			updateCombatList((CreatureObject) cci.getTarget());
		if (res instanceof AttackInfoLight)
			doCombat(cci.getSource(), cci.getTarget(), (AttackInfoLight) res, c);
		else {
			Log.w(this, "Unknown return from combat script: " + res);
			return;
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
	
	private void doCombat(CreatureObject source, SWGObject target, AttackInfoLight info, CombatCommand command) {
		CombatAction action = new CombatAction(source.getObjectId());
		action.setAttacker(source);
		action.setClientEffectId((byte) 0);
		action.setActionCrc(command.getCrc());
		action.setTrail(TrailLocation.WEAPON);
		action.setUseLocation(false);
		if (target instanceof CreatureObject) {
			if (command.getAttackType() == AttackType.SINGLE_TARGET)
				doCombatSingle(source, (CreatureObject) target, info, command);
			action.addDefender((CreatureObject) target, true, (byte) 0, HitLocation.HIT_LOCATION_BODY, (short) info.getDamage());
		}
		source.sendObserversAndSelf(action);
	}
	
	private void doCombatSingle(CreatureObject source, CreatureObject target, AttackInfoLight info, CombatCommand command) {
		if (!source.isInCombat())
			source.setInCombat(true);
		if (!target.isInCombat())
			target.setInCombat(true);
		target.addDefender(source);
		// Note: This will not kill anyone
		if (target.getHealth() <= info.getDamage())
			target.setHealth(target.getMaxHealth());
		else
			target.modifyHealth(-info.getDamage());
	}
	
	private boolean handleStatus(CreatureObject source, CombatStatus status) {
		switch (status) {
			case SUCCESS:
				return true;
			case NO_TARGET:
				new ChatBroadcastIntent(source.getOwner(), new ProsePackage("combat_effects", "target_invalid_fly")).broadcast();
				return false;
			case TOO_FAR:
				new ChatBroadcastIntent(source.getOwner(), new ProsePackage("combat_effects", "range_too_far")).broadcast();
				return false;
			default:
				new ChatBroadcastIntent(source.getOwner(), new ProsePackage("combat_effects", "cant_attack_fly")).broadcast();
				Log.e(this, "Character unable to attack. Player: %s  Reason: %s", source.getName(), status);
				return false;
		}
	}
	
	private CombatStatus canPerform(CreatureObject source, SWGObject target, CombatCommand c) {
		if (source.getEquippedWeapon() == null)
			return CombatStatus.NO_WEAPON;
		CombatStatus status;
		switch (c.getAttackType()) {
			case AREA:
			case TARGET_AREA:
				status = canPerformArea(source, c);
				break;
			case SINGLE_TARGET:
				status = canPerformSingle(source, target, c);
				break;
			default:
				status = CombatStatus.UNKNOWN;
				break;
		}
		if (status != CombatStatus.SUCCESS)
			return status;
		status = Scripts.invoke("commands/combat/"+c.getName(), "canPerform", source, target, c);
		if (status == null) {
			return CombatStatus.UNKNOWN;
		}
		return status;
	}
	
	private CombatStatus canPerformSingle(CreatureObject source, SWGObject target, CombatCommand c) {
		if (target == null || !(target instanceof CreatureObject))
			return CombatStatus.NO_TARGET;
		WeaponObject weapon = source.getEquippedWeapon();
		double dist = source.getLocation().distanceTo(target.getLocation());
		if (dist > weapon.getMaxRange() || (dist > c.getMaxRange() && c.getMaxRange() > 0))
			return CombatStatus.TOO_FAR;
		return CombatStatus.SUCCESS;
	}
	
	private CombatStatus canPerformArea(CreatureObject source, CombatCommand c) {
		return CombatStatus.SUCCESS;
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
