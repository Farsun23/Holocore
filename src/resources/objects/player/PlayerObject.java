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
package resources.objects.player;

import network.packets.swg.zone.UpdatePostureMessage;
import network.packets.swg.zone.baselines.Baseline.BaselineType;
import network.packets.swg.zone.chat.ChatSystemMessage;
import resources.collections.SWGFlag;
import resources.collections.SWGBitSet;
import resources.collections.SWGList;
import resources.collections.SWGMap;
import resources.network.BaselineBuilder;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.objects.intangible.IntangibleObject;
import resources.objects.waypoint.WaypointObject;
import resources.player.AccessLevel;
import resources.player.Player;
import resources.player.PlayerFlags;
import utilities.MathUtils;
import utilities.Encoder.StringType;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

public class PlayerObject extends IntangibleObject {
	
	private static final long serialVersionUID = 1L;
	
	private String				biography			= "";
	private List<String> 		joinedChannels		= new ArrayList<>();

	// PLAY 03
	private SWGFlag 	        flagsList			= new SWGFlag(3, 5);
	private SWGFlag 	        profileFlags		= new SWGFlag(3, 6);
	private String 				title				= "";
	private int 				bornDate			= 0;
	private int 				playTime			= 0;
	private int					professionIcon		= 0;
	private String				profession			= "";
	private int 				gcwPoints			= 0;
	private int 				pvpKills			= 0;
	private long 				lifetimeGcwPoints	= 0;
	private int 				lifetimePvpKills	= 0;
	private SWGBitSet 		 	collectionBadges	= new SWGBitSet(3, 16);
	private SWGBitSet		 	collectionBadges2	= new SWGBitSet(3, 17);
	private boolean				showHelmet			= true;
	private boolean				showBackpack		= true;
	// PLAY 06
	private int					adminTag			= 0;
	private int 				currentRank			= 0;
	private float 				rankProgress		= 0;
	private int 				highestRebelRank	= 0;
	private int 				highestImperialRank	= 0;
	private int 				gcwNextUpdate		= 0;
	private String 				home				= "";
	// PLAY 08
	private SWGMap<String, Integer> 		experience			= new SWGMap<>(8, 0, StringType.ASCII);
	private SWGMap<Long, WaypointObject> 	waypoints			= new SWGMap<>(8, 1);
	private boolean 						citizen				= false;
	private int 							guildRankTitle		= 0;
	private int 							activeQuest			= 0;
	private SWGMap<Integer, Integer>		quests				= new SWGMap<>(8, 7);
	private String 							profWheelPosition	= "";
	// PLAY 09
	private int 				experimentFlag		= 0;
	private int 				craftingStage		= 0;
	private long 				nearbyCraftStation	= 0;
	private SWGList<String> 	draftSchemList		= new SWGList<>(9, 3, StringType.ASCII);
	private int 				experimentPoints	= 0;
	private SWGList<String> 	friendsList			= new SWGList<>(9, 7, StringType.ASCII);
	private SWGList<String> 	ignoreList			= new SWGList<>(9, 8, StringType.ASCII);
	private int 				languageId			= 0;
	private SWGList<Long> 		defenders			= new SWGList<>(9, 17); // TODO: Change to set
	private int 				killMeter			= 0;
	private long 				petId				= 0;
	private SWGList<String> 	petAbilities		= new SWGList<>(9, 21);
	private SWGList<String> 	activePetAbilities	= new SWGList<>(9, 22);
	private int startPlayTime;
	
	public PlayerObject(long objectId) {
		super(objectId, BaselineType.PLAY);
		setVolume(0);
	}
	
	public void addWaypoint(WaypointObject waypoint) {
		synchronized(waypoints) {
			if (waypoints.size() < 250) {
			waypoints.put(waypoint.getObjectId(), waypoint);
			waypoints.sendDeltaMessage(this);
			}
			else {
				sendSelf(new ChatSystemMessage(ChatSystemMessage.SystemChatType.SCREEN_AND_CHAT, "@base_player:too_many_waypoints"));
			}
		}
	}
	
	public WaypointObject getWaypoint(long objId) {
		synchronized(waypoints) {
			return waypoints.get(objId);
		}
	}
	public SWGMap<Long, WaypointObject> getWaypoints() {
		synchronized(waypoints) {
			return waypoints;
		}
	}

	public void updateWaypoint(WaypointObject obj) {
		synchronized(waypoints) {
			waypoints.update(obj.getObjectId(), this);
		}
	}
	
	public void removeWaypoint(long objId) {
		synchronized(waypoints) {
			waypoints.remove(objId);
			waypoints.sendDeltaMessage(this);
		}
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
		sendDelta(3, 7, title, StringType.ASCII);
	}
	
