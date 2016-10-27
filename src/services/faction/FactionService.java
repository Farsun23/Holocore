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
package services.faction;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import network.packets.swg.zone.UpdatePvpStatusMessage;
import network.packets.swg.zone.chat.ChatSystemMessage;
import network.packets.swg.zone.chat.ChatSystemMessage.SystemChatType;
import intents.FactionIntent;
import intents.chat.ChatBroadcastIntent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import resources.PvpFaction;
import resources.PvpFlag;
import resources.PvpStatus;
import resources.control.Intent;
import resources.control.Service;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.objects.tangible.TangibleObject;
import resources.player.Player;
import utilities.ThreadUtilities;

public final class FactionService extends Service {

	private final ScheduledExecutorService executor;
	private final Map<TangibleObject, Future<?>> statusChangers;
	
	public FactionService() {
		statusChangers = new HashMap<>();
		executor = Executors.newSingleThreadScheduledExecutor(ThreadUtilities.newThreadFactory("faction-service"));
		
		registerForIntent(FactionIntent.TYPE);
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		switch(i.getType()) {
			case FactionIntent.TYPE: handleFactionIntent((FactionIntent) i); break;
		}
	}
	
	@Override
	public boolean terminate() {
		// If some were in the middle of switching, finish the switches immediately
		executor.shutdownNow().forEach(runnable -> runnable.run());
			
		return super.terminate();
	}
	
	private void handleFactionIntent(FactionIntent i) {
		switch (i.getUpdateType()) {
			case FACTIONUPDATE:
				handleTypeChange(i);
				break;
			case SWITCHUPDATE:
				handleSwitchChange(i);
				break;
			case STATUSUPDATE:
				handleStatusChange(i);
				break;
			case FLAGUPDATE:
				handleFlagChange(i.getTarget());
				break;
		}
	}
	
	private void sendSystemMessage(TangibleObject target, String message) {
		target.getOwner().sendPacket(new ChatSystemMessage(SystemChatType.SCREEN_AND_CHAT, message));
	}
	
	private String getBeginMessage(PvpStatus oldStatus, PvpStatus newStatus) {
		String message = "@faction_recruiter:";
		
		if(oldStatus == PvpStatus.ONLEAVE && newStatus == PvpStatus.COMBATANT)
			message += "on_leave_to_covert";
		else if(oldStatus == PvpStatus.COMBATANT && newStatus == PvpStatus.SPECIALFORCES)
			message += "covert_to_overt";
		else if(oldStatus == PvpStatus.SPECIALFORCES && newStatus == PvpStatus.COMBATANT)
			message += "overt_to_covert";
		
		return message;
	}
	
	private String getCompletionMessage(PvpStatus oldStatus, PvpStatus newStatus) {
		String message = "@faction_recruiter:";
		
		if((oldStatus == PvpStatus.ONLEAVE || oldStatus == PvpStatus.SPECIALFORCES) && newStatus == PvpStatus.COMBATANT)
			message += "covert_complete";
		else if(oldStatus == PvpStatus.COMBATANT && newStatus == PvpStatus.SPECIALFORCES)
			message += "overt_complete";
		else if(oldStatus == PvpStatus.COMBATANT && newStatus == PvpStatus.ONLEAVE )
			message += "on_leave_complete";
			
		return message;
	}
	
	private long getDelay(PvpStatus oldStatus, PvpStatus newStatus) {
		long delay = 0;
		
		if(oldStatus == PvpStatus.ONLEAVE && newStatus == PvpStatus.COMBATANT)
			delay = 1;
		else if(oldStatus == PvpStatus.COMBATANT && newStatus == PvpStatus.SPECIALFORCES)
			delay = 30;
		else if(oldStatus == PvpStatus.SPECIALFORCES && newStatus == PvpStatus.COMBATANT)
			delay = 300;
		
		return delay;
	}
	
	private void handleTypeChange(FactionIntent fi) {
		TangibleObject target = fi.getTarget();
		PvpFaction newFaction = fi.getNewFaction();
		
		target.setPvpFaction(newFaction);
		handleFlagChange(target);
		
		if(target instanceof CreatureObject && ((CreatureObject) target).getPvpFaction() != PvpFaction.NEUTRAL) {
			// We're given rank 1 upon joining a non-neutral faction
			((CreatureObject) target).setFactionRank((byte) 1);
		}
	}
	
