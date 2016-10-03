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
package services.player;

import intents.GalacticIntent;
import intents.LoginEventIntent;
import intents.LoginEventIntent.LoginEvent;
import intents.network.GalacticPacketIntent;
import intents.object.DestroyObjectIntent;
import intents.player.DeleteCharacterIntent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import network.packets.Packet;
import network.packets.swg.ErrorMessage;
import network.packets.swg.login.CharacterCreationDisabled;
import network.packets.swg.login.EnumerateCharacterId;
import network.packets.swg.login.EnumerateCharacterId.SWGCharacter;
import network.packets.swg.login.creation.DeleteCharacterRequest;
import network.packets.swg.login.creation.DeleteCharacterResponse;
import network.packets.swg.login.LoginClientId;
import network.packets.swg.login.LoginClientToken;
import network.packets.swg.login.LoginClusterStatus;
import network.packets.swg.login.LoginEnumCluster;
import network.packets.swg.login.LoginIncorrectClientId;
import network.packets.swg.zone.GameServerLagResponse;
import network.packets.swg.zone.LagRequest;
import network.packets.swg.zone.ServerNowEpochTime;
import resources.Galaxy;
import resources.Race;
import resources.common.BCrypt;
import resources.config.ConfigFile;
import resources.control.Intent;
import resources.control.Service;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.player.AccessLevel;
import resources.player.Player;
import resources.player.PlayerState;
import resources.player.Player.PlayerServer;
import resources.server_info.Config;
import resources.server_info.Log;
import resources.server_info.RelationalDatabase;
import services.CoreManager;

public class LoginService extends Service {
	
	private static final String REQUIRED_VERSION = "20111130-15:46";
	
	private Random random;
	private PreparedStatement getUser;
	private PreparedStatement getCharacter;
	private PreparedStatement getCharacters;
	private PreparedStatement deleteCharacter;
	
	public LoginService() {
		random = new Random();
		
		registerForIntent(DeleteCharacterIntent.TYPE);
		registerForIntent(GalacticPacketIntent.TYPE);
	}
	
	@Override
	public boolean initialize() {
		RelationalDatabase local = getLocalDatabase();
		getUser = local.prepareStatement("SELECT * FROM users WHERE LOWER(username) = LOWER(?)");
		getCharacter = local.prepareStatement("SELECT * FROM characters WHERE LOWER(name) = LOWER(?)");
		getCharacters = local.prepareStatement("SELECT * FROM characters WHERE userid = ?");
		deleteCharacter = local.prepareStatement("DELETE FROM characters WHERE id = ?");
		return super.initialize();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		if (i instanceof DeleteCharacterIntent) {
			deleteCharacter(((DeleteCharacterIntent) i).getCreature());
		} else if (i instanceof GalacticPacketIntent) {
			GalacticPacketIntent gpi = (GalacticPacketIntent) i;
			handlePacket(gpi, gpi.getPlayerManager().getPlayerFromNetworkId(gpi.getNetworkId()), gpi.getPacket());
		}
	}
	
	public void handlePacket(GalacticIntent intent, Player player, Packet p) {
		if (p instanceof LoginClientId)
			handleLogin(player, (LoginClientId) p);
		if (p instanceof DeleteCharacterRequest)
			handleCharDeletion(intent, player, (DeleteCharacterRequest) p);
		if (p instanceof LagRequest && player.getPlayerServer() == PlayerServer.LOGIN)
			handleLagRequest(player);
	}
	
	private String getServerString() {
		Config c = getConfig(ConfigFile.NETWORK);
		String name = c.getString("LOGIN-SERVER-NAME", "LoginServer");
		int id = c.getInt("LOGIN-SERVER-ID", 1);
		return name + ":" + id;
	}
	
	private void handleLagRequest(Player player) {
		player.sendPacket(new GameServerLagResponse());
	}
	
	private void handleCharDeletion(GalacticIntent intent, Player player, DeleteCharacterRequest request) {
		SWGObject obj = intent.getObjectManager().getObjectById(request.getPlayerId());
		boolean success = false;
		if (obj != null && obj instanceof CreatureObject) {
			success = deleteCharacter(obj);
			Log.i("LoginService", "Deleted character %s for user %s", ((CreatureObject)obj).getName(), player.getUsername());
		} else
			Log.w("LoginService", "Could not delete character! Character: ID: " + request.getPlayerId() + " / " + obj);
		player.sendPacket(new DeleteCharacterResponse(success));
	}
	
