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
package resources.radial;

public class RadialOption {
	
	private int id;
	private int parentId;
	private int optionType; // 1 unless the option text isn't listed in the radial item list (datatable). - ANHWiki
	private String text;
	
	public RadialOption() {
		
	}
	
	public RadialOption(int parentId, RadialItem id) {
		this(parentId, id.getId(), id.getOptionType(), id.getText());
	}
	
	public RadialOption(int parentId, int id, int optionType, String text) {
		this.parentId = parentId;
		this.id = id;
		this.optionType = optionType;
		this.text = text;
	}
	
	public void setParentId(byte parentId) { this.parentId = parentId; }
	public void setId(short id) { this.id = id; }
	public void setOptionType(byte optionType) { this.optionType = optionType; }
	public void setText(String text) { this.text = text; }
	
	public int getParentId() { return parentId; }
	public int getId() { return id; }
	public int getOptionType() { return optionType; }
	public String getText() { return text; }
	
	@Override
	public String toString() { 
		return String.format("ID=%d Parent=%d Option=%d Text=%s", id, parentId, optionType, text); 
	}
	
}
