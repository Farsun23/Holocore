package resources.server_info;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import resources.config.ConfigFile;
import resources.services.Config;

public class DataManager {
	
	private static final Object instanceLock = new Object();
	private static DataManager instance = null;
	
	private Map <ConfigFile, Config> config;
	private RelationalDatabase localDatabase;
	private boolean initialized;
	
	private DataManager() {
		initialized = false;
	}
	
	private synchronized void initialize() {
		initializeConfig();
		initializeDatabases();
		initialized = localDatabase.isOnline() && localDatabase.isTable("users");
	}
	
	private synchronized void initializeConfig() {
		config = new ConcurrentHashMap<ConfigFile, Config>();
		for (ConfigFile file : ConfigFile.values()) {
			File f = new File(file.getFilename());
			try {
				if (!f.exists() && !f.createNewFile() && !f.isFile()) {
					System.err.println("Service: Warning - ConfigFile could not be loaded! " + file.getFilename());
				} else {
					config.put(file, new Config(f));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private synchronized void initializeDatabases() {
		Config c = getConfig(ConfigFile.PRIMARY);
		initializeLocalDatabase(c);
	}
	
	private synchronized void initializeLocalDatabase(Config c) {
		String db = c.getString("LOCAL-DB", "nge");
		String user = c.getString("LOCAL-USER", "nge");
		String pass = c.getString("LOCAL-PASS", "nge");
		localDatabase = new PostgresqlDatabase("localhost", db, user, pass);
	}
	
	/**
	 * Gets the config object associated with a certain file, or NULL if the
	 * file failed to load on startup
	 * @param file the file to get the config for
	 * @return the config object associated with the file, or NULL if the
	 * config failed to load
	 */
	public synchronized final Config getConfig(ConfigFile file) {
		Config c = config.get(file);
		if (c == null)
			return new Config();
		return c;
	}
	
	/**
	 * Gets the relational database associated with the local postgres database
	 * @return the database for the local postgres database
	 */
	public synchronized final RelationalDatabase getLocalDatabase() {
		return localDatabase;
	}
	
	public synchronized final boolean isInitialized() {
		return initialized;
	}
	
	public synchronized static final DataManager getInstance() {
		synchronized (instanceLock) {
			if (instance == null) {
				instance = new DataManager();
				instance.initialize();
			}
			return instance;
		}
	}
	
}