	private void handleLogin(Player player, LoginClientId id) {
		if (player.getPlayerState() != PlayerState.CONNECTED && player.getPlayerState() != PlayerState.LOGGED_IN) {
			Log.w(this, "Player cannot login when " + player.getPlayerState());
			return;
		}
		player.setPlayerServer(PlayerServer.LOGIN);
		final boolean doClientCheck = getConfig(ConfigFile.NETWORK).getBoolean("LOGIN-VERSION-CHECKS", true);
		if (!id.getVersion().equals(REQUIRED_VERSION) && doClientCheck) {
			onLoginClientVersionError(player, id);
			return;
		}
		synchronized (getUser) {
			try {
				getUser.setString(1, id.getUsername());
				try (ResultSet user = getUser.executeQuery()) {
					if (user.next()) {
						if (isUserValid(user, id.getPassword()))
							onSuccessfulLogin(user, player, id);
						else if (user.getBoolean("banned"))
							onLoginBanned(player, id);
						else
							onInvalidUserPass(player, id, user);
					} else
						onInvalidUserPass(player, id, null);
				}
			} catch (SQLException e) {
				e.printStackTrace();
				onLoginServerError(player, id);
			}
		}
	}
	
	private void onLoginClientVersionError(Player player, LoginClientId id) {
		Log.i("LoginService", "%s cannot login due to invalid version code: %s, expected %s from %s:%d", player.getUsername(), id.getVersion(), REQUIRED_VERSION, id.getAddress(), id.getPort());
		String type = "Login Failed!";
		String message = "Invalid Client Version Code: " + id.getVersion();
		player.sendPacket(new ErrorMessage(type, message, false));
		player.setPlayerState(PlayerState.DISCONNECTED);
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_FAIL_INVALID_VERSION_CODE).broadcast();
	}
	
	private void onSuccessfulLogin(ResultSet user, Player player, LoginClientId id) throws SQLException {
		player.setUsername(user.getString("username"));
		player.setUserId(user.getInt("id"));
		int tokenLength = getConfig(ConfigFile.NETWORK).getInt("SESSION-TOKEN-LENGTH", 24);
		byte [] sessionToken = new byte[tokenLength];
		random.nextBytes(sessionToken);
		player.setSessionToken(sessionToken);
		player.setPlayerState(PlayerState.LOGGING_IN);
		switch(user.getString("access_level")) {
			case "player": player.setAccessLevel(AccessLevel.PLAYER); break;
			case "warden": player.setAccessLevel(AccessLevel.WARDEN); break;
			case "csr": player.setAccessLevel(AccessLevel.CSR); break;
			case "qa": player.setAccessLevel(AccessLevel.QA); break;
			case "dev": player.setAccessLevel(AccessLevel.DEV); break;
			default: player.setAccessLevel(AccessLevel.PLAYER); break;
		}
		sendLoginSuccessPacket(player);
		Log.i("LoginService", "%s connected to the login server from %s:%d", player.getUsername(), id.getAddress(), id.getPort());
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_SUCCESS).broadcast();
	}
	
	private void onLoginBanned(Player player, LoginClientId id) {
		String type = "Login Failed!";
		String message = "Sorry, you're banned!";
		player.sendPacket(new ErrorMessage(type, message, false));
		Log.i("LoginService", "%s cannot login due to a ban, from %s:%d", player.getUsername(), id.getAddress(), id.getPort());
		player.setPlayerState(PlayerState.DISCONNECTED);
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_FAIL_BANNED).broadcast();
	}
	
	private void onInvalidUserPass(Player player, LoginClientId id, ResultSet set) throws SQLException {
		String type = "Login Failed!";
		String message = getUserPassError(set, id.getUsername(), id.getPassword());
		player.sendPacket(new ErrorMessage(type, message, false));
		player.sendPacket(new LoginIncorrectClientId(getServerString(), REQUIRED_VERSION));
		Log.i("LoginService", "%s cannot login due to invalid user/pass from %s:%d", id.getUsername(), id.getAddress(), id.getPort());
		player.setPlayerState(PlayerState.DISCONNECTED);
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_FAIL_INVALID_USER_PASS).broadcast();
	}
	
	private void onLoginServerError(Player player, LoginClientId id) {
		String type = "Login Failed!";
		String message = "Server Error.";
		player.sendPacket(new ErrorMessage(type, message, false));
		player.setPlayerState(PlayerState.DISCONNECTED);
		Log.e("LoginService", "%s cannot login due to server error, from %s:%d", id.getUsername(), id.getAddress(), id.getPort());
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_FAIL_SERVER_ERROR).broadcast();
	}
	
	private void sendLoginSuccessPacket(Player p) throws SQLException {
		LoginClientToken token = new LoginClientToken(p.getSessionToken(), p.getUserId(), p.getUsername());
		LoginEnumCluster cluster = new LoginEnumCluster();
		LoginClusterStatus clusterStatus = new LoginClusterStatus();
		List <Galaxy> galaxies = getGalaxies(p);
		SWGCharacter [] characters = getCharacters(p.getUserId());
		for (Galaxy g : galaxies) {
			cluster.addGalaxy(g);
			clusterStatus.addGalaxy(g);
		}
		cluster.setMaxCharacters(getConfig(ConfigFile.PRIMARY).getInt("GALAXY-MAX-CHARACTERS", 2));
		p.sendPacket(new ServerNowEpochTime((int)(System.currentTimeMillis()/1E3)));
		p.sendPacket(token);
		p.sendPacket(cluster);
		p.sendPacket(new CharacterCreationDisabled());
		p.sendPacket(new EnumerateCharacterId(characters));
		p.sendPacket(clusterStatus);
		p.setPlayerState(PlayerState.LOGGED_IN);
	}
	
	private boolean isUserValid(ResultSet set, String password) throws SQLException {
		if (password.isEmpty())
			return false;
		if (set.getBoolean("banned"))
			return false;
		String psqlPass = set.getString("password");
		if (psqlPass.length() != 60 && !psqlPass.startsWith("$2"))
			return psqlPass.equals(password);
		password = BCrypt.hashpw(BCrypt.hashpw(password, psqlPass), psqlPass);
		return psqlPass.equals(password);
	}
	
	private String getUserPassError(ResultSet set, String username, String password) throws SQLException {
		if (set == null)
			return "No username found";
		if (password.isEmpty())
			return "No password specified!";
		String psqlPass = set.getString("password");
		if (psqlPass.length() != 60 && !psqlPass.startsWith("$2")) {
			if (psqlPass.equals(password))
				return "Server Error.\n\nPassword appears to be correct. [Plaintext]";
			return "Invalid password";
		}
		password = BCrypt.hashpw(BCrypt.hashpw(password, psqlPass), psqlPass);
		if (psqlPass.equals(password))
			return "Server Error.\n\nPassword appears to be correct. [Hashed]";
		return "Invalid password";
	}
	
	public ResultSet getCharacter(String character) throws SQLException {
		synchronized (getCharacter) {
			getCharacter.setString(1, character);
			return getCharacter.executeQuery();
		}
	}
	
	private List <Galaxy> getGalaxies(Player p) throws SQLException {
		List<Galaxy> galaxies = new ArrayList<>();
		galaxies.add(CoreManager.getGalaxy());
		return galaxies;
	}
	
	private SWGCharacter [] getCharacters(int userId) throws SQLException {
		getCharacters.setInt(1, userId);
		ResultSet set = getCharacters.executeQuery();
		List <SWGCharacter> characters = new ArrayList<>();
		try {
			while (set.next()) {
				SWGCharacter c = new SWGCharacter();
				c.setId(set.getInt("id"));
				c.setName(set.getString("name"));
				c.setGalaxyId(CoreManager.getGalaxyId());
				c.setRaceCrc(Race.getRaceByFile(set.getString("race")).getCrc());
				c.setType(1); // 1 = Normal (2 = Jedi, 3 = Spectral)
				characters.add(c);
			}
		} finally {
			set.close();
		}
		return characters.toArray(new SWGCharacter[characters.size()]);
	}
	
	private boolean deleteCharacter(SWGObject obj) {
		new DestroyObjectIntent(obj).broadcast();
		synchronized (deleteCharacter) {
			try {
				deleteCharacter.setLong(1, obj.getObjectId());
				return deleteCharacter.executeUpdate() > 0;
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return false;
		}
	}
	
}