	private void handleSwitchChange(FactionIntent fi) {
		final PvpFlag pvpFlag;
		final TangibleObject target = fi.getTarget();
		final PvpStatus oldStatus = target.getPvpStatus();
		final PvpStatus newStatus;
		
		if(target.hasPvpFlag(PvpFlag.GOING_COVERT) || target.hasPvpFlag(PvpFlag.GOING_OVERT)) {
			sendSystemMessage(target, "@faction_recruiter:pvp_status_changing");
		} else {
			if(oldStatus == PvpStatus.COMBATANT) {
				pvpFlag = PvpFlag.GOING_OVERT;
				newStatus = PvpStatus.SPECIALFORCES;
			} else {	// Covers both ONLEAVE and SPECIALFORCES
				pvpFlag = PvpFlag.GOING_COVERT;
				newStatus = PvpStatus.COMBATANT;
			}
			
			target.setPvpFlags(pvpFlag);
			sendSystemMessage(target, getBeginMessage(oldStatus, newStatus));
			synchronized (statusChangers) {
				statusChangers.put(target, executor.schedule(() -> completeChange(target, pvpFlag, oldStatus, newStatus), getDelay(oldStatus, newStatus), TimeUnit.SECONDS));
			}
		}
	}
	
	// Forces the target into the given PvpStatus
	private void handleStatusChange(FactionIntent fi) {
		TangibleObject target = fi.getTarget();
		PvpStatus oldStatus = target.getPvpStatus();
		PvpStatus newStatus = fi.getNewStatus();
		
		// No reason to send deltas and all that if the status isn't effectively changing
		if(oldStatus == newStatus)
			return;
		
		// Let's clear PvP flags in case they were in the middle of going covert/overt
		synchronized (statusChangers) {
			Future<?> future = statusChangers.remove(target);

			if (future != null) {
				if (future.cancel(false)) {
					target.clearPvpFlags(PvpFlag.GOING_COVERT, PvpFlag.GOING_OVERT);
				} else if (target.getPvpStatus() != newStatus) {
					// Their new status does not equal the one we want - apply the new one
					changeStatus(target, newStatus);
				}
			} else {
				// They're not currently waiting to switch to a new status - change now
				changeStatus(target, newStatus);
			}
		}
	}
	
	private void handleFlagChange(TangibleObject target) {
		Player objOwner = target.getOwner();
		
		for (SWGObject o : target.getObservers()) {
			if (!(o instanceof TangibleObject))
				continue;
			TangibleObject observer = (TangibleObject) o;
			Player obsOwner = observer.getOwner();

			int pvpBitmask = getPvpBitmask(target, observer);
			
			if (objOwner != null)
				// Send the PvP information about this observer to the owner
				objOwner.sendPacket(createPvpStatusMessage(observer, observer.getPvpFlags() | pvpBitmask));
			if (obsOwner != null)
				// Send the pvp information about the owner to this observer
				obsOwner.sendPacket(createPvpStatusMessage(target, target.getPvpFlags() | pvpBitmask));
		}
	}
	
	private void completeChange(TangibleObject target, PvpFlag pvpFlag, PvpStatus oldStatus, PvpStatus newStatus) {
		synchronized (statusChangers) {
			statusChangers.remove(target);
		}
		
		new ChatBroadcastIntent(target.getOwner(), getCompletionMessage(oldStatus, newStatus)).broadcast();
		target.clearPvpFlags(pvpFlag);
		changeStatus(target, newStatus);
	}
	
	private void changeStatus(TangibleObject target, PvpStatus newStatus) {
		target.setPvpStatus(newStatus);
		handleFlagChange(target);
	}
	
	private UpdatePvpStatusMessage createPvpStatusMessage(TangibleObject target, int flags) {
		Set<PvpFlag> flagSet = PvpFlag.getFlags(flags);
		return new UpdatePvpStatusMessage(target.getPvpFaction(), target.getObjectId(), flagSet.toArray(new PvpFlag[flagSet.size()]));
	}
	
	private int getPvpBitmask(TangibleObject target, TangibleObject observer) {
		int pvpBitmask = 0;

		if(target.isEnemy(observer)) {
			pvpBitmask |= PvpFlag.AGGRESSIVE.getBitmask() | PvpFlag.ATTACKABLE.getBitmask() | PvpFlag.ENEMY.getBitmask();
		}
		
		return pvpBitmask;
	}
	
}
