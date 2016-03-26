package resources.objects.manufacture;

import network.packets.swg.zone.baselines.Baseline.BaselineType;
import resources.collections.SWGMap;
import resources.common.CRC;
import resources.encodables.StringId;
import resources.network.BaselineBuilder;
import resources.network.NetBuffer;
import resources.objects.intangible.IntangibleObject;
import resources.player.Player;
import resources.server_info.CrcDatabase;

public class ManufactureSchematicObject extends IntangibleObject {
	
	private static final long serialVersionUID = 1L;
	
	private SWGMap<StringId, Float> attributes	= new SWGMap<>(3, 5);
	private int itemsPerContainer				= 0;
	private float manufactureTime				= 0;
	private byte [] appearanceData				= new byte[0];
	private byte [] customAppearance			= new byte[0];
	private String draftSchematicTemplate		= "";
	private boolean crafting					= false;
	private byte schematicChangedSignal			= 0;
	
	public ManufactureSchematicObject(long objectId) {
		super(objectId, BaselineType.MSCO);
	}
	
	public float getManufactureAttribute(String name) {
		Float attr;
		synchronized (attributes) {
			attr = attributes.get(name);
		}
		if (attr == null)
			return Float.NaN;
		return attr;
	}
	
	public void setItemsPerContainer(int items) {
		this.itemsPerContainer = items;
	}
	
	public int getItemsPerContainer() {
		return itemsPerContainer;
	}
	
	public void setManufactureTime(float time) {
		this.manufactureTime = time;
	}
	
	public float getManufactureTime() {
		return manufactureTime;
	}
	
	public byte [] getAppearanceData() {
		return appearanceData;
	}
	
	public void setAppearanceData(byte[] appearanceData) {
		this.appearanceData = appearanceData;
	}
	
	public byte [] getCustomAppearance() {
		return customAppearance;
	}
	
	public void setCustomAppearance(byte[] customAppearance) {
		this.customAppearance = customAppearance;
	}
	
	public String getDraftSchematicTemplate() {
		return draftSchematicTemplate;
	}
	
	public void setDraftSchematicTemplate(String draftSchematicTemplate) {
		this.draftSchematicTemplate = draftSchematicTemplate;
	}
	
	public boolean isCrafting() {
		return crafting;
	}
	
	public void setCrafting(boolean crafting) {
		this.crafting = crafting;
	}
	
	public byte getSchematicChangedSignal() {
		return schematicChangedSignal;
	}
	
	public void setSchematicChangedSignal(byte schematicChangedSignal) {
		this.schematicChangedSignal = schematicChangedSignal;
	}
	
	@Override
	public void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb);
		bb.addObject(attributes);
		bb.addInt(itemsPerContainer);
		bb.addFloat(manufactureTime);
	}
	
	@Override
	public void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb);
		bb.addArray(appearanceData);
		bb.addArray(customAppearance);
		bb.addInt(CRC.getCrc(draftSchematicTemplate));
		bb.addBoolean(crafting);
		bb.addByte(schematicChangedSignal);
	}
	
	@Override
	public void parseBaseline3(NetBuffer buffer) {
		super.parseBaseline3(buffer);
		attributes = buffer.getSwgMap(3, 5, StringId.class, Float.class);
		itemsPerContainer = buffer.getInt();
		manufactureTime = buffer.getFloat();
	}
	
	@Override
	public void parseBaseline6(NetBuffer buffer) {
		super.parseBaseline6(buffer);
		appearanceData = buffer.getArray();
		customAppearance = buffer.getArray();
		try (CrcDatabase db = new CrcDatabase()) {
			draftSchematicTemplate = db.getString(buffer.getInt());
		}
		crafting = buffer.getBoolean();
		schematicChangedSignal = buffer.getByte();
	}
	
}
