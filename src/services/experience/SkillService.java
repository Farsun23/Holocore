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
package services.experience;

import intents.experience.LevelChangedIntent;
import intents.experience.SkillBoxGrantedIntent;
import intents.network.GalacticPacketIntent;
import java.util.HashMap;
import java.util.Map;
import network.packets.Packet;
import network.packets.swg.zone.object_controller.ChangeRoleIconChoice;
import resources.client_info.ClientFactory;
import resources.client_info.visitors.DatatableData;
import resources.control.Intent;
import resources.control.Service;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.objects.player.PlayerObject;
import resources.server_info.Log;

/**
 *
 * @author Mads
 */
public final class SkillService extends Service {
	
	// Maps icon index to qualifying skill.
	private Map<Integer, String> roleIconMap;
	private Map<String, SkillData> skillDataMap;
	
	public SkillService() {
		roleIconMap = new HashMap<>();
		skillDataMap = new HashMap<>();
		registerForIntent(SkillBoxGrantedIntent.TYPE);
		registerForIntent(GalacticPacketIntent.TYPE);
	}

	@Override
	public void onIntentReceived(Intent i) {
		switch(i.getType()) {
			case SkillBoxGrantedIntent.TYPE: handleSkillAddIntent((SkillBoxGrantedIntent) i); break;
			case GalacticPacketIntent.TYPE: handleGalacticPacket((GalacticPacketIntent) i);
		}
	}
	
	@Override
	public boolean initialize() {
		DatatableData roleIconTable = (DatatableData) ClientFactory.getInfoFromFile("datatables/role/role.iff");

		for (int i = 0; i < roleIconTable.getRowCount(); i++) {
			int iconIndex = (int) roleIconTable.getCell(i, 0);
			String qualifyingSkill = (String) roleIconTable.getCell(i, 2);
			
			roleIconMap.put(iconIndex, qualifyingSkill);
		}
		
		DatatableData skillsTable = (DatatableData) ClientFactory.getInfoFromFile("datatables/skill/skills.iff", true);

		for (int i = 0; i < skillsTable.getRowCount(); i++) {
			String skillName = (String) skillsTable.getCell(i, 0);
			String requiredSkillString = (String) skillsTable.getCell(i, 11);
			String xpType = (String) skillsTable.getCell(i, 12);
			int xpCost = (int) skillsTable.getCell(i, 13);
			String commandsString = (String) skillsTable.getCell(i, 21);
			String schematicsString = (String) skillsTable.getCell(i, 23);
			
			String skillModsString = (String) skillsTable.getCell(i, 22);
			Map<String, Integer> skillMods = new HashMap<>();
			if(!skillModsString.isEmpty()) {
				String[] skillModsStrings = skillModsString.split(",");

				for(String skillModString : skillModsStrings) {
					String[] values = skillModString.split("=");
					skillMods.put(values[0], Integer.parseInt(values[1]));
				}
			}
			
			String[] requiredSkills = requiredSkillString.split(",");
			if(requiredSkills.length == 1 && requiredSkills[0].isEmpty()) {
				requiredSkills = new String[0];
			}
			
			String[] commands = commandsString.split(",");
			if(commands.length == 1 && commands[0].isEmpty()) {
				commands = new String[0];
			}
			
			String[] schematics = schematicsString.split(",");
			if(schematics.length == 1 && schematics[0].isEmpty()) {
				schematics = new String[0];
			}
			
			SkillData skillData = new SkillData(
					requiredSkills,
					xpType,
					xpCost,
					commands,
					skillMods,
					schematics
			);
			
			skillDataMap.put(skillName, skillData);
		}
		
		return super.initialize();
	}
	
	private void handleSkillAddIntent(SkillBoxGrantedIntent intent) {
		String skillName = intent.getSkillName();
		CreatureObject target = intent.getTarget();
		SkillData skillData = skillDataMap.get(skillName);
		String[] requiredSkills = skillData.requiredSkills;
		PlayerObject playerObject = target.getPlayerObject();
		
		for(String requiredSkill : requiredSkills) {
			if(!target.hasSkill(requiredSkill)) {
				Log.w(this, "%s lacks required skill %s before being granted skill %s", target, requiredSkill, skillName);
				return;
			}
		}
		
		target.addSkill(skillName);
		
		for(String commandName : skillData.commands) {
			target.addAbility(commandName);
		}
		
		skillData.skillMods.forEach((skillModName, skillModValue) -> target.adjustSkillmod(skillModName, 0, skillModValue));
		
		for(String schematic : skillData.schematics) {
			playerObject.addDraftSchematic(schematic);
		}
		
		Log.d(this, "%s was given skill %s", target, skillName);
	}
	
	private void handleGalacticPacket(GalacticPacketIntent gpi) {
		Packet packet = gpi.getPacket();
		if (packet instanceof ChangeRoleIconChoice) {
			ChangeRoleIconChoice iconChoice = (ChangeRoleIconChoice) packet;
			
			int chosenIcon = iconChoice.getIconChoice();
			SWGObject object = gpi.getObjectManager().getObjectById(iconChoice.getObjectId());
			
			if(object instanceof CreatureObject) {
				changeRoleIcon((CreatureObject) object, chosenIcon);
			} else {
				Log.e(this, "Could not alter role icon for object %s because it's not a CreatureObject", object);
			}
		}
	}
	
	private void changeRoleIcon(CreatureObject creature, int chosenIcon) {
		String qualifyingSkill = roleIconMap.get(chosenIcon);
		
		if (qualifyingSkill != null) {
			if (creature.hasSkill(qualifyingSkill)) {
				PlayerObject playerObject = creature.getPlayerObject();
				
				if(playerObject != null) {
					playerObject.setProfessionIcon(chosenIcon);
				} else {
					Log.e(this, "Could not alter role icon for PlayerObject of %s because it has none attached" , creature);
				}
			} else {
				Log.w(this, "%s cannot use role icon %d because they lack the qualifying skill %s", creature, chosenIcon, qualifyingSkill);
			}
		} else {
			Log.w(this, "%s tried to use undefined role icon %d", creature, chosenIcon);
		}
	}
	
	private static class SkillData {
		private String[] requiredSkills;
		private String xpType;
		private int xpCost;
		private String[] commands;
		private Map<String, Integer> skillMods;
		private String[] schematics;

		public SkillData(String[] requiredSkills, String xpType, int xpCost, String[] commands, Map<String, Integer> skillMods, String[] schematics) {
			this.requiredSkills = requiredSkills;
			this.xpType = xpType;
			this.xpCost = xpCost;
			this.commands = commands;
			this.skillMods = skillMods;
			this.schematics = schematics;
		}
		
	}
	
}
