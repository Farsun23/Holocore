package services.objects;

import intents.GalacticPacketIntent;
import intents.swgobject_events.SWGObjectEventIntent;
import intents.swgobject_events.SWGObjectMovedIntent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import main.ProjectSWG;
import network.packets.swg.zone.HeartBeatMessage;
import network.packets.swg.zone.ParametersMessage;
import network.packets.swg.zone.UpdatePvpStatusMessage;
import network.packets.swg.zone.chat.ChatOnConnectAvatar;
import network.packets.swg.zone.chat.VoiceChatStatus;
import network.packets.swg.zone.insertion.ChatServerStatus;
import network.packets.swg.zone.insertion.CmdStartScene;
import network.packets.swg.zone.insertion.SelectCharacter;
import resources.Location;
import resources.Race;
import resources.Terrain;
import resources.client_info.ClientFactory;
import resources.client_info.visitors.ObjectData;
import resources.client_info.visitors.SlotArrangementData;
import resources.client_info.visitors.SlotDefinitionData;
import resources.client_info.visitors.SlotDescriptorData;
import resources.containers.ObjectSlot;
import resources.control.Intent;
import resources.control.Manager;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.objects.intangible.IntangibleObject;
import resources.objects.player.PlayerObject;
import resources.objects.quadtree.QuadTree;
import resources.objects.tangible.TangibleObject;
import resources.objects.waypoint.WaypointObject;
import resources.objects.weapon.WeaponObject;
import resources.player.Player;
import resources.server_info.ObjectDatabase;
import resources.server_info.ObjectDatabase.Traverser;
import services.player.PlayerManager;

public class ObjectManager extends Manager {
	
	private static final double AWARE_RANGE = 200;
	
	private ClientFactory clientFac;
	
	private ObjectDatabase<SWGObject> objects;
	private Map <String, QuadTree <SWGObject>> quadTree;
	private long maxObjectId;
	
	public ObjectManager() {
		objects = new ObjectDatabase<SWGObject>("odb/objects.db");
		quadTree = new HashMap<String, QuadTree<SWGObject>>();
		maxObjectId = 1;
	}
	
	@Override
	public boolean initialize() {
		registerForIntent(SWGObjectEventIntent.TYPE);
		registerForIntent(GalacticPacketIntent.TYPE);
		for (Terrain t : Terrain.values()) {
			quadTree.put(t.getFile(), new QuadTree<SWGObject>(-5000, -5000, 5000, 5000));
		}
		objects.loadToCache();
		System.out.println("ObjectManager: Loading " + objects.size() + " objects from ObjectDatabase...");
		objects.traverse(new Traverser<SWGObject>() {
			@Override
			public void process(SWGObject obj) {
				Location l = obj.getLocation();
				if (l.getTerrain() != null) {
					QuadTree <SWGObject> tree = quadTree.get(l.getTerrain().getFile());
					if (tree != null) {
						System.out.println(l.getX() + ", " + l.getZ());
						tree.put(l.getX(), l.getZ(), obj);
					} else {
						System.err.println("ObjectManager: Unable to load QuadTree for object " + obj.getObjectId() + " and terrain: " + l.getTerrain());
					}
				}
			}
		});
		
		clientFac = new ClientFactory();
		// Load up the slot definitions to the datamap in clientFac so we don't have to load it later
		SlotDefinitionData def = (SlotDefinitionData) clientFac.getInfoFromFile("abstract/slot/slot_definition/slot_definitions.iff"); 

		return super.initialize();
	}
	
	@Override
	public boolean terminate() {
		objects.save();
		return super.terminate();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		if (i instanceof SWGObjectMovedIntent) {
			Location oTerrain = ((SWGObjectMovedIntent) i).getOldLocation();
			Location nTerrain = ((SWGObjectMovedIntent) i).getNewLocation();
			SWGObject obj = ((SWGObjectMovedIntent) i).getObject();
			if (oTerrain.getTerrain() == null || nTerrain.getTerrain() == null)
				return;
			double x = oTerrain.getX();
			double y = oTerrain.getZ();
			quadTree.get(oTerrain.getTerrain().getFile()).remove(x, y, obj);
			x = nTerrain.getX();
			y = nTerrain.getZ();
			QuadTree<SWGObject> tree = quadTree.get(nTerrain.getTerrain().getFile());
			tree.put(x, y, obj);
			List <Player> updatedAware = new ArrayList<Player>();
			for (SWGObject inRange : tree.getWithinRange(x, y, AWARE_RANGE)) {
				if (inRange.getOwner() != null)
					updatedAware.add(inRange.getOwner());
			}
			obj.updateAwareness(updatedAware);
		} else if (i instanceof GalacticPacketIntent) {
			GalacticPacketIntent gpi = (GalacticPacketIntent) i;
			if (gpi.getPacket() instanceof SelectCharacter) {
				zoneInCharacter(gpi.getPlayerManager(), gpi.getNetworkId(), ((SelectCharacter)gpi.getPacket()).getCharacterId());
			}
		}
	}
	
	public SWGObject createObject(String template) {
		return createObject(template, new Location());
	}
	
	public SWGObject createObject(String template, Location l) {
		synchronized (objects) {
			long objectId = getNextObjectId();
			SWGObject obj = createObjectFromTemplate(objectId, template);
			addObjectAttributes(obj, template);
			obj.setTemplate(template);
			obj.setLocation(l);
			objects.put(objectId, obj);
			return obj;
		}
	}
	
