/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/
package com.projectswg.holocore;

import com.projectswg.common.data.encodables.chat.ChatAvatar;
import com.projectswg.common.data.encodables.galaxy.Galaxy;
import com.projectswg.common.data.encodables.galaxy.Galaxy.GalaxyStatus;
import com.projectswg.common.data.info.Config;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.holocore.intents.server.ServerStatusIntent;
import com.projectswg.holocore.resources.config.ConfigFile;
import com.projectswg.holocore.resources.control.ServerStatus;
import com.projectswg.holocore.resources.server_info.DataManager;
import com.projectswg.holocore.services.CoreManager;
import com.projectswg.holocore.utilities.ScheduledUtilities;
import me.joshlarson.jlcommon.concurrency.Delay;
import me.joshlarson.jlcommon.control.IntentManager;
import me.joshlarson.jlcommon.control.IntentManager.IntentSpeedRecord;
import me.joshlarson.jlcommon.log.Log;
import me.joshlarson.jlcommon.log.Log.LogLevel;
import me.joshlarson.jlcommon.log.log_wrapper.ConsoleLogWrapper;
import me.joshlarson.jlcommon.log.log_wrapper.FileLogWrapper;

import java.io.File;
import java.lang.Thread.State;
import java.time.OffsetTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ProjectSWG {
	
	public static final String VERSION = "APR18";
	
	private static final Galaxy GALAXY = new Galaxy();
	private static final AtomicBoolean SHUTDOWN_HOOK = new AtomicBoolean(false);
	private static final AtomicReference<ProjectSWG> INSTANCE = new AtomicReference<>(null);
	
	private CoreManager manager;
	private boolean shutdownRequested;
	private ServerStatus status;
	private ServerInitStatus initStatus;
	private int adminServerPort;
	
	public static void main(String [] args) throws InterruptedException {
		ThreadGroup group = new ThreadGroup("holocore");
		Thread mainThread = new Thread(group, () -> mainThread(args), "main");
		Runtime.getRuntime().addShutdownHook(createShutdownHook(mainThread));
		
		mainThread.start();
		mainThread.join();
		
		if (!SHUTDOWN_HOOK.get())
			System.exit(0);
	}
	
	/**
	 * Returns the server's galactic time. This is the official time sent to
	 * the client and should be used for any official client-time purposes.
	 * @return the server's galactic time in seconds
	 */
	public static long getGalacticTime() {
		return (long) (System.currentTimeMillis()/1E3 - 1309996800L); // Date is 07/07/2011 GMT
	}
	
	public static Galaxy getGalaxy() {
		return GALAXY;
	}
	
	private static void mainThread(String [] args) {
		File logDirectory = new File("log");
		if (!logDirectory.isDirectory() && !logDirectory.mkdir())
			Log.w("Failed to make log directory!");
		Log.addWrapper(new ConsoleLogWrapper(LogLevel.DATA));
		Log.addWrapper(new FileLogWrapper(new File("log/log.txt")));
		
		Log.i("Holocore version: %s", VERSION);
		
		ProjectSWG server = new ProjectSWG();
		try {
			startupStaticClasses();
			server.run(args);
		} catch (Throwable t) {
			Log.e(t);
		}
		try {
			server.stop();
			server.terminate();
		} finally {
			shutdownStaticClasses();
			printFinalPswgState();
			Log.i("Server shut down.");
		}
	}
	
	private static Thread createShutdownHook(Thread mainThread) {
		Thread currentThread = Thread.currentThread();
		Thread thread = new Thread(() -> {
			SHUTDOWN_HOOK.set(true);
			currentThread.interrupt();
			mainThread.interrupt();
			try {
				mainThread.join();
			} catch (InterruptedException e) {
				Log.e(e);
			}
		}, "holocore-shutdown-hook");
		thread.setDaemon(true);
		return thread;
	}
	
	private static void startupStaticClasses() {
		IntentManager.setInstance(new IntentManager(Runtime.getRuntime().availableProcessors()));
		IntentManager.getInstance().initialize();
		DataManager.initialize();
		Thread.currentThread().setPriority(10);
		setupGalaxy();
	}
	
	private static void shutdownStaticClasses() {
		DataManager.terminate();
		IntentManager.getInstance().terminate();
		ScheduledUtilities.shutdown();
	}
	
	private static void printFinalPswgState() {
		List<Thread> threads = Thread.getAllStackTraces().keySet().stream()
				.filter(t -> !t.isDaemon() && t.getState() != State.TERMINATED)
				.sorted(Comparator.comparing(Thread::getName))
				.collect(Collectors.toList());
		Log.i("Final PSWG State:");
		Log.i("    Threads: %d", threads.size());
		for (Thread thread : threads) {
			Log.i("        Thread: %s", thread.getName());
		}
		List<IntentSpeedRecord> intentTimes = IntentManager.getInstance().getSpeedRecorder().getAllTimes();
		Collections.sort(intentTimes);
		Log.i("    Intent Times: [%d]", intentTimes.size());
		Log.i("        %-30s%-40s%-10s%-20s", "Intent", "Receiver", "Count", "Time");
		for (IntentSpeedRecord record : intentTimes) {
			String receiverName = record.getConsumer().getClass().getName();
			if (receiverName.indexOf('$') != -1)
				receiverName = receiverName.substring(0, receiverName.indexOf('$'));
			receiverName = receiverName.replace("com.projectswg.holocore.services.", "");
			String intentName = record.getIntent().getSimpleName();
			String recordCount = Long.toString(record.getCount());
			String recordTime = String.format("%.6fms", record.getTime() / 1E6);
			Log.i("        %-30s%-40s%-10s%-20s", intentName, receiverName, recordCount, recordTime);
		}
	}
	
	private ProjectSWG() {
		this.manager = null;
		this.shutdownRequested = false;
		this.status = ServerStatus.OFFLINE;
		this.initStatus = ServerInitStatus.INITIALIZED;
		this.adminServerPort = 0;
	}
	
	private void run(String [] args) {
		setupParameters(args);
		create();
		initialize();
		start();
		loop();
		stop();
		terminate();
	}
	
	private void setupParameters(String [] args) {
		Map<String, String> params = getParameters(args);
		GALAXY.setAdminServerPort(safeParseInt(params.get("-adminServerPort"), -1));
	}
	
	private void setStatus(ServerStatus status) {
		this.status = status;
		new ServerStatusIntent(status).broadcast();
	}
	
	private void create() {
		long start = System.nanoTime();
		manager = new CoreManager(adminServerPort);
		manager.setIntentManager(IntentManager.getInstance());
		long end = System.nanoTime();
		Log.i("Created new manager in %.3fms", (end-start)/1E6);
	}
	
	private void initialize() {
		setStatus(ServerStatus.INITIALIZING);
		Log.i("Initializing...");
		long start = System.nanoTime();
		if (!manager.initialize())
			throw new CoreException("Failed to initialize.");
		long time = System.nanoTime() - start;
		Log.i("Initialized. Time: %.3fms", time / 1E6);
		initStatus = ServerInitStatus.INITIALIZED;
		cleanupMemory();
	}
	
	private void start() {
		Log.i("Starting...");
		long start = System.nanoTime();
		if (!manager.start())
			throw new CoreException("Failed to start.");
		long time = System.nanoTime() - start;
		Log.i("Started. Time: %.3fms", time / 1E6);
		initStatus = ServerInitStatus.STARTED;
	}
	
	private void loop() {
		setStatus(ServerStatus.OPEN);
		
		boolean initialIntentsCompleted = false;
		long loop = 0;
		while (!shutdownRequested && manager.isOperational()) {
			if (!initialIntentsCompleted && IntentManager.getInstance().getIntentCount() == 0) {
				Log.i("Intent queue empty.");
				initialIntentsCompleted = true;
				cleanupMemory();
//				throw new CoreException("Intent queue empty");
			}
			
			if (loop % 12000 == 0 && initialIntentsCompleted) {// Approx every 10 mins, do a memory clean-up
				cleanupMemory();
			}
			if (!Delay.sleepMilli(50))
				break;
			loop++;
		}
	}
	
	private void stop() {
		if (manager == null || status == ServerStatus.OFFLINE || initStatus != ServerInitStatus.STARTED)
			return;
		Log.i("Stopping...");
		setStatus(ServerStatus.STOPPING);
		initStatus = ServerInitStatus.STOPPED;
		long start = System.nanoTime();
		if (!manager.stop()) {
			Log.e("Failed to stop.");
			return;
		}
		long time = System.nanoTime() - start;
		Log.i("Stopped. Time: %.3fms", time / 1E6);
	}
	
	private void terminate() {
		if (manager == null || status == ServerStatus.OFFLINE)
			return;
		if (initStatus != ServerInitStatus.NONE && initStatus != ServerInitStatus.INITIALIZED && initStatus != ServerInitStatus.STOPPED)
			return;
		Log.i("Terminating...");
		setStatus(ServerStatus.TERMINATING);
		long start = System.nanoTime();
		if (!manager.terminate())
			throw new CoreException("Failed to terminate.");
		long time = System.nanoTime() - start;
		setStatus(ServerStatus.OFFLINE);
		Log.i("Terminated. Time: %.3fms", time / 1E6);
	}
	
	private enum ServerInitStatus {
		NONE,
		INITIALIZED,
		STARTED,
		STOPPED,
		TERMINATED
	}
	
	private static void cleanupMemory() {
		Runtime rt = Runtime.getRuntime();
		long total = rt.totalMemory();
		long usedBefore = total - rt.freeMemory();
		ClientFactory.freeMemory();
		System.gc();
		long usedAfter = total - rt.freeMemory();
		Log.d("Memory cleanup. Total: %.1fGB  Before: %.2f%%  After: %.2f%%", total/1073741824.0, usedBefore/1073741824.0*100, usedAfter/1073741824.0*100);
	}
	
	private static Map<String, String> getParameters(String [] args) {
		Map<String, String> params = new HashMap<>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			String nextArg = (i+1 < args.length) ? args[i+1] : null;
			if (arg.indexOf('=') != -1) {
				String [] parts = arg.split("=", 2);
				if (parts.length < 2)
					params.put(parts[0], null);
				else
					params.put(parts[0], parts[1]);
			} else if (arg.equalsIgnoreCase("-adminServerPort") && nextArg != null) {
				params.put(arg, nextArg);
				i++;
			} else {
				params.put(arg, null);
			}
		}
		return params;
	}
	
	private static int safeParseInt(String str, int def) {
		if (str == null)
			return def;
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			return def;
		}
	}
	
	private static void setupGalaxy() {
		Config c = DataManager.getConfig(ConfigFile.PRIMARY);
		GALAXY.setId(1);
		GALAXY.setName(c.getString("GALAXY-NAME", "Holocore"));
		GALAXY.setAddress("");
		GALAXY.setPopulation(0);
		GALAXY.setZoneOffset(OffsetTime.now().getOffset());
		GALAXY.setZonePort(0);
		GALAXY.setPingPort(0);
		GALAXY.setStatus(GalaxyStatus.DOWN);
		GALAXY.setMaxCharacters(c.getInt("GALAXY-MAX-CHARACTERS", 2));
		GALAXY.setOnlinePlayerLimit(c.getInt("GALAXY-MAX-ONLINE", 3000));
		GALAXY.setOnlineFreeTrialLimit(c.getInt("GALAXY-MAX-ONLINE", 3000));
		GALAXY.setRecommended(true);
		
		ChatAvatar.setGalaxy(GALAXY.getName());
	}
	
	public static class CoreException extends RuntimeException {
		
		private static final long serialVersionUID = 455306876887818064L;
		
		public CoreException(String reason) {
			super(reason);
		}
		
		public CoreException(String reason, Throwable cause) {
			super(reason, cause);
		}
		
	}
	
}
