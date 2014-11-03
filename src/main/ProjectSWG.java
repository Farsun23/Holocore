package main;

import services.CoreManager;

public class ProjectSWG {
	
	private final Thread mainThread;
	private CoreManager manager;
	private boolean shutdownRequested;
	
	public static void main(String [] args) {
		final ProjectSWG server = new ProjectSWG();
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
	}
	
	public ProjectSWG() {
		mainThread = Thread.currentThread();
		shutdownRequested = false;
	}
	
	private void run() {
		while (!shutdownRequested) {
			manager = new CoreManager();
			initialize();
			System.out.println("ProjectSWG: Initialized. Time: " + manager.getCoreTime() + "ms");
			start();
			System.out.println("ProjectSWG: Started. Time: " + manager.getCoreTime() + "ms");
			loop();
			terminate();
			System.out.println("ProjectSWG: Terminated. Time: " + manager.getCoreTime() + "ms");
			if (!shutdownRequested) {
				System.out.println("ProjectSWG: Cleaning up memory...");
				cleanup();
			}
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
	}
	
	private void start() {
		System.out.println("ProjectSWG: Starting...");
		if (!manager.start())
			throw new CoreException("Failed to start.");
	}
	
	private void loop() {
		while (manager.isOperational() && !shutdownRequested) {
			try {
				Thread.sleep(100); // Checks the state of the server every 100ms
			} catch (InterruptedException e) {
				if (!shutdownRequested)
					throw new CoreException("Main Thread Interrupted.");
			}
		}
	}
	
	private void terminate() {
		if (manager == null)
			return;
		if (!manager.terminate())
			throw new CoreException("Failed to terminate.");
	}
	
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