	public int getPlayTime() {
		return playTime;
	}

	public void setPlayTime(int playTime) {
		this.playTime = playTime;
		sendDelta(3, 9, playTime);
	}

	public int getGcwPoints() {
		return gcwPoints;
	}

	public void setGcwPoints(int gcwPoints) {
		this.gcwPoints = gcwPoints;
		sendDelta(3, 12, gcwPoints);
	}

	public int getPvpKills() {
		return pvpKills;
	}

	public void setPvpKills(int pvpKills) {
		this.pvpKills = pvpKills;
		sendDelta(3, 13, pvpKills);
	}

	public long getLifetimeGcwPoints() {
		return lifetimeGcwPoints;
	}

	public void setLifetimeGcwPoints(long lifetimeGcwPoints) {
		this.lifetimeGcwPoints = lifetimeGcwPoints;
		sendDelta(3, 14, lifetimeGcwPoints);
	}

	public int getLifetimePvpKills() {
		return lifetimePvpKills;
	}

	public void setLifetimePvpKills(int lifetimePvpKills) {
		this.lifetimePvpKills = lifetimePvpKills;
		sendDelta(3, 15, lifetimePvpKills);
	}
	
	public byte[] getCollectionBadges() {
		return collectionBadges.toByteArray();
	}
	
	public void setCollectionBadges(byte[] collection) {
		this.collectionBadges.clear();
		this.collectionBadges.or(BitSet.valueOf(collection));
		sendDelta(3, 16, collectionBadges);
	}

	public boolean isShowHelmet() {
		return showHelmet;
	}

	public void setShowHelmet(boolean showHelmet) {
		this.showHelmet = showHelmet;
		sendDelta(3, 19, showHelmet);
	}

	public boolean isShowBackpack() {
		return showBackpack;
	}

	public void setShowBackpack(boolean showBackpack) {
		this.showBackpack = showBackpack;
		sendDelta(3, 18, showBackpack);
	}

	public String getProfession() {
		return profession;
	}
	
	public void setProfession(String profession) {
		this.profession = profession;
		sendDelta(3, 11, profession, StringType.ASCII);
	}
	
	public String getBiography() {
		return biography;
	}
	
	public void setBiography(String biography) {
		this.biography = biography;
	}
	
	public void setBornDate(int year, int month, int day) {
		this.bornDate = MathUtils.numberDaysSince(year, month, day, 2000, 12, 31);
	}
	
	public int getBornDate() {
		return bornDate;
	}
	
	public void setAdminTag(AccessLevel access) {
		switch(access) {
			case PLAYER: break;
			case CSR: adminTag = 1; break;
			case DEV: adminTag = 2; break;
			case WARDEN: adminTag = 3; break;
			case QA: adminTag = 4; break;
		}
		sendDelta(6, 2, adminTag);
	}
	
	public void setAdminTag(int tag) {
		this.adminTag = tag;
		sendDelta(6, 2, tag);
	}
	
	public int getCurrentRank() {
		return currentRank;
	}

	public void setCurrentRank(int currentRank) {
		this.currentRank = currentRank;
		sendDelta(6, 3, currentRank);
	}

	public float getRankProgress() {
		return rankProgress;
	}

	public void setRankProgress(float rankProgress) {
		this.rankProgress = rankProgress;
		sendDelta(6, 4, rankProgress);
	}

	public int getHighestRebelRank() {
		return highestRebelRank;
	}

	public void setHighestRebelRank(int highestRebelRank) {
		this.highestRebelRank = highestRebelRank;
		sendDelta(6, 5, highestRebelRank);
	}

	public int getHighestImperialRank() {
		return highestImperialRank;
	}

	public void setHighestImperialRank(int highestImperialRank) {
		this.highestImperialRank = highestImperialRank;
		sendDelta(6, 6, highestImperialRank);
	}

	public int getGcwNextUpdate() {
		return gcwNextUpdate;
	}

	public void setGcwNextUpdate(int gcwNextUpdate) {
		this.gcwNextUpdate = gcwNextUpdate;
		sendDelta(6, 7, gcwNextUpdate);
	}

	public String getHome() {
		return home;
	}

	public void setHome(String home) {
		this.home = home;
		sendDelta(6, 8, home, StringType.ASCII);
	}

	public boolean isCitizen() {
		return citizen;
	}

	public void setCitizen(boolean citizen) {
		this.citizen = citizen;
		sendDelta(6, 9, citizen);
	}

	public int getGuildRankTitle() {
		return guildRankTitle;
	}

