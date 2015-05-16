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
package services;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import network.packets.Packet;
import network.packets.soe.DataChannelA;
import network.packets.soe.MultiPacket;
import network.packets.swg.SWGPacket;
import network.packets.swg.zone.baselines.Baseline;
import network.packets.swg.zone.object_controller.ObjectController;
import intents.InboundPacketIntent;
import intents.OutboundPacketIntent;
import intents.ServerManagementIntent;
import intents.ServerStatusIntent;
import resources.Galaxy;
import resources.Galaxy.GalaxyStatus;
import resources.config.ConfigFile;
import resources.control.Intent;
import resources.control.Manager;
import resources.control.ServerStatus;
import resources.services.Config;
import services.galaxy.GalacticManager;

public class CoreManager extends Manager {
	
	private static final int galaxyId = 1;
	
	private EngineManager engineManager;
	private GalacticManager galacticManager;
	private PrintStream packetOutput;
	private Galaxy galaxy;
	private long startTime;
	private boolean shutdownRequested;
	private boolean packetDebug;
	
	public CoreManager() {
		shutdownRequested = false;
		galaxy = getGalaxy();
		if (galaxy != null) {
			engineManager = new EngineManager(galaxy);
			galacticManager = new GalacticManager(galaxy);
			
			addChildService(engineManager);
			addChildService(galacticManager);
		}
	}
	
	/**
	 * Determines whether or not the core is operational
	 * @return TRUE if the core is operational, FALSE otherwise
	 */
	public boolean isOperational() {
		return true;
	}
	
	public boolean isShutdownRequested() {
		return shutdownRequested;
	}
	
	@Override
	public boolean initialize() {
		startTime = System.nanoTime();
		registerForIntent(InboundPacketIntent.TYPE);
		registerForIntent(OutboundPacketIntent.TYPE);
		registerForIntent(ServerManagementIntent.TYPE);
		packetDebug = getConfig(ConfigFile.PRIMARY).getBoolean("PACKET-DEBUG", false);
		initializePacketOutput();
		return galaxy != null && super.initialize();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		if (packetDebug) {
			if (i instanceof InboundPacketIntent) {
				InboundPacketIntent in = (InboundPacketIntent) i;
				packetOutput.println("IN  " + in.getNetworkId() + ":" + in.getServerType());
				outputPacket(1, in.getPacket());
			} else if (i instanceof OutboundPacketIntent) {
				OutboundPacketIntent out = (OutboundPacketIntent) i;
				packetOutput.println("OUT " + out.getNetworkId());
				outputPacket(1, out.getPacket());
			}
		}
		if (i instanceof ServerManagementIntent)
			handleServerManagementIntent((ServerManagementIntent) i);
	}
	
	private void initializePacketOutput() {
		try {
			packetOutput = new PrintStream(new FileOutputStream("packets.txt", false), true, "UTF-8");
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
			packetOutput = System.out;
		}
	}
	
	private void handleServerManagementIntent(ServerManagementIntent i) {
		switch(i.getEvent()) {
			case SHUTDOWN: initiateShutdownSequence(i);  break;
			default: break;
		}
	}

	private void initiateShutdownSequence(ServerManagementIntent i) {
		System.out.println("Beginning server shutdown sequence...");
		long time = i.getTime();
		TimeUnit timeUnit = i.getTimeUnit();
		
		Executors.newSingleThreadScheduledExecutor().schedule(
				new Runnable() {
					@Override
					public void run() {
						shutdownRequested = true;
					}
					// Ziggy: Give the broadcast method extra time to complete.
					// If we don't, the final broadcast won't be displayed.
				},
				TimeUnit.NANOSECONDS.convert(time, timeUnit) + TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS), TimeUnit.NANOSECONDS);

		new ServerStatusIntent(ServerStatus.SHUTDOWN_REQUESTED, time, i.getTimeUnit()).broadcast();
	}
	
	public GalaxyStatus getGalaxyStatus() {
		return galaxy.getStatus();
	}
	
	private void outputPacket(int indent, Packet packet) {
		if (packet instanceof DataChannelA) {
			for (SWGPacket p : ((DataChannelA) packet).getPackets()) {
				for (int i = 0; i < indent; i++)
					packetOutput.print("    ");
				outputSWG(p);
			}
		} else if (packet instanceof MultiPacket) {
			for (Packet p : ((MultiPacket) packet).getPackets()) {
				for (int i = 0; i < indent; i++)
					packetOutput.print("    ");
				if (p instanceof SWGPacket)
					outputSWG((SWGPacket) p);
				if (p instanceof DataChannelA)
					outputPacket(indent+1, p);
			}
		} else if (packet instanceof SWGPacket) {
			for (int i = 0; i < indent; i++)
				packetOutput.print("    ");
			outputSWG((SWGPacket) packet);
		} else {
			for (int i = 0; i < indent; i++)
				packetOutput.print("    ");
			packetOutput.println(packet.getClass().getSimpleName());
		}
	}
	
	private void outputSWG(SWGPacket p) {
		if (p instanceof Baseline)
			outputBaseline((Baseline) p);
		else if (p instanceof ObjectController)
			outputObjectController((ObjectController) p);
		else
			packetOutput.println(p.getClass().getSimpleName());
	}
	
	private void outputBaseline(Baseline b) {
		packetOutput.println("Baseline [" + b.getId() + "] " + b.getType() + " " + b.getNum());
	}
	
	private void outputObjectController(ObjectController cont) {
		int crc = cont.getControllerCrc();
		long id = cont.getObjectId();
		packetOutput.println("ObjectController [" + id + "] 0x" + Integer.toHexString(crc));
	}
	
	/**
	 * Returns the time in milliseconds since the server started initialization
	 * @return the core time represented as a double
	 */
	public double getCoreTime() {
		return (System.nanoTime()-startTime)/1E6;
	}
	
	private Galaxy getGalaxy() {
		PreparedStatement getGalaxy = getLocalDatabase().prepareStatement("SELECT * FROM galaxies WHERE id = ?");
		Config c = getConfig(ConfigFile.PRIMARY);
		ResultSet set = null;
		try {
			getGalaxy.setInt(1, galaxyId);
			set = getGalaxy.executeQuery();
			if (!set.next()) {
				System.err.println("CoreManager: No such galaxy exists with ID " + galaxyId + "!");
				return null;
			}
			Galaxy g = new Galaxy();
			g.setId(set.getInt("id"));
			g.setName(set.getString("name"));
			g.setAddress(set.getString("address"));
			g.setPopulation(set.getInt("population"));
			g.setTimeZone(set.getInt("timezone"));
			g.setZonePort(set.getInt("zone_port"));
			g.setPingPort(set.getInt("ping_port"));
			g.setStatus(set.getInt("status"));
			g.setMaxCharacters(c.getInt("GALAXY-MAX-CHARACTERS", 2));
			g.setOnlinePlayerLimit(c.getInt("GALAXY-MAX-ONLINE", 3000));
			g.setOnlineFreeTrialLimit(c.getInt("GALAXY-MAX-ONLINE", 3000));
			g.setRecommended(true);
			return g;
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (set != null) {
				try {
					set.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}
	
}
