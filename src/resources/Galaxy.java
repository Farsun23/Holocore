package resources;


public class Galaxy {
	
	private int id = 0;
	private String name = "";
	private String address = "";
	private int zonePort = 44463;
	private int pingPort = 44462;
	private int population = 0;
	private int popStatus = 0;
	private GalaxyStatus status = GalaxyStatus.DOWN;
	private int timeZone = 0;
	private int maxCharacters = 0;
	private int onlinePlayerLimit = 0;
	private int onlineFreeTrialLimit = 0;
	private boolean recommended = true;
	
	public Galaxy() {
		
	}
	
	public int    getId()                   { return id; }
	public String getName()                 { return name; }
	public String getAddress()              { return address; }
	public int    getZonePort()             { return zonePort; }
	public int    getPingPort()             { return pingPort; }
	public int    getPopulation()           { return population; }
	public int    getPopulationStatus()     { return popStatus; }
	public GalaxyStatus getStatus()         { return status; }
	public int    getTimeZone()             { return timeZone; }
	public int    getMaxCharacters()        { return maxCharacters; }
	public int    getOnlinePlayerLimit()    { return onlinePlayerLimit; }
	public int    getOnlineFreeTrialLimit() { return onlineFreeTrialLimit; }
	public boolean isRecommended()          { return recommended; }
	
	public void setId(int id)                    { this.id = id; }
	public void setName(String name)             { this.name = name; }
	public void setAddress(String addr)          { this.address = addr; }
	public void setZonePort(int port)            { this.zonePort = port; }
	public void setPingPort(int port)            { this.pingPort = port; }
	public void setPopulation(int population)    { this.population = population; }
	public void setPopulationStatus(int status)  { this.popStatus = status; }
	public void setStatus(GalaxyStatus status)   { this.status = status; }
	public void setTimeZone(int timeZone)        { this.timeZone = timeZone; }
	public void setMaxCharacters(int max)        { this.maxCharacters = max; }
	public void setOnlinePlayerLimit(int max)    { this.onlinePlayerLimit = max; }
	public void setOnlineFreeTrialLimit(int max) { this.onlineFreeTrialLimit = max; }
	public void setRecommended(boolean r)        { this.recommended = r; }
	
	public void setStatus(int status) {
		for (GalaxyStatus gs : GalaxyStatus.values()) {
			if (gs.getStatus() == status) {
				setStatus(gs);
				return;
			}
		}
	}
	
	public enum GalaxyStatus {
		DOWN		(0x00),
		LOADING		(0x01),
		UP			(0x02),
		LOCKED		(0x03),
		RESTRICTED	(0x04),
		FULL		(0x05);
		
		private byte status;
		
		GalaxyStatus(int status) {
			this.status = (byte) status;
		}
		
		public byte getStatus() {
			return status;
		}
	}
	
}
