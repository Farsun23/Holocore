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
package resources.objects.resource;

import network.packets.swg.zone.baselines.Baseline.BaselineType;
import resources.objects.tangible.TangibleObject;

public class ResourceContainerObject extends TangibleObject {
	
	private static final long serialVersionUID = 1L;
	
	private long	resourceType	= 0;
	private String	resourceName	= "";
	private int		quantity		= 0;
	private int		maxQuantity		= 0;
	private String	parentName		= "";
	private String	displayName		= "";
	
	public ResourceContainerObject(long objectId) {
		super(objectId, BaselineType.RCNO);
	}
	
	public long getResourceType() {
		return resourceType;
	}
	
	public String getResourceName() {
		return resourceName;
	}
	
	public int getQuantity() {
		return quantity;
	}
	
	public int getMaxQuantity() {
		return maxQuantity;
	}
	
	public String getParentName() {
		return parentName;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public void setResourceType(long resourceType) {
		this.resourceType = resourceType;
	}
	
	public void setResourceName(String resourceName) {
		this.resourceName = resourceName;
	}
	
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
	
	public void setMaxQuantity(int maxQuantity) {
		this.maxQuantity = maxQuantity;
	}
	
	public void setParentName(String parentName) {
		this.parentName = parentName;
	}
	
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	
}
