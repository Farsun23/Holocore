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
package resources.containers;

import resources.objects.SWGObject;

public enum ContainerPermissionsType {
	DEFAULT		(new DefaultPermissions()),
	INVENTORY	(new InventoryPermissions());
	
	private final ContainerPermissions permissions;
	
	ContainerPermissionsType(ContainerPermissions permissions) {
		this.permissions = permissions;
	}
	
	public ContainerPermissions getContainerPermissions() {
		return permissions;
	}
	
	public boolean canView(SWGObject viewer, SWGObject container) {
		return permissions.canView(viewer, container);
	}
	
	public boolean canEnter(SWGObject viewer, SWGObject container) {
		return permissions.canEnter(viewer, container);
	}
	
	public boolean canRemove(SWGObject viewer, SWGObject container) {
		return permissions.canRemove(viewer, container);
	}
	
	public boolean canMove(SWGObject viewer, SWGObject container) {
		return permissions.canMove(viewer, container);
	}
	
	public boolean canAdd(SWGObject viewer, SWGObject container) {
		return permissions.canAdd(viewer, container);
	}
	
}
