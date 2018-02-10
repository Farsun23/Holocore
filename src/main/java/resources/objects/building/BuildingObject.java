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
package resources.objects.building;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.projectswg.common.concurrency.SynchronizedMap;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.common.data.swgfile.visitors.ObjectData.ObjectDataAttribute;
import com.projectswg.common.data.swgfile.visitors.PortalLayoutData;
import com.projectswg.common.debug.Assert;
import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.network.packets.swg.zone.baselines.Baseline.BaselineType;

import resources.objects.SWGObject;
import resources.objects.cell.CellObject;
import resources.objects.tangible.TangibleObject;
import services.objects.ObjectCreator;

public class BuildingObject extends TangibleObject {
	
	private final Map<String, CellObject> nameToCell;
	private final Map<Integer, CellObject> idToCell;
	
	private WeakReference<PortalLayoutData> portalLayoutData;
	
	public BuildingObject(long objectId) {
		super(objectId, BaselineType.BUIO);
		this.nameToCell = new SynchronizedMap<>();
		this.idToCell = new SynchronizedMap<>();
		this.portalLayoutData = null;
	}
	
	public CellObject getCellByName(String cellName) {
		return nameToCell.get(cellName);
	}
	
	public CellObject getCellByNumber(int cellNumber) {
		return idToCell.get(cellNumber);
	}
	
	public List<CellObject> getCells() {
		return new ArrayList<>(idToCell.values());
	}
	
	@Override
	public void addObject(SWGObject object) {
		super.addObject(object);
		assert object instanceof CellObject : "Object added to building is not a cell!";
		
		CellObject cell = (CellObject) object;
		Assert.test(cell.getNumber() > 0, "Cell Number must be greater than 0!");
		Assert.test(cell.getNumber() < getCellCount(), "Cell Number must be less than the cell count!");
		cell.setCellName(getCellName(cell.getNumber()));
		synchronized (idToCell) {
			Assert.isNull(idToCell.get(cell.getNumber()), "Multiple cells have the same number!");
			idToCell.put(cell.getNumber(), cell);
			nameToCell.put(cell.getCellName(), cell); // Can be multiple cells with the same name
		}
	}
	
	public void populateCells() {
		int cells = getCellCount();
		synchronized (idToCell) {
			for (int i = 1; i < cells; i++) { // 0 is world
				if (idToCell.get(i) != null)
					continue;
				CellObject cell = (CellObject) ObjectCreator.createObjectFromTemplate("object/cell/shared_cell.iff");
				Assert.notNull(cell, "Failed to create a cell!");
				cell.setNumber(i);
				cell.setTerrain(getTerrain());
				addObject(cell);
			}
		}
	}
	
	private int getCellCount() {
		PortalLayoutData data = getPortalLayoutData();
		if (data == null)
			return 0;
		return data.getCells().size();
	}
	
	private String getCellName(int cell) {
		PortalLayoutData data = getPortalLayoutData();
		if (data == null)
			return "";
		return data.getCells().get(cell).getName();
	}
	
	private PortalLayoutData getPortalLayoutData() {
		PortalLayoutData portalLayoutData = (this.portalLayoutData == null) ? null : this.portalLayoutData.get();
		if (portalLayoutData == null) {
			String portalFile = (String) getDataAttribute(ObjectDataAttribute.PORTAL_LAYOUT_FILENAME);
			if (portalFile == null || portalFile.isEmpty())
				return null;
			
			portalLayoutData = (PortalLayoutData) ClientFactory.getInfoFromFile(portalFile);
			this.portalLayoutData = new WeakReference<>(portalLayoutData);
		}
		Assert.test(portalLayoutData != null && portalLayoutData.getCells() != null && portalLayoutData.getCells().size() > 0, "Invalid portal layout data!");
		return portalLayoutData;
	}
	
	@Override
	public void save(NetBufferStream stream) {
		super.save(stream);
		stream.addByte(0);
	}
	
	@Override
	public void read(NetBufferStream stream) {
		super.read(stream);
		stream.getByte();
	}
	
}
