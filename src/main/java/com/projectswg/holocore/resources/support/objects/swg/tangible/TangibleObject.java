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
package com.projectswg.holocore.resources.support.objects.swg.tangible;

import com.projectswg.common.data.customization.CustomizationString;
import com.projectswg.common.data.encodables.mongo.MongoData;
import com.projectswg.common.data.encodables.tangible.PvpFaction;
import com.projectswg.common.data.encodables.tangible.PvpFlag;
import com.projectswg.common.data.encodables.tangible.PvpStatus;
import com.projectswg.common.encoding.StringType;
import com.projectswg.common.network.NetBuffer;
import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.network.packets.swg.zone.baselines.Baseline.BaselineType;
import com.projectswg.holocore.intents.gameplay.gcw.faction.FactionIntent;
import com.projectswg.holocore.intents.gameplay.gcw.faction.FactionIntent.FactionIntentType;
import com.projectswg.holocore.intents.support.objects.swg.DestroyObjectIntent;
import com.projectswg.holocore.resources.support.data.collections.SWGMap;
import com.projectswg.holocore.resources.support.data.collections.SWGSet;
import com.projectswg.holocore.resources.support.global.network.BaselineBuilder;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.permissions.ContainerResult;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TangibleObject extends SWGObject {
	
	private CustomizationString	appearanceData	= new CustomizationString();
	private int				maxHitPoints	= 1000;
	private int				components		= 0;
	private boolean			inCombat		= false;
	private int				condition		= 0;
	private Set<PvpFlag>	pvpFlags		= EnumSet.noneOf(PvpFlag.class);
	private PvpStatus		pvpStatus = PvpStatus.COMBATANT;
	private PvpFaction		pvpFaction = PvpFaction.NEUTRAL;
	private boolean			visibleGmOnly	= true;
	private byte []			objectEffects	= new byte[0];
	private int    			optionFlags     = 0;
	private int				counter			= 0;
	private String			currentCity				= "";
	
	private SWGSet<Long>	defenders	= new SWGSet<>(6, 3);
	
	private SWGMap<String, String> effectsMap	= new SWGMap<>(6, 7);
	
	public TangibleObject(long objectId) {
		this(objectId, BaselineType.TANO);
		addOptionFlags(OptionFlag.INVULNERABLE);
	}
	
	public TangibleObject(long objectId, BaselineType objectType) {
		super(objectId, objectType);
	}
	
	@Override
	public void moveToContainer(SWGObject newParent) {
		if (defaultMoveToContainer(newParent))
			super.moveToContainer(newParent);    // Not stackable, use default behavior
	}
	
	@Override
	public ContainerResult moveToContainer(@NotNull CreatureObject requester, SWGObject newParent) {
		if (!getContainerPermissions().canMove(requester, newParent))
			return ContainerResult.NO_PERMISSION;
		return defaultMoveToContainer(newParent) ? super.moveToContainer(requester, newParent) : ContainerResult.SUCCESS;
	}
	
	private boolean defaultMoveToContainer(SWGObject newParent) {
		int counter = getCounter();
		
		// Check if object is stackable
		if (newParent != null && counter > 0) {
			// Scan container for matching stackable item
			String ourTemplate = getTemplate();
			Map<String, String> ourAttributes = getAttributes();
			TangibleObject bestMatch = null;
			
			for (SWGObject candidate : newParent.getContainedObjects()) {
				String theirTemplate = candidate.getTemplate();
				Map<String, String> theirAttributes = candidate.getAttributes();
				
				if (candidate == this)
					continue; // Can't transfer into itself
				if (!(candidate instanceof TangibleObject))
					continue; // Item not the correct type
				if (!ourTemplate.equals(theirTemplate) || !ourAttributes.equals(theirAttributes))
					continue; // Not eligible for stacking
				
				TangibleObject tangibleMatch = (TangibleObject) candidate;
				if (tangibleMatch.getCounter() >= tangibleMatch.getMaxCounter())
					continue; // Can't add anything to this object
				
				bestMatch = tangibleMatch;
			}
			
			if (bestMatch != null) {
				int theirCounter = bestMatch.getCounter();
				int transferAmount = Math.min(bestMatch.getMaxCounter() - theirCounter, counter);
				
				bestMatch.setCounter(theirCounter + transferAmount);
				setCounter(counter - transferAmount);
				if (getCounter() > 0)
					return true;
				DestroyObjectIntent.broadcast(this);
				return false;
			}
		}
		
		return true;
	}
	
	public int getMaxHitPoints() {
		return maxHitPoints;
	}
	
	public int getComponents() {
		return components;
	}
	
	public boolean isInCombat() {
		return inCombat;
	}
	
	public int getCondition() {
		return condition;
	}
	
	public void setPvpFlags(PvpFlag... pvpFlags) {
		setPvpFlags(List.of(pvpFlags));
	}
	
	public void setPvpFlags(Collection<PvpFlag> pvpFlags) {
		this.pvpFlags.addAll(pvpFlags);
		
		new FactionIntent(this, FactionIntentType.FLAGUPDATE).broadcast();
	}
	
	public void clearPvpFlags(PvpFlag ... pvpFlags) {
		clearPvpFlags(List.of(pvpFlags));
	}
	
	public void clearPvpFlags(Collection<PvpFlag> pvpFlags) {
		this.pvpFlags.removeAll(pvpFlags);
		
		new FactionIntent(this, FactionIntentType.FLAGUPDATE).broadcast();
	}
	
	public boolean hasPvpFlag(PvpFlag pvpFlag) {
		return pvpFlags.contains(pvpFlag);
	}
	
	public PvpStatus getPvpStatus() {
		return pvpStatus;
	}

	public void setPvpStatus(PvpStatus pvpStatus) {
		this.pvpStatus = pvpStatus;
		
		sendDelta(3, 5, pvpStatus.getValue());
	}
	
	public PvpFaction getPvpFaction() {
		return pvpFaction;
	}
	
	public void setPvpFaction(PvpFaction pvpFaction) {
		this.pvpFaction = pvpFaction;
		
		sendDelta(3, 4, pvpFaction.getCrc());
	}
	
	public Set<PvpFlag> getPvpFlags() {
		return Collections.unmodifiableSet(pvpFlags);
	}
	
	public boolean isVisibleGmOnly() {
		return visibleGmOnly;
	}
	
	public byte [] getObjectEffects() {
		return objectEffects;
	}
	
	public void putCustomization(String name, int value) {
		appearanceData.put(name, value);
	}
	
	public Integer getCustomization(String name) {
		return appearanceData.get(name);
	}
	
	public Map<String, Integer> getCustomization() {
		return appearanceData.getVariables();
	}
	
	public void setAppearanceData(CustomizationString appearanceData) {
		this.appearanceData = appearanceData;
		
		sendDelta(3, 6, appearanceData);
	}
	
	public void setMaxHitPoints(int maxHitPoints) {
		this.maxHitPoints = maxHitPoints;
		sendDelta(3, 11, maxHitPoints);
	}
	
	public void setComponents(int components) {
		this.components = components;
	}
	
	public void setInCombat(boolean inCombat) {
		this.inCombat = inCombat;
		sendDelta(6, 2, inCombat);
	}
	
	public void setCondition(int condition) {
		this.condition = condition;
	}
	
	public void setVisibleGmOnly(boolean visibleGmOnly) {
		this.visibleGmOnly = visibleGmOnly;
	}
	
	public void setObjectEffects(byte [] objectEffects) {
		this.objectEffects = objectEffects;
	}

	public void setOptionFlags(int optionsBitmask) {
		this.optionFlags = optionsBitmask;
	}

	public void setOptionFlags(OptionFlag ... options) {
		optionFlags = 0;
		addOptionFlags(options);
	}

	public void addOptionFlags(OptionFlag ... options) {
		for (OptionFlag flag : options) {
			optionFlags |= flag.getFlag();
		}
		sendDelta(3, 8, optionFlags);
	}

	public void toggleOptionFlags(OptionFlag ... options) {
		for (OptionFlag option : options) {
			optionFlags ^= option.getFlag();
		}
		sendDelta(3, 8, optionFlags);
	}

	public void removeOptionFlags(OptionFlag ... options) {
		for (OptionFlag option : options) {
			optionFlags &= ~option.getFlag();
		}
		sendDelta(3, 8, optionFlags);
	}

	public boolean hasOptionFlags(OptionFlag ... options) {
		for (OptionFlag option : options) {
			if ((optionFlags & option.getFlag()) == 0)
				return false;
		}
		return true;
	}
	
	public Set<OptionFlag> getOptionFlags() {
		return OptionFlag.toEnumSet(optionFlags);
	}
	
	public void addDefender(CreatureObject creature) {
		synchronized (defenders) {
			if (defenders.add(creature.getObjectId()))
				defenders.sendDeltaMessage(this);
		}
	}
	
	public void removeDefender(CreatureObject creature) {
		synchronized (defenders) {
			if (defenders.remove(creature.getObjectId()))
				defenders.sendDeltaMessage(this);
		}
	}
	
	public List<Long> getDefenders() {
		return new ArrayList<>(defenders);
	}
	
	public void clearDefenders() {
		synchronized (defenders) {
			defenders.clear();
			defenders.sendDeltaMessage(this);
		}
	}
	
	public boolean hasDefenders() {
		return !defenders.isEmpty();
	}

	public int getCounter() {
		return counter;
	}

	public void setCounter(int counter) {
		this.counter = counter;
		sendDelta(3, 9, counter);
	}
	
	public int getMaxCounter() {
		return 100;
	}
	
	/**
	 *
	 * @param otherObject
	 * @return true if this object is an enemy of {@code otherObject}
	 */
	public boolean isEnemyOf(TangibleObject otherObject) {
		if (otherObject.hasOptionFlags(OptionFlag.INVULNERABLE)) {
			return false;
		}
		
		if (otherObject.hasPvpFlag(PvpFlag.ATTACKABLE)) {
			return true;
		}
		
		PvpFaction ourFaction = getPvpFaction();
		PvpFaction otherFaction = otherObject.getPvpFaction();
		
		if (ourFaction == PvpFaction.NEUTRAL || otherFaction == PvpFaction.NEUTRAL) {
			// Neutrals are always excluded from factional combat, unless they're both neutral
			return ourFaction == otherFaction;
		}
		
		// At this point, neither are neutral
		
		if (ourFaction == otherFaction) {
			// Members of the same faction are not enemies
			return false;
		}
		
		// At this point, they're members of opposing factions
		
		PvpStatus ourStatus = getPvpStatus();
		PvpStatus otherStatus = otherObject.getPvpStatus();
		
		if (ourStatus == PvpStatus.ONLEAVE || otherStatus == PvpStatus.ONLEAVE) {
			// They're of opposing factions, but one of them on leave
			return false;
		}
		
		// At this point, they're both either combatant or special forces
		
		boolean ourPlayer = getSlottedObject("ghost") != null;
		boolean otherPlayer = otherObject.getSlottedObject("ghost") != null;
		
		if (ourPlayer && otherPlayer) {
			// Two players can only attack each other if both are Special Forces
			return ourStatus == PvpStatus.SPECIALFORCES && otherStatus == PvpStatus.SPECIALFORCES;
		} else {
			// At this point, we're dealing with player vs npc or npc vs npc
			// In this case, they just need to not be on leave and we've already established this
			return true;
		}
	}
	
	public Set<PvpFlag> getPvpFlagsFor(TangibleObject observer) {
		Set<PvpFlag> pvpFlags = EnumSet.copyOf(observer.pvpFlags); // More efficient behind the scenes
		
		if (isEnemyOf(observer) && getPvpFaction() != PvpFaction.NEUTRAL && observer.getPvpFaction() != PvpFaction.NEUTRAL) {
			pvpFlags.add(PvpFlag.AGGRESSIVE);
			pvpFlags.add(PvpFlag.ATTACKABLE);
			pvpFlags.add(PvpFlag.ENEMY);
		}
		
		return pvpFlags;
	}
	
	public String getCurrentCity() {
		return currentCity;
	}
	
	public void setCurrentCity(String currentCity) {
		this.currentCity = currentCity;
	}
	
	@Override
	protected void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb); // 4 variables - BASE3 (4)
		bb.addInt(pvpFaction.getCrc()); // Faction - 4
		bb.addInt(pvpStatus.getValue()); // Faction Status - 5
		bb.addObject(appearanceData); // - 6
		bb.addInt(0); // Component customization (Set, Integer) - 7
			bb.addInt(0);
		bb.addInt(optionFlags); // 8
		bb.addInt(counter); // Generic Counter -- use count and incap timer - 9
		bb.addInt(condition); // 10
		bb.addInt(maxHitPoints); // maxHitPoints - 11
		bb.addBoolean(visibleGmOnly); // isVisible - 12
		
		bb.incrementOperandCount(9);
	}
	
	@Override
	protected void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb);
		bb.addBoolean(inCombat); // 2 - Combat flag
		bb.addObject(defenders); // 3 - Defenders List (Set, Long)
		bb.addInt(0); // 4 - Map color
		bb.addInt(0); // 5 - Access List
			bb.addInt(0);
		bb.addInt(0); // 6 - Guild Access Set
			bb.addInt(0);
		bb.addObject(effectsMap); // 7 - Effects Map
		
		bb.incrementOperandCount(6);
	}
	
	@Override
	protected void parseBaseline3(NetBuffer buffer) {
		super.parseBaseline3(buffer);
		pvpFaction = PvpFaction.getFactionForCrc(buffer.getInt());
		pvpStatus = PvpStatus.getStatusForValue(buffer.getInt());
		appearanceData.decode(buffer);
		SWGSet.getSwgSet(buffer, 3, 7, Integer.class);
		optionFlags = buffer.getInt();
		buffer.getInt();
		condition = buffer.getInt();
		maxHitPoints = buffer.getInt();
		visibleGmOnly = buffer.getBoolean();
	}
	
	@Override
	protected void parseBaseline6(NetBuffer buffer) {
		super.parseBaseline6(buffer);
		inCombat = buffer.getBoolean();
		defenders = SWGSet.getSwgSet(buffer, 6, 3, Long.TYPE);
		buffer.getInt();
		SWGSet.getSwgSet(buffer, 6, 5, StringType.ASCII);
		SWGSet.getSwgSet(buffer, 6, 6, StringType.ASCII);
		effectsMap = SWGMap.getSwgMap(buffer, 6, 7, StringType.ASCII);
	}
	
	@Override
	public void save(NetBufferStream stream) {
		super.save(stream);
		stream.addByte(1);
		appearanceData.save(stream);
		stream.addInt(maxHitPoints);
		stream.addInt(components);
		stream.addInt(condition);
		stream.addInt(pvpFlags.stream().mapToInt(PvpFlag::getBitmask).reduce(0, (a, b) -> a | b));
		stream.addAscii(pvpStatus.name());
		stream.addAscii(pvpFaction.name());
		stream.addBoolean(visibleGmOnly);
		stream.addArray(objectEffects);
		stream.addInt(optionFlags);
		stream.addMap(effectsMap, (e) -> {
			stream.addAscii(e.getKey());
			stream.addAscii(e.getValue());
		});
	}
	
	@Override
	public void read(NetBufferStream stream) {
		super.read(stream);
		byte version = stream.getByte();
		appearanceData.read(stream);
		maxHitPoints = stream.getInt();
		components = stream.getInt();
		if (version == 0)
			stream.getBoolean();
		condition = stream.getInt();
		pvpFlags = PvpFlag.getFlags(stream.getInt());
		pvpStatus = PvpStatus.valueOf(stream.getAscii());
		pvpFaction = PvpFaction.valueOf(stream.getAscii());
		visibleGmOnly = stream.getBoolean();
		objectEffects = stream.getArray();
		optionFlags = stream.getInt();
		stream.getList((i) -> effectsMap.put(stream.getAscii(), stream.getAscii()));
	}
	
	@Override
	public void saveMongo(MongoData data) {
		super.saveMongo(data);
		data.putDocument("appearance", appearanceData);
		data.putInteger("maxHitPoints", maxHitPoints);
		data.putInteger("components", components);
		data.putInteger("condition", condition);
		data.putInteger("pvpFlags", pvpFlags.stream().mapToInt(PvpFlag::getBitmask).reduce(0, (a, b) -> a | b));
		data.putString("pvpStatus", pvpStatus.name());
		data.putString("pvpFaction", pvpFaction.name());
		data.putBoolean("visibleGmOnly", visibleGmOnly);
		data.putByteArray("objectEffects", objectEffects);
		data.putInteger("optionFlags", optionFlags);
		data.putMap("effectsMap", effectsMap);
	}
	
	@Override
	public void readMongo(MongoData data) {
		super.readMongo(data);
		appearanceData.readMongo(data.getDocument("appearance"));
		maxHitPoints = data.getInteger("maxHitPoints", 1000);
		components = data.getInteger("components", 0);
		condition = data.getInteger("condition", 0);
		pvpFlags.addAll(PvpFlag.getFlags(data.getInteger("pvpFlags", 0)));
		pvpStatus = PvpStatus.valueOf(data.getString("pvpStatus", "COMBATANT"));
		pvpFaction = PvpFaction.valueOf(data.getString("pvpFaction", "NEUTRAL"));
		visibleGmOnly = data.getBoolean("visibleGmOnly", false);
		objectEffects = data.getByteArray("objectEffects");
		optionFlags = data.getInteger("optionFlags", 0);
		effectsMap.putAll(data.getMap("effectsMap", String.class, String.class));
	}
}