	public void setGuildRankTitle(int guildRankTitle) {
		this.guildRankTitle = guildRankTitle;
		sendDelta(6, 13, guildRankTitle);
	}

	public int getActiveQuest() {
		return activeQuest;
	}

	public void setActiveQuest(int activeQuest) {
		this.activeQuest = activeQuest;
		sendDelta(8, 6, activeQuest);
	}

	public void removeFriend(String friend) {
		synchronized (friendsList) {
			friendsList.remove(friend);
		}
		friendsList.sendDeltaMessage(this);
	}

	public void addFriend(String friend) {
		synchronized (friendsList) {
			friendsList.add(friend);
		}
		friendsList.sendDeltaMessage(this);
	}

	public List<String> getFriendsList() {
		return friendsList;
	}

	public void addIgnored(String ignored) {
		synchronized (ignoreList) {
			ignoreList.add(ignored);
		}
		ignoreList.sendDeltaMessage(this);
	}

	public void removeIgnored(String ignored) {
		synchronized (ignoreList) {
			ignoreList.remove(ignored);
		}
		ignoreList.sendDeltaMessage(this);
	}

	public boolean isIgnored(String target) {
		return ignoreList.contains(target);
	}

	public List<String> getIgnoreList() {
		return ignoreList;
	}

	public String getProfWheelPosition() {
		return profWheelPosition;
	}

	public void setProfWheelPosition(String profWheelPosition) {
		this.profWheelPosition = profWheelPosition;
		sendDelta(8, 8, profWheelPosition, StringType.ASCII);
	}
	
	public void setFlagBitmask(PlayerFlags ... flags) {
		boolean changed = false;
		for (PlayerFlags flag : flags) {
			changed |= !flagsList.get(flag.getFlag());
			flagsList.set(flag.getFlag());
		}
		if (changed)
			flagsList.sendDeltaMessage(this);
	}
	
	public void clearFlagBitmask(PlayerFlags ... flags) {
		boolean changed = false;
		for (PlayerFlags flag : flags) {
			changed |= flagsList.get(flag.getFlag());
			flagsList.clear(flag.getFlag());
		}
		if (changed)
			flagsList.sendDeltaMessage(this);
	}
	
	public void toggleFlag(PlayerFlags ... flags) {
		for (PlayerFlags flag : flags)
			flagsList.flip(flag.getFlag());
		flagsList.sendDeltaMessage(this);
	}

	public List<String> getJoinedChannels() {
		return Collections.unmodifiableList(joinedChannels);
	}

	public boolean addJoinedChannel(String path) {
		synchronized (joinedChannels) {
			return true;
			// TODO: Refactor
			/*System.out.println("add joined channel: " + path);
			return !joinedChannels.contains(path) && joinedChannels.add(path);*/
		}
	}

	public boolean removeJoinedChannel(String path) {
		synchronized (joinedChannels) {
			return true;
			// TODO: Refactor
			/*System.out.println("remove joined channel: " + path);
			return joinedChannels.remove(path);*/
		}
	}

	public void setProfessionIcon(int professionIcon) {
		this.professionIcon = professionIcon;
		sendDelta(3, 10, professionIcon);
	}
	
	public int getProfessionIcon() {
		return professionIcon;
	}
	
	public void addDraftSchematic(String schematic) {
		draftSchemList.add(schematic);
		draftSchemList.sendDeltaMessage(this);
	}
	
	public Integer getExperiencePoints(String xpType) {
		return experience.get(xpType);
	}
	
	public void setExperiencePoints(String xpType, int experiencePoints) {
		experience.put(xpType, experiencePoints);
		experience.sendDeltaMessage(this);
	}
	
	public void createChildrenObjects(Player target) {
		SWGObject parent = getParent();
		if (parent != null && parent instanceof CreatureObject)
			target.sendPacket(new UpdatePostureMessage(((CreatureObject)parent).getPosture().getId(), getObjectId()));
		super.createChildrenObjects(target);
	}
	
