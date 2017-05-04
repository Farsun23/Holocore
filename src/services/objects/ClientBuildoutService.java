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
package services.objects;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.projectswg.common.control.Service;
import com.projectswg.common.data.CRC;
import com.projectswg.common.data.info.RelationalServerData;
import com.projectswg.common.data.info.RelationalServerFactory;
import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.debug.Log;

import intents.object.ObjectCreatedIntent;
import intents.player.PlayerTransformedIntent;
import resources.buildout.BuildoutArea;
import resources.buildout.BuildoutArea.BuildoutAreaBuilder;
import resources.buildout.BuildoutAreaGrid;
import resources.config.ConfigFile;
import resources.objects.SWGObject;
import resources.objects.SWGObject.ObjectClassification;
import resources.objects.building.BuildingObject;
import resources.objects.cell.CellObject;
import resources.server_info.DataManager;
import resources.server_info.StandardLog;

public class ClientBuildoutService extends Service {
	
	private static final String GET_BUILDOUT_AREAS = "SELECT * FROM areas ORDER BY area_name ASC, event ASC";
	private static final String GET_BUILDOUT_AREA = "SELECT * FROM areas WHERE id = ?";
	private static final String GET_ADDITIONAL_OBJECTS_SQL = "SELECT terrain, template, x, y, z, heading, cell_id, radius, building_name "
			+ "FROM additional_buildouts WHERE active = 1";
	private static final String GET_BUILDING_INFO_SQL = "SELECT object_id FROM buildings WHERE building_id = ?";
	
	private final BuildoutAreaGrid areaGrid;
	private final Map<Integer, BuildoutArea> areasById;
	
	public ClientBuildoutService() {
		areaGrid = new BuildoutAreaGrid();
		areasById = new Hashtable<>(1000); // Number of buildout areas
		
		registerForIntent(PlayerTransformedIntent.class, pti -> handlePlayerTransform(pti));
		registerForIntent(ObjectCreatedIntent.class, oci -> handleObjectCreated(oci));
	}
	
	public Map<Long, SWGObject> loadClientObjects() {
		Map<Long, SWGObject> objects;
		long startTime = StandardLog.onStartLoad("client objects");
		try {
			loadAreas(getEvents());
			if (DataManager.getConfig(ConfigFile.PRIMARY).getBoolean("LOAD-OBJECTS", true))
				objects = loadObjects();
			else
				objects = new HashMap<>();
		} catch (SQLException e) {
			objects = new HashMap<>();
			Log.e(e);
		}
		StandardLog.onEndLoad(objects.size(), "client objects", startTime);
		return objects;
	}
	
	public Map<Long, SWGObject> loadClientObjectsByArea(int areaId) {
		try {
			try (RelationalServerData data = RelationalServerFactory.getServerData("buildout/areas.db", "areas")) {
				PreparedStatement ps = data.prepareStatement(GET_BUILDOUT_AREA);
				ps.setInt(1, areaId);
				try (ResultSet set = ps.executeQuery()) {
					if (set.next())
						loadArea(createArea(set, new AreaIndexes(set)));
				}
			}
			return loadObjects(areaId);
		} catch (SQLException e) {
			Log.e(e);
			return new HashMap<>();
		}
	}
	
	private Map<Long, SWGObject> loadObjects() throws SQLException {
		Map<Long, SWGObject> objects = new Hashtable<>(115000);
		try (BuildoutLoader loader = new BuildoutLoader(areasById, objects, new File("serverdata/buildout/objects.sdb"))) {
			while (loader.loadNextEntry()) {
				if (!loader.isValidNextEntry())
					continue;
				loader.createObject();
			}
		}
		addAdditionalObjects(objects);
		return objects;
	}
	
	private Map<Long, SWGObject> loadObjects(int areaId) throws SQLException {
		Map<Long, SWGObject> objects = new Hashtable<>();
		try (BuildoutLoader loader = new BuildoutLoader(areasById, objects, new File("serverdata/buildout/objects.sdb"))) {
			while (loader.loadNextEntry()) {
				if (!loader.isAreaId(areaId))
					continue;
				loader.createObject();
			}
		}
		return objects;
	}
	
