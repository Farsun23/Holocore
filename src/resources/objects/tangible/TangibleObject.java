package resources.objects.tangible;

import network.packets.swg.zone.SceneEndBaselines;
import network.packets.swg.zone.baselines.Baseline.BaselineType;
import resources.network.BaselineBuilder;
import resources.objects.SWGObject;
import resources.player.Player;

public class TangibleObject extends SWGObject {
	
	private static final long serialVersionUID = 1L;
	
	private byte []	appearanceData	= new byte[0];
	private int		damageTaken		= 0;
	private int		maxHitPoints	= 0;
	private int		components		= 0;
	private boolean	inCombat		= false;
	private int		condition		= 0;
	private int		pvpFlags		= 0;
	private int		pvpType			= 0;
	private int		pvpFactionId	= 0;
	private boolean	visibleGmOnly	= false;
	private byte []	objectEffects	= new byte[0];
	
	public TangibleObject(long objectId) {
		super(objectId);
	}
	
	public byte [] getAppearanceData() {
		return appearanceData;
	}
	
	public int getDamageTaken() {
		return damageTaken;
	}
	
	public int getMaxHitPoints() {
		return maxHitPoints;
	}
	
	public int getComponents() {
		return components;
	}
	
	public boolean isInCombat() {
		return inCombat;
	}
	
	public int getCondition() {
		return condition;
	}
	
	public int getPvpFlags() {
		return pvpFlags;
	}
	
	public int getPvpType() {
		return pvpType;
	}
	
	public int getPvpFactionId() {
		return pvpFactionId;
	}
	
	public boolean isVisibleGmOnly() {
		return visibleGmOnly;
	}
	
	public byte [] getObjectEffects() {
		return objectEffects;
	}
	
	public void setAppearanceData(byte [] appearanceData) {
		this.appearanceData = appearanceData;
	}
	
	public void setDamageTaken(int damageTaken) {
		this.damageTaken = damageTaken;
	}
	
	public void setMaxHitPoints(int maxHitPoints) {
		this.maxHitPoints = maxHitPoints;
	}
	
	public void setComponents(int components) {
		this.components = components;
	}
	
	public void setInCombat(boolean inCombat) {
		this.inCombat = inCombat;
	}
	
	public void setCondition(int condition) {
		this.condition = condition;
	}
	
	public void setPvpFlags(int pvpFlags) {
		this.pvpFlags = pvpFlags;
	}
	
	public void setPvpType(int pvpType) {
		this.pvpType = pvpType;
	}
	
	public void setPvpFactionId(int pvpFactionId) {
		this.pvpFactionId = pvpFactionId;
	}
	
	public void setVisibleGmOnly(boolean visibleGmOnly) {
		this.visibleGmOnly = visibleGmOnly;
	}
	
	public void setObjectEffects(byte [] objectEffects) {
		this.objectEffects = objectEffects;
	}
	
	protected void createObject(Player target) {
		super.sendSceneCreateObject(target);
		
		BaselineBuilder bb = new BaselineBuilder(this, BaselineType.TANO, 3);
		createBaseline3(target, bb);
		bb.sendTo(target);
		
		bb = new BaselineBuilder(this, BaselineType.TANO, 6);
		createBaseline6(target, bb);
		bb.sendTo(target);
		
		if (getOwner() == target) {
			bb = new BaselineBuilder(this, BaselineType.TANO, 8);
			createBaseline8(target, bb);
			bb.sendTo(target);
			
			bb = new BaselineBuilder(this, BaselineType.TANO, 9);
			createBaseline9(target, bb);
			bb.sendTo(target);
		}
		
		createChildrenObjects(target);
		target.sendPacket(new SceneEndBaselines(getObjectId()));
	}
	
	protected void createChildrenObjects(Player target) {
		super.createChildrenObjects(target);
	}
	
	public void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb); // 4 variables - BASE3 (4)
		bb.addInt(0); // Faction
		bb.addInt(0); // Faction Status
		bb.addArray(appearanceData);
		bb.addInt(0); // Component customization (Set, Integer)
			bb.addInt(0); //updates
		bb.addInt(128); // Options Bitmask (128 = Attackable)
		bb.addInt(0); // Incap Timer for players, Use count for objects
		bb.addInt(condition);
		bb.addInt(100); // Random number that should be high enough - maxCondition
		bb.addBoolean(false); // isStatic
		
		bb.incremeantOperandCount(9);
	}
	
	public void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb);
		bb.addBoolean(false); // Combat flag
		bb.addInt(0); // Defenders List (Set, Long)
			bb.addInt(0);
		bb.addInt(0); // Unknown List (List, Long)
			bb.addInt(0);
		bb.addInt(0); // Unknown List (List, Integer)
			bb.addInt(0);
		bb.addInt(0); // Unknown, possibly a list/map or something
			bb.addInt(0);
		
		bb.addInt(0);
		
		bb.incremeantOperandCount(6);
	}
	
	public void createBaseline8(Player target, BaselineBuilder bb) {
		super.createBaseline8(target, bb);
		bb.addShort(0);
		bb.addShort(0);
		
		bb.incremeantOperandCount(2);
	}
	
	public void createBaseline9(Player target, BaselineBuilder bb) {
		super.createBaseline9(target, bb);
		bb.addShort(0);
		bb.addShort(0);
		
		bb.incremeantOperandCount(2);
	}
}
