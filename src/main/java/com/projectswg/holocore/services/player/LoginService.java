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
package com.projectswg.holocore.services.player;

import com.projectswg.common.data.BCrypt;
import com.projectswg.common.data.encodables.galaxy.Galaxy;
import com.projectswg.common.data.encodables.tangible.Race;
import com.projectswg.common.data.info.Config;
import com.projectswg.common.data.info.RelationalDatabase;
import com.projectswg.common.data.info.RelationalServerFactory;
import com.projectswg.common.network.packets.SWGPacket;
import com.projectswg.common.network.packets.swg.ErrorMessage;
import com.projectswg.common.network.packets.swg.login.*;
import com.projectswg.common.network.packets.swg.login.EnumerateCharacterId.SWGCharacter;
import com.projectswg.common.network.packets.swg.login.creation.DeleteCharacterRequest;
import com.projectswg.common.network.packets.swg.login.creation.DeleteCharacterResponse;
import com.projectswg.common.network.packets.swg.zone.GameServerLagResponse;
import com.projectswg.common.network.packets.swg.zone.LagRequest;
import com.projectswg.common.network.packets.swg.zone.ServerNowEpochTime;
import com.projectswg.holocore.ProjectSWG;
import com.projectswg.holocore.intents.LoginEventIntent;
import com.projectswg.holocore.intents.LoginEventIntent.LoginEvent;
import com.projectswg.holocore.intents.network.InboundPacketIntent;
import com.projectswg.holocore.intents.object.DestroyObjectIntent;
import com.projectswg.holocore.intents.player.DeleteCharacterIntent;
import com.projectswg.holocore.resources.config.ConfigFile;
import com.projectswg.holocore.resources.objects.SWGObject;
import com.projectswg.holocore.resources.player.AccessLevel;
import com.projectswg.holocore.resources.player.Player;
import com.projectswg.holocore.resources.player.Player.PlayerServer;
import com.projectswg.holocore.resources.player.PlayerState;
import com.projectswg.holocore.resources.server_info.DataManager;
import com.projectswg.holocore.services.objects.ObjectStorageService.ObjectLookup;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;
import me.joshlarson.jlcommon.log.Log;
import me.joshlarson.jlcommon.utilities.Arguments;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class LoginService extends Service {
	
	private static final String REQUIRED_VERSION = "20111130-15:46";
	private static final byte [] SESSION_TOKEN = new byte[24];
	
	private RelationalDatabase database;
	private PreparedStatement getUser;
	private PreparedStatement getCharacter;
	private PreparedStatement getCharacterFirstName;
	private PreparedStatement getCharacters;
	private PreparedStatement deleteCharacter;
	
	public LoginService() {
		
	}
	
	@Override
	public boolean initialize() {
		database = RelationalServerFactory.getServerDatabase("login/login.db");
		getUser = database.prepareStatement("SELECT * FROM users WHERE LOWER(username) = LOWER(?)");
		getCharacter = database.prepareStatement("SELECT id FROM players WHERE LOWER(name) = ?");
		getCharacterFirstName = database.prepareStatement("SELECT id FROM players WHERE LOWER(name) = ? OR LOWER(name) LIKE ?");
		getCharacters = database.prepareStatement("SELECT * FROM players WHERE userid = ?");
		deleteCharacter = database.prepareStatement("DELETE FROM players WHERE id = ?");
		return super.initialize();
	}
	
	@Override
	public boolean start() {
		synchronized (deleteCharacter) {
			try (ResultSet set = database.executeQuery("SELECT id FROM players")) {
				while (set.next()) {
					long id = set.getLong(1);
					if (ObjectLookup.getObjectById(id) != null)
						continue;
					
					try {
						deleteCharacter.setLong(1, id);
						deleteCharacter.executeUpdate();
						Log.d("Deleted character with id: %d", id);
					} catch (SQLException e) {
						Log.e(e);
					}
				}
			} catch (SQLException e) {
				Log.e(e);
			}
		}
		return super.start();
	}
	
	@Override
	public boolean terminate() {
		database.close();
		return super.terminate();
	}
	
	@IntentHandler
	private void handleDeleteCharacterIntent(DeleteCharacterIntent dci) {
		deleteCharacter(dci.getCreature());
	}
	
	@IntentHandler
	private void handleInboundPacketIntent(InboundPacketIntent gpi) {
		SWGPacket p = gpi.getPacket();
		if (p instanceof LoginClientId) {
			handleLogin(gpi.getPlayer(), (LoginClientId) p);
		} else if (p instanceof DeleteCharacterRequest) {
			handleCharDeletion(gpi.getPlayer(), (DeleteCharacterRequest) p);
		} else if (p instanceof LagRequest) {
			Player player = gpi.getPlayer();
			if (player.getPlayerServer() == PlayerServer.LOGIN)
				handleLagRequest(player);
		}
	}
	
	public long getCharacterIdByFirstName(@NotNull String name) {
		Objects.requireNonNull(name);
		name = name.trim().toLowerCase(Locale.US);
		Arguments.validate(!name.isEmpty(), "name cannot be empty");
		synchronized (getCharacterFirstName) {
			try {
				getCharacterFirstName.setString(1, name + "%");
				try (ResultSet set = getCharacterFirstName.executeQuery()) {
					if (set.next())
						return set.getLong("id");
				}
			} catch (SQLException e) {
				Log.e(e);
			}
		}
		return 0;
	}
	
	public long getCharacterId(@NotNull String name) {
		Objects.requireNonNull(name);
		name = name.trim().toLowerCase(Locale.US);
		Arguments.validate(!name.isEmpty(), "name cannot be empty");
		synchronized (getCharacter) {
			try {
				getCharacter.setString(1, name);
				try (ResultSet set = getCharacter.executeQuery()) {
					if (set.next())
						return set.getLong("id");
				}
			} catch (SQLException e) {
				Log.e(e);
			}
		}
		return 0;
	}
	
	private String getServerString() {
		Config c = DataManager.getConfig(ConfigFile.NETWORK);
		String name = c.getString("LOGIN-SERVER-NAME", "LoginServer");
		int id = c.getInt("LOGIN-SERVER-ID", 1);
		return name + ":" + id;
	}
	
	private void handleLagRequest(Player player) {
		player.sendPacket(new GameServerLagResponse());
	}
	
	private void handleCharDeletion(Player player, DeleteCharacterRequest request) {
		SWGObject obj = ObjectLookup.getObjectById(request.getPlayerId());
		boolean success = obj != null && deleteCharacter(obj);
		if (success) {
			new DestroyObjectIntent(obj).broadcast();
			Log.i("Deleted character %s for user %s", obj.getObjectName(), player.getUsername());
		} else {
			Log.e("Could not delete character! Character: ID: " + request.getPlayerId() + " / " + obj);
		}
		player.sendPacket(new DeleteCharacterResponse(success));
	}
	
	private void handleLogin(Player player, LoginClientId id) {
		if (player.getPlayerState() == PlayerState.LOGGED_IN) { // Client occasionally sends multiple login requests
			sendLoginSuccessPacket(player);
			return;
		}
		assert player.getPlayerState() == PlayerState.CONNECTED;
		assert player.getPlayerServer() == PlayerServer.NONE;
		player.setPlayerState(PlayerState.LOGGING_IN);
		player.setPlayerServer(PlayerServer.LOGIN);
		final boolean doClientCheck = DataManager.getConfig(ConfigFile.NETWORK).getBoolean("LOGIN-VERSION-CHECKS", true);
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
				Log.e(e);
				onLoginServerError(player, id);
			}
		}
	}
	
	private void onLoginClientVersionError(Player player, LoginClientId id) {
		Log.i("%s cannot login due to invalid version code: %s, expected %s from %s", player.getUsername(), id.getVersion(), REQUIRED_VERSION, id.getSocketAddress());
		String type = "Login Failed!";
		String message = "Invalid Client Version Code: " + id.getVersion();
		player.sendPacket(new ErrorMessage(type, message, false));
		player.setPlayerState(PlayerState.DISCONNECTED);
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_FAIL_INVALID_VERSION_CODE).broadcast();
	}
	
	private void onSuccessfulLogin(ResultSet user, Player player, LoginClientId id) throws SQLException {
		player.setUsername(user.getString("username"));
		player.setUserId(user.getInt("id"));
		switch(user.getString("access_level")) {
			case "player": player.setAccessLevel(AccessLevel.PLAYER); break;
			case "warden": player.setAccessLevel(AccessLevel.WARDEN); break;
			case "csr": player.setAccessLevel(AccessLevel.CSR); break;
			case "qa": player.setAccessLevel(AccessLevel.QA); break;
			case "dev": player.setAccessLevel(AccessLevel.DEV); break;
			default: player.setAccessLevel(AccessLevel.PLAYER); break;
		}
		player.setPlayerState(PlayerState.LOGGED_IN);
		sendLoginSuccessPacket(player);
		Log.i("%s connected to the login server from %s", player.getUsername(), id.getSocketAddress());
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_SUCCESS).broadcast();
	}
	
	private void onLoginBanned(Player player, LoginClientId id) {
		String type = "Login Failed!";
		String message = "Sorry, you're banned!";
		player.sendPacket(new ErrorMessage(type, message, false));
		Log.i("%s cannot login due to a ban, from %s", player.getUsername(), id.getSocketAddress());
		player.setPlayerState(PlayerState.DISCONNECTED);
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_FAIL_BANNED).broadcast();
	}
	
	private void onInvalidUserPass(Player player, LoginClientId id, ResultSet set) throws SQLException {
		String type = "Login Failed!";
		String message = getUserPassError(set, id.getUsername(), id.getPassword());
		player.sendPacket(new ErrorMessage(type, message, false));
		player.sendPacket(new LoginIncorrectClientId(getServerString(), REQUIRED_VERSION));
		Log.i("%s cannot login due to invalid user/pass from %s", id.getUsername(), id.getSocketAddress());
		player.setPlayerState(PlayerState.DISCONNECTED);
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_FAIL_INVALID_USER_PASS).broadcast();
	}
	
	private void onLoginServerError(Player player, LoginClientId id) {
		String type = "Login Failed!";
		String message = "Server Error.";
		player.sendPacket(new ErrorMessage(type, message, false));
		player.setPlayerState(PlayerState.DISCONNECTED);
		Log.e("%s cannot login due to server error, from %s", id.getUsername(), id.getSocketAddress());
		new LoginEventIntent(player.getNetworkId(), LoginEvent.LOGIN_FAIL_SERVER_ERROR).broadcast();
	}
	
	private void sendLoginSuccessPacket(Player player) {
		LoginClientToken token = new LoginClientToken(SESSION_TOKEN, player.getUserId(), player.getUsername());
		LoginEnumCluster cluster = new LoginEnumCluster();
		LoginClusterStatus clusterStatus = new LoginClusterStatus();
		List <Galaxy> galaxies = getGalaxies(player);
		List<SWGCharacter> characters = getCharacters(player.getUserId());
		for (Galaxy g : galaxies) {
			cluster.addGalaxy(g);
			clusterStatus.addGalaxy(g);
		}
		cluster.setMaxCharacters(DataManager.getConfig(ConfigFile.PRIMARY).getInt("GALAXY-MAX-CHARACTERS", 2));
		player.sendPacket(new ServerNowEpochTime((int)(System.currentTimeMillis()/1E3)));
		player.sendPacket(token);
		player.sendPacket(cluster);
		player.sendPacket(new CharacterCreationDisabled());
		player.sendPacket(new EnumerateCharacterId(characters));
		player.sendPacket(clusterStatus);
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
	
	private List <Galaxy> getGalaxies(Player p) {
		List<Galaxy> galaxies = new ArrayList<>();
		galaxies.add(ProjectSWG.getGalaxy());
		return galaxies;
	}
	
	private List<SWGCharacter> getCharacters(int userId) {
		List <SWGCharacter> characters = new ArrayList<>();
		synchronized (getCharacters) {
			try {
				getCharacters.setInt(1, userId);
				try (ResultSet set = getCharacters.executeQuery()) {
					while (set.next()) {
						SWGCharacter c = new SWGCharacter();
						c.setId(set.getInt("id"));
						c.setName(set.getString("name"));
						c.setGalaxyId(ProjectSWG.getGalaxy().getId());
						c.setRaceCrc(Race.getRaceByFile(set.getString("race")).getCrc());
						c.setType(1); // 1 = Normal (2 = Jedi, 3 = Spectral)
						characters.add(c);
					}
				}
			} catch (SQLException e) {
				Log.e(e);
			}
		}
		return characters;
	}
	
	private boolean deleteCharacter(SWGObject obj) {
		synchronized (deleteCharacter) {
			try {
				deleteCharacter.setLong(1, obj.getObjectId());
				return deleteCharacter.executeUpdate() > 0;
			} catch (SQLException e) {
				Log.e(e);
			}
			return false;
		}
	}
	
}