	private void addAdditionalObjects(Map<Long, SWGObject> buildouts) throws SQLException {
		try (RelationalServerData data = RelationalServerFactory.getServerData("buildout/additional_buildouts.db", "additional_buildouts")) {
			try (ResultSet set = data.executeQuery(GET_ADDITIONAL_OBJECTS_SQL)) {
				set.setFetchSize(4*1024);
				while (set.next()) {
					createAdditionalObject(buildouts, set);
				}
			}
		}
	}
	
	private void createAdditionalObject(Map<Long, SWGObject> buildouts, ResultSet set) throws SQLException {
		try {
			SWGObject obj = ObjectCreator.createObjectFromTemplate(set.getString("template"));
			Location l = new Location();
			l.setX(set.getFloat("x"));
			l.setY(set.getFloat("y"));
			l.setZ(set.getFloat("z"));
			l.setTerrain(Terrain.getTerrainFromName(set.getString("terrain")));
			l.setHeading(set.getFloat("heading"));
			obj.setLocation(l);
			obj.setClassification(ObjectClassification.BUILDOUT);
			obj.setPrefLoadRange(set.getFloat("radius"));
			checkParent(buildouts, obj, set.getString("building_name"), set.getInt("cell_id"));
			buildouts.put(obj.getObjectId(), obj);
		} catch (NullPointerException e) {
			Log.e("File: %s", set.getString("template"));
		}
	}
	
	private void checkParent(Map<Long, SWGObject> objects, SWGObject obj, String buildingName, int cellId) throws SQLException {
		try (RelationalServerData data = RelationalServerFactory.getServerData("building/building.db", "buildings")) {
			try (PreparedStatement statement = data.prepareStatement(GET_BUILDING_INFO_SQL)) {
				statement.setString(1, buildingName);
				try (ResultSet set = statement.executeQuery()) {
					if (!set.next()) {
						Log.e("Unknown building name: %s", buildingName);
						return;
					}
					long buildingId = set.getLong("object_id");
					if (buildingId == 0)
						return;
					SWGObject buildingUncasted = objects.get(buildingId);
					if (buildingUncasted == null) {
						Log.e("Building not found in map: %s / %d", buildingName, buildingId);
						return;
					}
					if (!(buildingUncasted instanceof BuildingObject)) {
						Log.e("Building is not an instance of BuildingObject: %s", buildingName);
						return;
					}
					CellObject cell = ((BuildingObject) buildingUncasted).getCellByNumber(cellId);
					if (cell == null) {
						Log.e("Cell is not found! Building: %s Cell: %d", buildingName, cellId);
						return;
					}
					obj.moveToContainer(cell);
				}
			}
		}
	}
	
	private List<String> getEvents() {
		List <String> events = new ArrayList<>();
		String eventStr = DataManager.getConfig(ConfigFile.FEATURES).getString("EVENTS", "");
		String [] eventArray = eventStr.split(",");
		for (String event : eventArray) {
			event = event.toLowerCase(Locale.US);
			if (!event.isEmpty())
				events.add(event);
		}
		return events;
	}
	
	private void loadAreas(List <String> events) throws SQLException {
		BuildoutArea primary = null; // Stored as "best area" for what we want to load
		try (RelationalServerData data = RelationalServerFactory.getServerData("buildout/areas.db", "areas")) {
			try (ResultSet set = data.executeQuery(GET_BUILDOUT_AREAS)) {
				areaGrid.clear();
				areasById.clear();
				AreaIndexes ind = new AreaIndexes(set);
				boolean loaded = false;
				while (set.next()) {
					BuildoutArea area = createArea(set, ind);
					area.setLoaded(false);
					if (area.getEvent().isEmpty() && (primary == null || !area.getName().equals(primary.getName()))) {
						if (!loaded && primary != null)
							loadArea(area);
						loaded = false;
						primary = area; // Primary area, no event
					}
					if (events.contains(area.getEvent())) {
						loadArea(area);
						loaded = true;
					}
				}
				if (!loaded && primary != null)
					loadArea(primary);
			}
		}
	}
	
