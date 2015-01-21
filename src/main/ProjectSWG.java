package main;

import intents.ServerStatusIntent;
import intents.ServerStatusIntent.ServerStatus;
import resources.Galaxy.GalaxyStatus;
import services.CoreManager;

public class ProjectSWG {
	
	private static ProjectSWG server;
	private final Thread mainThread;
	private CoreManager manager;
	private boolean shutdownRequested;
	
	public static final void main(String [] args) {
		server = new ProjectSWG();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				server.forceShutdown();
			}
		});
		try {
			server.run();
		} catch (CoreException e) {
			System.err.println("ProjectSWG: Shutting down. Reason: " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("ProjectSWG: Shutting down - unknown error.");
		}
		server.terminate();
		System.out.println("ProjectSWG: Server shut down.");
	}
	
	/**
	 * Returns the time in milliseconds since the server started initialization
	 * @return the core time represented as a double
	 */
	public static final long getCoreTime() {
		return (long) server.manager.getCoreTime();
	}
	
	private ProjectSWG() {
		mainThread = Thread.currentThread();
		shutdownRequested = false;
	}
	
	private void run() {
		while (!shutdownRequested && (manager == null || (manager != null && !manager.isShutdownRequested()))) {
			manager = new CoreManager();
			new ServerStatusIntent(ServerStatus.INITIALIZING).broadcast();
			initialize();
			start();
			GalaxyStatus status = manager.getGalaxyStatus();
			new ServerStatusIntent(status==GalaxyStatus.UP?ServerStatus.OPEN:ServerStatus.LOCKED).broadcast();
			loop();
			System.out.println("ProjectSWG: Shutting down server.");
			new ServerStatusIntent(ServerStatus.TERMINATING).broadcast();
			terminate();
			new ServerStatusIntent(ServerStatus.OFFLINE).broadcast();
			/*if (!shutdownRequested) { // Should this be here? It can only be reached at this point if there is a shutdown due to loop() method
				System.out.println("ProjectSWG: Cleaning up memory...");
				cleanup();
			}*/
		}
	}
	
	private void forceShutdown() {
		shutdownRequested = true;
		mainThread.interrupt();
		try { mainThread.join(); } catch (InterruptedException e) { }
	}
	
	private void initialize() {
		System.out.println("ProjectSWG: Initializing...");
		if (!manager.initialize())
			throw new CoreException("Failed to initialize.");
		System.out.println("ProjectSWG: Initialized. Time: " + manager.getCoreTime() + "ms");
	}
	
	private void start() {
		System.out.println("ProjectSWG: Starting...");
		if (!manager.start())
			throw new CoreException("Failed to start.");
		System.out.println("ProjectSWG: Started. Time: " + manager.getCoreTime() + "ms");
	}
	
	private void loop() {
		long loop = 0;
		while (!shutdownRequested && !manager.isShutdownRequested()) {
			try {
				manager.flushPackets(); // Sends any packets that weren't sent
				Thread.sleep(50); // Checks the state of the server every 10ms
			} catch (InterruptedException e) {
				if (!shutdownRequested)
					throw new CoreException("Main Thread Interrupted.");
			}
			loop++;
			if (loop % 10 == 0 && !manager.isOperational())
				break;
//			if (!manager.isOperational())
//				break;
		}
	}
	
	private void terminate() {
		if (manager == null)
			return;
		if (!manager.terminate())
			throw new CoreException("Failed to terminate.");
		System.out.println("ProjectSWG: Terminated. Time: " + manager.getCoreTime() + "ms");
	}
	
	@SuppressWarnings("unused")
	private void cleanup() {
		manager = null;
		Runtime.getRuntime().gc();
	}
	
	private static class CoreException extends RuntimeException {
		
		private static final long serialVersionUID = 455306876887818064L;
		
		public CoreException(String reason) {
			super(reason);
		}
		
	}
	
}
