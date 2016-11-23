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
package services.network;

import intents.network.CloseConnectionIntent;
import intents.network.ConnectionClosedIntent;
import intents.network.ConnectionOpenedIntent;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import network.NetworkClient;
import network.packets.swg.holo.HoloConnectionStopped.ConnectionStoppedReason;
import resources.config.ConfigFile;
import resources.control.Intent;
import resources.control.Manager;
import resources.network.DisconnectReason;
import resources.network.TCPServer;
import resources.network.TCPServer.TCPCallback;
import resources.server_info.Log;

public class NetworkClientManager extends Manager implements TCPCallback {
	
	private final InboundNetworkManager inboundManager;
	private final OutboundNetworkManager outboundManager;
	private final ClientManager clientManager;
	private final TCPServer tcpServer;
	
	public NetworkClientManager() {
		clientManager = new ClientManager();
		tcpServer = new TCPServer(getBindPort(), getBufferSize());
		inboundManager = new InboundNetworkManager(clientManager);
		outboundManager = new OutboundNetworkManager(tcpServer, clientManager);
		
		addChildService(inboundManager);
		addChildService(outboundManager);
		
		registerForIntent(CloseConnectionIntent.TYPE);
	}
	
	@Override
	public boolean start() {
		try {
			tcpServer.bind();
			tcpServer.setCallback(this);
		} catch (IOException e) {
			e.printStackTrace();
			if (e instanceof BindException)
				Log.e(this, "Failed to bind to " + getBindPort());
			return false;
		}
		return super.start();
	}
	
	@Override
	public boolean stop() {
		tcpServer.close();
		return super.stop();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		switch (i.getType()) {
			case CloseConnectionIntent.TYPE:
				if (i instanceof CloseConnectionIntent)
					processCloseConnectionIntent((CloseConnectionIntent) i);
				break;
		}
	}
	
	private void processCloseConnectionIntent(CloseConnectionIntent i) {
		NetworkClient client = clientManager.getClient(i.getNetworkId());
		if (client != null)
			onSessionDisconnect(client.getAddress(), getHolocoreReason(i.getDisconnectReason()));
	}
	
	private ConnectionStoppedReason getHolocoreReason(DisconnectReason reason) {
		switch (reason) {
			case APPLICATION:
				return ConnectionStoppedReason.APPLICATION;
			case CONNECTION_REFUSED:
				return ConnectionStoppedReason.NETWORK;
			case NEW_CONNECTION_ATTEMPT:
				return ConnectionStoppedReason.NETWORK;
			case OTHER_SIDE_TERMINATED:
				return ConnectionStoppedReason.OTHER_SIDE_TERMINATED;
			case TIMEOUT:
				return ConnectionStoppedReason.NETWORK;
		}
		return ConnectionStoppedReason.APPLICATION;
	}
	
	@Override
	public void onIncomingConnection(Socket s) {
		SocketAddress addr = s.getRemoteSocketAddress();
		if (addr instanceof InetSocketAddress)
			onSessionConnect((InetSocketAddress) addr);
		else if (addr != null)
			Log.e(this, "Incoming connection has socket address of instance: %s", addr.getClass().getSimpleName());
	}
	
	@Override
	public void onConnectionDisconnect(Socket s, SocketAddress addr) {
		if (addr instanceof InetSocketAddress)
			onSessionDisconnect((InetSocketAddress) addr, ConnectionStoppedReason.APPLICATION);
		else if (addr != null)
			Log.e(this, "Connection Disconnected. Has socket address of instance: %s", addr.getClass().getSimpleName());
	}
	
	@Override
	public void onIncomingData(Socket s, byte [] data) {
		SocketAddress addr = s.getRemoteSocketAddress();
		if (addr instanceof InetSocketAddress)
			onInboundData((InetSocketAddress) addr, data);
		else if (addr != null)
			Log.e(this, "Incoming data has socket address of instance: %s", addr.getClass().getSimpleName());
	}
	
	private int getBindPort() {
		return getConfig(ConfigFile.NETWORK).getInt("BIND-PORT", 44463);
	}
	
	private int getBufferSize() {
		return getConfig(ConfigFile.NETWORK).getInt("BUFFER-SIZE", 4096);
	}
	
	private void onSessionConnect(InetSocketAddress addr) {
		NetworkClient client = clientManager.createSession(addr);
		inboundManager.onSessionCreated(client);
		outboundManager.onSessionCreated(client);
		new ConnectionOpenedIntent(client.getNetworkId()).broadcast();
	}
	
	private void onInboundData(InetSocketAddress addr, byte [] data) {
		inboundManager.onInboundData(addr, data);
	}
	
	private void onSessionDisconnect(InetSocketAddress addr, ConnectionStoppedReason reason) {
		NetworkClient client = clientManager.getClient(addr);
		if (client != null) {
			client.onDisconnected(reason);
			client.onSessionDestroyed();
			inboundManager.onSessionDestroyed(client);
			outboundManager.onSessionDestroyed(client);
		}
		tcpServer.disconnect(addr);
		new ConnectionClosedIntent(client.getNetworkId(), reason).broadcast();
	}
	
}
