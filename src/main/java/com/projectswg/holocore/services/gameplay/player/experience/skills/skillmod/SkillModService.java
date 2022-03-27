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
package com.projectswg.holocore.services.gameplay.player.experience.skills.skillmod;

import com.projectswg.common.data.encodables.oob.ProsePackage;
import com.projectswg.common.data.encodables.oob.StringId;
import com.projectswg.common.data.encodables.tangible.Race;
import com.projectswg.common.data.info.RelationalServerData;
import com.projectswg.common.data.info.RelationalServerFactory;
import com.projectswg.holocore.ProjectSWG;
import com.projectswg.holocore.intents.gameplay.player.experience.skills.SkillModIntent;
import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent;
import com.projectswg.holocore.intents.support.objects.swg.ContainerTransferIntent;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.services.gameplay.player.experience.skills.skillmod.adjust.*;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;

import java.sql.PreparedStatement;
import java.util.*;
import java.util.function.Function;

public class SkillModService extends Service {
	
	private static final int HEALTH_POINTS_PER_STAMINA 			= 2;
	private static final int HEALTH_POINTS_PER_CONSTITUTION 	= 8;
	private static final int ACTION_POINTS_PER_STAMINA 			= 8;
	private static final int ACTION_POINTS_PER_CONSTITUTION 	= 2;	
	private static final String GET_PLAYER_LEVELS_SQL = "SELECT * FROM player_levels WHERE combat_level = ?";
	private static final String GET_RACIAL_STATS_SQL = "SELECT * FROM racial_stats WHERE level = ?";
	
	private final RelationalServerData playerLevelDatabase;
	private final RelationalServerData racialStatsDatabase;

	private final Map<String, Function<SkillModAdjust, Collection<SkillModAdjust>>> skillModAdjusters;
	
	public SkillModService() {
		
		playerLevelDatabase = RelationalServerFactory.getServerData("player/player_levels.db", "player_levels");
		if (playerLevelDatabase == null)
			throw new ProjectSWG.CoreException("Unable to load player_levels.sdb file for SkillTemplateService");

		PreparedStatement getPlayerLevelStatement = playerLevelDatabase.prepareStatement(GET_PLAYER_LEVELS_SQL);
		
		racialStatsDatabase = RelationalServerFactory.getServerData("player/racial_stats.db", "racial_stats");
		if (racialStatsDatabase == null)
			throw new ProjectSWG.CoreException("Unable to load racial_stats.sdb file for SkillTemplateService");

		PreparedStatement getRacialStatsStatement = racialStatsDatabase.prepareStatement(GET_RACIAL_STATS_SQL);
		
		skillModAdjusters = new HashMap<>();
		skillModAdjusters.put("agility", new AgilityAdjustFunction());
		skillModAdjusters.put("agility_modified", new AgilityAdjustFunction());
		skillModAdjusters.put("luck", new LuckAdjustFunction());
		skillModAdjusters.put("luck_modified", new LuckAdjustFunction());
		skillModAdjusters.put("precision", new PrecisionAdjustFunction());
		skillModAdjusters.put("precision_modified", new PrecisionAdjustFunction());
		skillModAdjusters.put("strength", new StrengthAdjustFunction());
		skillModAdjusters.put("strength_modified", new StrengthAdjustFunction());
		skillModAdjusters.put("expertise_dodge", new SingleModAdjustFunction("display_only_dodge", 100));
		skillModAdjusters.put("expertise_innate_protection_all", new InnateArmorAdjustFunction());
		skillModAdjusters.put("expertise_innate_protection_kinetic", new SingleModAdjustFunction("kinetic"));
		skillModAdjusters.put("expertise_innate_protection_energy", new SingleModAdjustFunction("energy"));
		skillModAdjusters.put("expertise_saber_block", new SingleModAdjustFunction("display_only_parry", 100));
		skillModAdjusters.put("expertise_glancing_blow_all", new SingleModAdjustFunction("display_only_glancing_blow", 100));
	}
	
	@Override
	public boolean terminate() {
		playerLevelDatabase.close();
		racialStatsDatabase.close();
		return super.terminate();
	}
	
	@IntentHandler
	private void handleContainerTransferIntent(ContainerTransferIntent cti){

		if (cti.getObject().getOwner() == null)
		    return;
		
		CreatureObject creature = cti.getObject().getOwner().getCreatureObject();
	
		for (Map.Entry<String, String> attributes : cti.getObject().getAttributes().entrySet()){
			if(attributes.getKey().startsWith("cat_stat_mod_bonus") || attributes.getKey().startsWith("cat_skill_mod_bonus")){
				String[] splitModName = attributes.getKey().split(":",2);
				String modName = splitModName[1];
				int modValue = Integer.parseInt(attributes.getValue());

				if(cti.getContainer().getObjectId() == creature.getObjectId()){
					adjustSkillmod(creature, modName, 0, modValue);
					updateSkillModHamValues(creature, modName,modValue);
				}else if(cti.getOldContainer() != null){
					if(cti.getOldContainer().getObjectId() == creature.getObjectId()){
						adjustSkillmod(creature, modName, 0, -modValue);
						updateSkillModHamValues(creature, modName, -modValue);
					}
				}				
			}
		}
	}