	public void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb); // 5 variables
		bb.addObject(flagsList); // 4 flags -- 5
		bb.addObject(profileFlags); // 4 flags -- 6
		bb.addAscii(title); // 7
		bb.addInt(bornDate); // Born Date -- 4001 = 12/15/2011 || Number of days after 12/31/2000 -- 8
		bb.addInt(playTime); // 9
		bb.addInt(professionIcon); // 10
		bb.addAscii(profession); // 11
		bb.addInt(gcwPoints); // 12
		bb.addInt(pvpKills); // 13
		bb.addLong(lifetimeGcwPoints); // 14
		bb.addInt(lifetimePvpKills); // 15
		bb.addObject(collectionBadges); // 16
		bb.addObject(collectionBadges2); // 17
		bb.addBoolean(showBackpack); // 18
		bb.addBoolean(showHelmet); // 19
		for (int i = 0; i < 64; i++)
			bb.addByte(0);
		
		bb.incrementOperandCount(15);
	}
	
	public void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb); // 2 variables
		bb.addByte(adminTag); // Admin Tag (0 = none, 1 = CSR, 2 = Developer, 3 = Warden, 4 = QA) -- 2
		bb.addInt(currentRank); // 3
		bb.addFloat(rankProgress); // 4
		bb.addInt(highestImperialRank); // 5
		bb.addInt(highestRebelRank); // 6
		bb.addInt(gcwNextUpdate); // 7
		bb.addAscii(home); // 8
		bb.addBoolean(citizen); // 9
		bb.addAscii(""); // City Region Defender 'region' -- 10
			bb.addBoolean(false); // City Region Defender byte #1
			bb.addBoolean(false); // City Region Defender byte #2
		bb.addAscii(""); // Guild Region Defender 'region' -- 11
			bb.addBoolean(false); // Guild Region Defender byte #1
			bb.addBoolean(false); // Guild Region Defender byte #2
		bb.addLong(0); // General? -- 12
		bb.addAscii(""); // 13
		bb.addInt(0); // Citizen Rank Title? 6 bytes -- 14
		bb.addInt(0); // Environment Flags Override -- 15
		bb.addAscii(""); // Vehicle Attack Command -- 16
		
		bb.incrementOperandCount(15);
	}
	
	public void createBaseline8(Player target, BaselineBuilder bb) {
		super.createBaseline8(target, bb); // 0 variables
		bb.addObject(experience); // 0
		bb.addObject(waypoints); // 1
		bb.addInt(100); // Current Force Power -- 2
		bb.addInt(100); // Max Force Power -- 3
		bb.addInt(0); // Completed Quests (List) -- 4
			bb.addInt(0);
		bb.addInt(0); // Active Quests (List) -- 5
			bb.addInt(0);
		bb.addInt(activeQuest); // Current Quest -- 6
		bb.addObject(quests); // 7
		bb.addAscii(profWheelPosition); // 8
		
		bb.incrementOperandCount(9);
	}
	
	public void createBaseline9(Player target, BaselineBuilder bb) {
		super.createBaseline9(target, bb); // 0 variables
		bb.addInt(experimentFlag); // 0
		bb.addInt(craftingStage); // 1
		bb.addLong(nearbyCraftStation); // 2
		bb.addObject(draftSchemList); // 3
		bb.addInt(0); // Might or might not be a list, two ints that are part of the same delta -- 4
			bb.addInt(0);
		bb.addInt(experimentPoints); // 5
		bb.addInt(0); // Accomplishment Counter - Pre-NGE? -- 6
		bb.addObject(friendsList); // 7
		bb.addObject(ignoreList); // 8
		bb.addInt(languageId); // 9
		bb.addInt(0); // Current Stomach -- 10
		bb.addInt(100); // Max Stomach -- 11
		bb.addInt(0); // Current Drink -- 12
		bb.addInt(100); // Max Drink -- 13
		bb.addInt(0); // Current Consumable -- 14
		bb.addInt(100); // Max Consumable -- 15
		bb.addInt(0); // Group Waypoints -- 16
			bb.addInt(0);
		bb.addObject(defenders); // 17
		bb.addInt(killMeter); // 18
		bb.addInt(0); // Unk -- 19
		bb.addLong(petId); // 20
		bb.addObject(petAbilities); // 21
		bb.addObject(activePetAbilities); // 22
		bb.addByte(0); // Unk  sometimes 0x01 or 0x02 -- 23
		bb.addInt(0); // Unk  sometimes 4 -- 24
		bb.addLong(0); // Unk  Bitmask starts with 0x20 ends with 0x40 -- 25
		bb.addLong(0); // Unk Changes from 6 bytes to 9 -- 26
		bb.addByte(0); // Unk Changes from 6 bytes to 9 -- 27
		bb.addLong(0); // Unk  sometimes 856 -- 28
		bb.addLong(0); // Unk  sometimes 8559 -- 29
		bb.addInt(0); // Residence Time?  Seen as Saturday 28th May 2011 -- 30
		
		bb.incrementOperandCount(31);
	}
	
	public int getStartPlayTime() {
		return startPlayTime;
	}

	public void setStartPlayTime(int startPlayTime) {
		this.startPlayTime = startPlayTime;
	}
}