	private void addObjectAttributes(SWGObject obj, String template) {
		System.out.println("Adding attributes for template " + template);
		
		ObjectData attributes = (ObjectData) clientFac.getInfoFromFile(ClientFactory.formatToSharedFile(template));
		
		obj.setStf((String) attributes.getAttribute(ObjectData.OBJ_STF));
		obj.setDetailStf((String) attributes.getAttribute(ObjectData.DETAIL_STF));
		
		addSlotsToObject(obj, attributes);
	}
	
	private void addSlotsToObject(SWGObject obj, ObjectData attributes) {

		if ((String) attributes.getAttribute(ObjectData.SLOT_DESCRIPTOR) != null) {
			SlotDefinitionData slotDefs = (SlotDefinitionData) clientFac.getInfoFromFile("abstract/slot/slot_definition/slot_definitions.iff");
			
			// TODO: These are the slots that the object HAS
			System.out.println("SlotDescriptor: " + (String) attributes.getAttribute(ObjectData.SLOT_DESCRIPTOR));
			SlotDescriptorData descriptor = (SlotDescriptorData) clientFac.getInfoFromFile((String) attributes.getAttribute(ObjectData.SLOT_DESCRIPTOR));
			
			for (String slotName : descriptor.getSlots()) {
				System.out.println("Slot: " + slotName);
				obj.addObjectSlot(new ObjectSlot(slotName));
			}
			
			
		}
		
		if ((String) attributes.getAttribute(ObjectData.ARRANGEMENT_FILE) != null) {
			// This is what slots the object *USES*
			System.out.println("Arrangement: " + (String) attributes.getAttribute(ObjectData.ARRANGEMENT_FILE));
			
			SlotArrangementData arrangementData = (SlotArrangementData) clientFac.getInfoFromFile((String) attributes.getAttribute(ObjectData.ARRANGEMENT_FILE));
			
			obj.setArrangment(arrangementData.getArrangement());
		}
	}
	
	private void zoneInCharacter(PlayerManager playerManager, long netId, long characterId) {
		Player player = playerManager.getPlayerFromNetworkId(netId);
		if (player != null) {
			verifyPlayerObjectsSet(player, characterId);
			long objId = player.getCreatureObject().getObjectId();
			Race race = ((CreatureObject) player.getCreatureObject()).getRace();
			Location l = player.getCreatureObject().getLocation();
			long time = (long)(ProjectSWG.getCoreTime()/1E3);
			sendPacket(player, new HeartBeatMessage());
			sendPacket(player, new ChatServerStatus(true));
			sendPacket(player, new VoiceChatStatus());
			sendPacket(player, new ParametersMessage());
			sendPacket(player, new ChatOnConnectAvatar());
			sendPacket(player, new CmdStartScene(false, objId, race, l, time));
//			player.getCreatureObject().createObject(player);
			CreatureObject creature = (CreatureObject) player.getCreatureObject();
			player.sendPacket(new UpdatePvpStatusMessage(creature.getPvpType(), creature.getPvpFactionId(), creature.getObjectId()));
			creature.createObject(player);
		}
	}
	
	private void verifyPlayerObjectsSet(Player player, long characterId) {
		if (player.getCreatureObject() != null && player.getPlayerObject() != null)
			return;
		SWGObject creature = objects.get(characterId);
		if (creature == null) {
			System.err.println("ObjectManager: Failed to start zone - CreatureObject could not be fetched from database");
			throw new NullPointerException("CreatureObject for ID: " + characterId + " cannot be null!");
		}
		player.setCreatureObject(creature);
		for (SWGObject obj : creature.getChildren()) {
			if (obj instanceof PlayerObject) {
				player.setPlayerObject(obj);
				break;
			}
		}
	}
	
	private long getNextObjectId() {
		synchronized (objects) {
			return maxObjectId++;
		}
	}
	
	private String getFirstTemplatePart(String template) {
		int ind = template.indexOf('/');
		if (ind == -1)
			return "";
		return template.substring(0, ind);
	}
	
	private SWGObject createObjectFromTemplate(long objectId, String template) {
		if (!template.startsWith("object/"))
			return null;
		if (!template.endsWith(".iff"))
			return null;
		template = template.substring(7, template.length()-7-4);
		switch (getFirstTemplatePart(template)) {
			case "creature": return createCreatureObject(objectId, template);
			case "player": return createPlayerObject(objectId, template);
			case "tangible": return createTangibleObject(objectId, template);
			case "intangible": return createIntangibleObject(objectId, template);
			case "waypoint": return createWaypointObject(objectId, template);
			case "weapon": return createWeaponObject(objectId, template);
			case "building": break;
			case "cell": break;
		}
		return null;
	}
	
	private CreatureObject createCreatureObject(long objectId, String template) {
		return new CreatureObject(objectId);
	}
	
	private PlayerObject createPlayerObject(long objectId, String template) {
		return new PlayerObject(objectId);
	}
	
	private TangibleObject createTangibleObject(long objectId, String template) {
		return new TangibleObject(objectId);
	}
	
	private IntangibleObject createIntangibleObject(long objectId, String template) {
		return new IntangibleObject(objectId);
	}
	
	private WaypointObject createWaypointObject(long objectId, String template) {
		return new WaypointObject(objectId);
	}
	
	private WeaponObject createWeaponObject(long objectId, String template) {
		return new WeaponObject(objectId);
	}
}