	private void loadArea(BuildoutArea area) {
		area.setLoaded(true);
		areaGrid.addBuildoutArea(area);
		areasById.put(area.getId(), area);
	}
	
	private void handlePlayerTransform(PlayerTransformedIntent pti) {
		setObjectArea(pti.getPlayer());
	}
	
	private void handleObjectCreated(ObjectCreatedIntent oci) {
		setObjectArea(oci.getObject());
	}
	
	private void setObjectArea(SWGObject obj) {
		if (obj.getParent() != null) {
			obj.setBuildoutArea(null);
			return;
		}
		Location world = obj.getWorldLocation();
		BuildoutArea area = obj.getBuildoutArea();
		if (area == null || !isWithin(area, world.getTerrain(), world.getX(), world.getZ())) {
			area = getAreaForObject(obj);
			obj.setBuildoutArea(area);
		}
	}
	
	private BuildoutArea createArea(ResultSet set, AreaIndexes ind) throws SQLException {
		BuildoutAreaBuilder bldr = new BuildoutAreaBuilder()
			.setId(set.getInt(ind.id))
			.setName(set.getString(ind.name))
			.setTerrain(Terrain.getTerrainFromName(set.getString(ind.terrain)))
			.setEvent(set.getString(ind.event))
			.setX1(set.getDouble(ind.x1))
			.setZ1(set.getDouble(ind.z1))
			.setX2(set.getDouble(ind.x2))
			.setZ2(set.getDouble(ind.z2))
			.setAdjustCoordinates(set.getBoolean(ind.adjust))
			.setTranslationX(set.getDouble(ind.transX))
			.setTranslationZ(set.getDouble(ind.transZ));
		return bldr.build();
	}
	
	private BuildoutArea getAreaForObject(SWGObject obj) {
		Location l = obj.getWorldLocation();
		return areaGrid.getBuildoutArea(l.getTerrain(), l.getX(), l.getZ());
	}
	
	private boolean isWithin(BuildoutArea area, Terrain t, double x, double z) {
		return area.getTerrain() == t && x >= area.getX1() && x <= area.getX2() && z >= area.getZ1() && z <= area.getZ2();
	}
	
	private static class BuildoutLoader implements AutoCloseable {
		
		private final Map<Integer, BuildoutArea> areas;
		private final Map<Long, SWGObject> objects;
		private final SdbLoader loader;
		private final Location location;
		private final ObjectCreationData creationData;
		private BuildoutArea previousArea;
		private String line;
		
		public BuildoutLoader(Map<Integer, BuildoutArea> areas, Map<Long, SWGObject> objects, File file) {
			this.areas = areas;
			this.objects = objects;
			this.loader = new SdbLoader(file);
			this.creationData = new ObjectCreationData();
			this.previousArea = areas.values().iterator().next();
			this.location = new Location(0, 0, 0, previousArea.getTerrain());
			this.line = "";
			loader.loadNextLine(); // Skip column names
			loader.loadNextLine(); // Skip data types
		}
		
		@Override
		public void close() {
			loader.close();
		}
		
		public boolean loadNextEntry() {
			line = loader.loadNextLine();
			if (line != null) {
				parse();
				return true;
			}
			return false;
		}
		
		public boolean isAreaId(int areaId) {
			return creationData.areaId == areaId;
		}
		
		public boolean isValidNextEntry() {
			return areas.containsKey(creationData.areaId);
		}
		
		public void createObject() {
			SWGObject obj = ObjectCreator.createObjectFromTemplate(creationData.id, CRC.getString(creationData.templateCrc));
			obj.setClassification(creationData.snapshot ? ObjectClassification.SNAPSHOT : ObjectClassification.BUILDOUT);
			obj.setPrefLoadRange(creationData.radius);
			setObjectLocation(obj);
			setCellNumber(obj);
			if (obj instanceof BuildingObject)
				((BuildingObject) obj).populateCells();
			objects.put(obj.getObjectId(), obj);
		}
		
