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
package main;

import java.lang.Thread.State;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import intents.server.ServerStatusIntent;
import resources.Galaxy.GalaxyStatus;
import resources.control.IntentManager;
import resources.control.ServerStatus;
import resources.server_info.Log;
import services.CoreManager;

public class ProjectSWG {
	
	private static ProjectSWG server;
	private final Thread mainThread;
	private CoreManager manager;
	private boolean shutdownRequested;
	private ServerStatus status;
	private ServerInitStatus initStatus;
	private int adminServerPort;
	
	public static final void main(String [] args) {
		server = new ProjectSWG();
		AtomicBoolean forcingShutdown = new AtomicBoolean(false);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			forcingShutdown.set(true);
			server.forceShutdown();
		}, "main-shutdown-hook"));
		try {
			startupIntentManager();
			server.run(args);
		} catch (Throwable t) {
			Log.e(t);
		}
		try {
			server.stop();
			server.terminate();
		} finally {
			shutdownIntentManager();
			printFinalPswgState();
			Log.i("Server shut down.");
			if (!forcingShutdown.get())
				System.exit(0);
		}
	}
	
	/**
	 * Returns the time in milliseconds since the server started initialization
	 * @return the core time represented as a double
	 */
	public static final long getCoreTime() {
		return (long) server.manager.getCoreTime();
	}
	
	/**
	 * Returns the server's galactic time. This is the official time sent to
	 * the client and should be used for any official client-time purposes.
	 * @return the server's galactic time in seconds
	 */
	public static final long getGalacticTime() {
		return (long) (System.currentTimeMillis()/1E3 - 1309996800L); // Date is 07/07/2011 GMT
	}
	
	private static void startupIntentManager() {
		IntentManager.getInstance().initialize();
	}
	
	private static void shutdownIntentManager() {
		IntentManager.getInstance().terminate();
	}
	
	private static void printFinalPswgState() {
		List<Thread> threads = Thread.getAllStackTraces().keySet().stream()
				.filter(t -> !t.isDaemon() && t.getState() != State.TERMINATED)
				.sorted((a, b) -> a.getName().compareTo(b.getName()))
				.collect(Collectors.toList());
		Log.i("Final PSWG State:");
		Log.i("    Threads: %d", threads.size());
		for (Thread thread : threads) {
			Log.i("        Thread: %s", thread.getName());
		}
	}
	
	private ProjectSWG() {
		mainThread = Thread.currentThread();
		shutdownRequested = false;
		initStatus = ServerInitStatus.INITIALIZED;
	}
	
	private void run(String [] args) {
		setupParameters(args);
		long start = System.nanoTime();
		manager = new CoreManager(adminServerPort);
		long end = System.nanoTime();
		Log.i("Created new manager in %.3fms", (end-start)/1E6);
		while (!shutdownRequested && !manager.isShutdownRequested()) {
			initialize();
			start();
			loop();
			stop();
			terminate();
			if (!shutdownRequested && !manager.isShutdownRequested()) {
				start = System.nanoTime();
				manager = new CoreManager(adminServerPort);
				end = System.nanoTime();
				Log.i("Created new manager in %.3fms", (end-start)/1E6);
			}
		}
	}
	
	private void setupParameters(String [] args) {
		Map<String, String> params = getParameters(args);
		this.adminServerPort = safeParseInt(params.get("-adminServerPort"), -1);
	}
	
	private int safeParseInt(String str, int def) {
		if (str == null)
			return def;
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			return def;
		}
	}
	
	private Map<String, String> getParameters(String [] args) {
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
	
	private void setStatus(ServerStatus status) {
		this.status = status;
		new ServerStatusIntent(status).broadcast();
	}
	
	private void forceShutdown() {
		shutdownRequested = true;
		mainThread.interrupt();
		try { mainThread.join(); } catch (InterruptedException e) { }
	}
	
	private void initialize() {
		setStatus(ServerStatus.INITIALIZING);
		Log.i("Initializing...");
		if (!manager.initialize())
			throw new CoreException("Failed to initialize.");
		Log.i("Initialized. Time: %.3fms", manager.getCoreTime());
		initStatus = ServerInitStatus.INITIALIZED;
	}
	
	private void start() {
		Log.i("Starting...");
		if (!manager.start())
			throw new CoreException("Failed to start.");
		Log.i("Started. Time: %.3fms", manager.getCoreTime());
		initStatus = ServerInitStatus.STARTED;
	}
	
	private void loop() {
		setStatus((manager.getGalaxyStatus() == GalaxyStatus.UP) ? ServerStatus.OPEN : ServerStatus.LOCKED);
		while (!shutdownRequested && !manager.isShutdownRequested() && manager.isOperational()) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				throw new CoreException("Main Thread Interrupted.");
			}
		}
	}
	
	private void stop() {
		if (manager == null || status == ServerStatus.OFFLINE || initStatus != ServerInitStatus.STARTED)
			return;
		Log.i("Stopping...");
		setStatus(ServerStatus.STOPPING);
		initStatus = ServerInitStatus.STOPPED;
		if (!manager.stop()) {
			Log.e("Failed to stop.");
			return;
		}
		Log.i("Stopped. Time: %.3fms", manager.getCoreTime());
	}
	
	private void terminate() {
		if (manager == null || status == ServerStatus.OFFLINE)
			return;
		if (initStatus != ServerInitStatus.NONE && initStatus != ServerInitStatus.INITIALIZED && initStatus != ServerInitStatus.STOPPED)
			return;
		Log.i("Terminating...");
		setStatus(ServerStatus.TERMINATING);
		if (!manager.terminate())
			throw new CoreException("Failed to terminate.");
		setStatus(ServerStatus.OFFLINE);
		Log.i("Terminated. Time: %.3fms", manager.getCoreTime());
	}
	
	private enum ServerInitStatus {
		NONE,
		INITIALIZED,
		STARTED,
		STOPPED,
		TERMINATED
	}
	
	public static class CoreException extends RuntimeException {
		
		private static final long serialVersionUID = 455306876887818064L;
		
		public CoreException(String reason) {
			super(reason);
		}
		
	}
	
}