	@IntentHandler
	private void handleSkillModIntent(SkillModIntent smi) {
		for (CreatureObject creature : smi.getAffectedCreatures()) {
			String skillModName = smi.getSkillModName();
			
			int adjustBase = smi.getAdjustBase();
			int adjustModifier = smi.getAdjustModifier();
			
			adjustSkillmod(creature, skillModName, adjustBase, adjustModifier);
			updateSkillModHamValues(creature, skillModName, adjustBase + adjustModifier);
		}
	}
	
	private void adjustSkillmod(CreatureObject creature, String skillModName, int adjustBase, int adjustModifier) {
		creature.adjustSkillmod(skillModName, adjustBase, adjustModifier);
		
		if (skillModAdjusters.containsKey(skillModName)) {
			Function<SkillModAdjust, Collection<SkillModAdjust>> modIntentConsumer = skillModAdjusters.get(skillModName);
			SkillModAdjust inputAdjust = new SkillModAdjust(skillModName, adjustBase, adjustModifier);
			
			Collection<SkillModAdjust> outputAdjusts = modIntentConsumer.apply(inputAdjust);
			
			for (SkillModAdjust outputAdjust : outputAdjusts) {
				int outputBase = outputAdjust.getBase();
				int outputModifier = outputAdjust.getModifier();
				String outputName = outputAdjust.getName();
				
				adjustSkillmod(creature, outputName, outputBase, outputModifier);
			}
		}
	}
	
	private void updateSkillModHamValues(CreatureObject creature, String skillModName, int modifer) {
		int newHealth = 0;
		int newAction = 0;

		if(skillModName.equals("constitution_modified")){
			newHealth = HEALTH_POINTS_PER_CONSTITUTION * modifer;
			newAction = ACTION_POINTS_PER_CONSTITUTION * modifer;
		}else if (skillModName.equals("stamina_modified")){
			newHealth = HEALTH_POINTS_PER_STAMINA * modifer;
			newAction = ACTION_POINTS_PER_STAMINA * modifer;
		}
		
		if (newHealth != 0){
			creature.setMaxHealth(creature.getMaxHealth() + newHealth);
			
			if (!creature.isInCombat()) {
				// Don't replenish  health to 100% if creature is in combat
				creature.setHealth(creature.getMaxHealth());
			}
		}
		
		if (newAction !=0){
			creature.setMaxAction(creature.getMaxAction() + newAction);
			
			if (!creature.isInCombat()) {
				// Don't replenish action to 100% if creature is in combat
				creature.setAction(creature.getMaxAction());
			}
		}
	}

	private String getRaceColumnAbbr(Race race){

		switch (race) {
			case HUMAN_MALE:
			case HUMAN_FEMALE:
				return "hum";
			case TRANDOSHAN_MALE:
			case TRANDOSHAN_FEMALE:
				return "tran";
			case TWILEK_MALE:
			case TWILEK_FEMALE:
				return "twi";
			case BOTHAN_MALE:
			case BOTHAN_FEMALE:
				return "both";
			case ZABRAK_MALE:
			case ZABRAK_FEMALE:
				return "zab";
			case RODIAN_MALE:
			case RODIAN_FEMALE:
				return "rod";
			case MONCAL_MALE:
			case MONCAL_FEMALE:
				return "mon";
			case WOOKIEE_MALE:
			case WOOKIEE_FEMALE:
				return "wok";
			case SULLUSTAN_MALE:
			case SULLUSTAN_FEMALE:
				return "sul";
			case ITHORIAN_MALE:
			case ITHORIAN_FEMALE:
				return "ith";
			default:
				return "";
		}
	}
	
	private void sendSystemMessage(Player target, String id, Object... objects) {
		if (target != null){
			SystemMessageIntent.broadcastPersonal(target, new ProsePackage(new StringId("@spam:" + id), objects));
		}
	}
	
	public enum SkillModTypes{
		LUCK		("_luck","_lck","level_up_stat_gain_0"),
		PRECISION	("_precision","_pre","level_up_stat_gain_1"),
		STRENGTH	("_strength","_str","level_up_stat_gain_2"),
		CONSTITUTION("_constitution","_con","level_up_stat_gain_3"),
		STAMINA		("_stamina","_sta","level_up_stat_gain_4"),
		AGILITY		("_agility","_agi","level_up_stat_gain_5"),
		HEALTH_REGEN("_health_regen",null,null),
		ACTION_REGEN("_action_regen",null,null);
		
		private final String professionMod;
		private final String raceMod;
		private final String levelUpMessage;
		
		SkillModTypes(String profession, String race, String levelUpMessage){
			this.professionMod = profession;
			this.raceMod = race;
			this.levelUpMessage = levelUpMessage;
		}
		
		public String getProfession(){
			return this.professionMod;
		}
		
		public String getRace(){
			return this.raceMod;
		}
		
		public String getLevelUpMessage(){
			return this.levelUpMessage;
		}
		
		public boolean isRaceModDefined(){
			return raceMod !=null;
		}

		public boolean isLevelUpMessageDefined(){
			return levelUpMessage != null;
		}
	}	
}