		private void setObjectLocation(SWGObject obj) {
			location.setPosition(creationData.x, creationData.y, creationData.z);
			location.setOrientation(creationData.orientationX, creationData.orientationY, creationData.orientationZ, creationData.orientationW);
			if (previousArea.getId() != creationData.areaId)
				location.setTerrain(areas.get(creationData.areaId).getTerrain());
			obj.setLocation(location);
		}
		
		private void setCellNumber(SWGObject obj) {
			if (creationData.cellIndex != 0) {
				BuildingObject building = (BuildingObject) objects.get(creationData.containerId);
				CellObject cell = building.getCellByNumber(creationData.cellIndex);
				obj.moveToContainer(cell);
			}
		}
		
		private void parse() {
			int prevIndex = 0;
			int nextIndex = 0;
			for (int i = 0; i < 15; i++) { // 14 expected variables
				nextIndex = line.indexOf('\t', prevIndex);
				parseValue(getNextValue(prevIndex, nextIndex), i);
				prevIndex = nextIndex+1;
			}
		}
		
		private void parseValue(Number n, int index) {
			switch (index) {
				case 1: creationData.id = n.longValue(); break;
				case 2: creationData.snapshot = n.longValue() != 0; break;
				case 3: creationData.areaId = n.intValue(); break;
				case 4: creationData.templateCrc = n.intValue(); break;
				case 5: creationData.containerId = n.longValue(); break;
				case 6: creationData.x = n.doubleValue(); break;
				case 7: creationData.y = n.doubleValue(); break;
				case 8: creationData.z = n.doubleValue(); break;
				case 9: creationData.orientationX = n.doubleValue(); break;
				case 10: creationData.orientationY = n.doubleValue(); break;
				case 11: creationData.orientationZ = n.doubleValue(); break;
				case 12: creationData.orientationW = n.doubleValue(); break;
				case 13: creationData.radius = n.doubleValue(); break;
				case 14: creationData.cellIndex = n.intValue(); break;
			}
		}
		
		private Number getNextValue(int prevIndex, int nextIndex) {
			if (nextIndex == -1)
				return parse(line.substring(prevIndex));
			else
				return parse(line.substring(prevIndex, nextIndex));
		}
		
		private Number parse(String str) {
			if (str.indexOf('.') == -1)
				return Long.valueOf(str);
			return Double.valueOf(str);
		}
		
	}
	
	private static class ObjectCreationData {
		
		public long id;
		public boolean snapshot;
		public int areaId;
		public int templateCrc;
		public long containerId;
		public double x;
		public double y;
		public double z;
		public double orientationX;
		public double orientationY;
		public double orientationZ;
		public double orientationW;
		public double radius;
		public int cellIndex;
		
	}
	
	private static class SdbLoader implements AutoCloseable {
		
		private final BufferedReader reader;
		
		public SdbLoader(File file) {
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(file));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				br = null;
			}
			this.reader = br;
		}
		
		@Override
		public void close() {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		public String loadNextLine() {
			try {
				return reader.readLine();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
		
	}
	
	private static class AreaIndexes {
		
		private int id;
		private int name;
		private int terrain;
		private int event;
		private int x1, z1, x2, z2;
		private int adjust;
		private int transX, transZ;
		
		public AreaIndexes(ResultSet set) throws SQLException {
			id = set.findColumn("id");
			name = set.findColumn("area_name");
			terrain = set.findColumn("terrain");
			event = set.findColumn("event");
			x1 = set.findColumn("min_x");
			z1 = set.findColumn("min_z");
			x2 = set.findColumn("max_x");
			z2 = set.findColumn("max_z");
			adjust = set.findColumn("adjust_coordinates");
			transX = set.findColumn("translate_x");
			transZ = set.findColumn("translate_z");
		}
		
	}
	
}
