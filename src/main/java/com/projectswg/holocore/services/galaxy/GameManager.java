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
package com.projectswg.holocore.services.galaxy;

import com.projectswg.holocore.services.collections.CollectionBadgeManager;
import com.projectswg.holocore.services.collections.CollectionService;
import com.projectswg.holocore.services.combat.CombatManager;
import com.projectswg.holocore.services.commands.BuffService;
import com.projectswg.holocore.services.commands.CommandService;
import com.projectswg.holocore.services.commands.EntertainmentService;
import com.projectswg.holocore.services.crafting.CraftingManager;
import com.projectswg.holocore.services.experience.ExperienceManager;
import com.projectswg.holocore.services.faction.FactionManager;
import com.projectswg.holocore.services.group.GroupService;
import com.projectswg.holocore.services.sui.SuiService;
import me.joshlarson.jlcommon.control.Manager;

public class GameManager extends Manager {
	
	public GameManager() {
		addChildService(new CommandService());
		addChildService(new ConnectionService());
		addChildService(new SuiService());
		addChildService(new CollectionService());
		addChildService(new CollectionBadgeManager());
		addChildService(new EnvironmentService());
		addChildService(new FactionManager());
		addChildService(new GroupService());
		addChildService(new SkillModService());
		addChildService(new EntertainmentService());
		addChildService(new CombatManager());
		addChildService(new ExperienceManager());
		addChildService(new BuffService());
		addChildService(new CraftingManager());
	}
	
}
