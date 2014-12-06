package services.objects;

import intents.GalacticPacketIntent;
import intents.swgobject_events.SWGObjectEventIntent;
import intents.swgobject_events.SWGObjectMovedIntent;

import java.util.HashMap;
import java.util.Map;

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
import resources.control.Intent;
import resources.control.Manager;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.objects.intangible.IntangibleObject;
import resources.objects.player.PlayerObject;
import resources.objects.quadtree.QuadTree;
import resources.objects.tangible.TangibleObject;
import resources.player.Player;
import services.player.PlayerManager;

public class ObjectManager extends Manager {
	
	private Map <Long, SWGObject> objects;
	private Map <String, QuadTree <SWGObject>> quadTree;
	private long maxObjectId;
	
	public ObjectManager() {
		objects = new HashMap<Long, SWGObject>();
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
		return super.initialize();
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
			quadTree.get(oTerrain.getTerrain().getFile()).remove(oTerrain.getX(), y, obj);
			x = nTerrain.getX();
			y = nTerrain.getZ();
			quadTree.get(nTerrain.getTerrain().getFile()).put(x, y, obj);
		} else if (i instanceof GalacticPacketIntent) {
			GalacticPacketIntent gpi = (GalacticPacketIntent) i;
			if (gpi.getPacket() instanceof SelectCharacter) {
				zoneInCharacter(gpi.getPlayerManager(), gpi.getNetworkId());
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
			obj.setTemplate(template);
			obj.setLocation(l);
			return obj;
		}
	}
	
	private void zoneInCharacter(PlayerManager playerManager, long netId) {
		Player player = playerManager.getPlayerFromNetworkId(netId);
		if (player != null) {
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
			case "weapon": break;
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
		if (!template.startsWith("tangible/"))
			return null;
		template = template.substring(9);
		switch (getFirstTemplatePart(template)) {
			
		}
		return new TangibleObject(objectId);
	}
	
	private IntangibleObject createIntangibleObject(long objectId, String template) {
		return new IntangibleObject(objectId);
	}
	
}
