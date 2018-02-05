/*******************************************************************************
 * Copyright (c) 2015 /// Project SWG /// www.projectswg.com
 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies.
 * Our goal is to create an emulator which will provide a server for players to
 * continue playing a game similar to the one they used to play. We are basing
 * it on the final publish of the game prior to end-game events.
 *
 * This file is part of Holocore.
 *
 * --------------------------------------------------------------------------------
 *
 * Holocore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Holocore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/

package services.chat;

import com.projectswg.common.control.Service;
import com.projectswg.common.data.encodables.chat.ChatAvatar;
import com.projectswg.common.data.encodables.chat.ChatResult;
import com.projectswg.common.data.encodables.chat.ChatRoom;
import com.projectswg.common.data.encodables.oob.OutOfBandPackage;
import com.projectswg.common.network.packets.SWGPacket;
import com.projectswg.common.network.packets.swg.zone.chat.*;
import com.projectswg.common.network.packets.swg.zone.insertion.ChatRoomList;
import intents.PlayerEventIntent;
import intents.chat.ChatRoomUpdateIntent;
import intents.network.GalacticPacketIntent;
import resources.player.Player;
import services.player.PlayerManager.PlayerLookup;

public class ChatRoomService extends Service {
	
	private final ChatRoomHandler chatRoomHandler;
	
	public ChatRoomService() {
		chatRoomHandler = new ChatRoomHandler();
		
		registerForIntent(ChatRoomUpdateIntent.class, this::handleChatRoomUpdateIntent);
		registerForIntent(GalacticPacketIntent.class, this::handleGalacticPacketIntent);
		registerForIntent(PlayerEventIntent.class, this::handlePlayerEventIntent);
	}
	
	@Override
	public boolean initialize() {
		return super.initialize() && chatRoomHandler.initialize();
	}
	
	@Override
	public boolean terminate() {
		return chatRoomHandler.terminate() && super.terminate();
	}
	
	private void handleGalacticPacketIntent(GalacticPacketIntent gpi) {
		SWGPacket packet = gpi.getPacket();
		Player player = gpi.getPlayer();
		switch (packet.getPacketType()) {
			case CHAT_QUERY_ROOM:
				handleChatQueryRoom(player, (ChatQueryRoom) packet);
				break;
			case CHAT_ENTER_ROOM_BY_ID:
				chatRoomHandler.enterChatChannel(player, ((ChatEnterRoomById) packet).getRoomId(), ((ChatEnterRoomById) packet).getSequence());
				break;
			case CHAT_REMOVE_AVATAR_FROM_ROOM:
				chatRoomHandler.leaveChatChannel(player, ((ChatRemoveAvatarFromRoom) packet).getPath());
				break;
			case CHAT_SEND_TO_ROOM:
				handleChatSendToRoom(player, (ChatSendToRoom) packet);
				break;
			case CHAT_REQUEST_ROOM_LIST:
				handleChatRoomListRequest(player);
				break;
			case CHAT_CREATE_ROOM:
				handleChatCreateRoom(player, (ChatCreateRoom) packet);
				break;
			case CHAT_DESTROY_ROOM:
				handleChatDestroyRoom(player, (ChatDestroyRoom) packet);
				break;
			case CHAT_INVITE_AVATAR_TO_ROOM:
				handleChatInviteToRoom(player, (ChatInviteAvatarToRoom) packet);
				break;
			case CHAT_UNINVITE_FROM_ROOM:
				handleChatUninviteFromRoom(player, (ChatUninviteFromRoom) packet);
				break;
			case CHAT_KICK_AVATAR_FROM_ROOM:
				handleChatKickAvatarFromRoom(player, (ChatKickAvatarFromRoom) packet);
				break;
			case CHAT_BAN_AVATAR_FROM_ROOM:
				handleChatBanAvatarFromRoom(player, (ChatBanAvatarFromRoom) packet);
				break;
			case CHAT_UNBAN_AVATAR_FROM_ROOM:
				handleChatUnbanAvatarFromRoom(player, (ChatUnbanAvatarFromRoom) packet);
				break;
			case CHAT_ADD_MODERATOR_TO_ROOM:
				handleChatAddModeratorToRoom(player, (ChatAddModeratorToRoom) packet);
				break;
			case CHAT_REMOVE_MODERATOR_FROM_ROOM:
				handleChatRemoveModeratorFromRoom(player, (ChatRemoveModeratorFromRoom) packet);
				break;
			default:
				break;
		}
	}
	
	private void handlePlayerEventIntent(PlayerEventIntent pei) {
		switch (pei.getEvent()) {
			case PE_ZONE_IN_CLIENT:
				chatRoomHandler.enterChatChannels(pei.getPlayer());
				chatRoomHandler.enterPlanetaryChatChannels(pei.getPlayer());
				break;
			default:
				break;
		}
	}
	
	private void handleChatRoomUpdateIntent(ChatRoomUpdateIntent crui) {
		switch (crui.getUpdateType()) {
			case CREATE:
				chatRoomHandler.createRoom(crui.getAvatar(), crui.isPublic(), false, crui.getPath(), crui.getTitle(), false);
				break;
			case DESTROY:
				chatRoomHandler.notifyDestroyRoom(crui.getAvatar(), crui.getPath(), 0);
				break;
			case JOIN:
				chatRoomHandler.enterChatChannel(crui.getPlayer(), crui.getPath(), crui.isIgnoreInvitation());
				break;
			case LEAVE:
				chatRoomHandler.leaveChatChannel(crui.getPlayer(), crui.getPath());
				break;
			case SEND_MESSAGE:
				chatRoomHandler.sendMessageToRoom(crui.getPlayer(), crui.getPath(), 0, crui.getMessage(), new OutOfBandPackage());
				break;
			default:
				break;
		}
	}
	
	/* Chat Rooms */
	
	private void handleChatRemoveModeratorFromRoom(Player player, ChatRemoveModeratorFromRoom p) {
		ChatAvatar target = p.getAvatar();
		ChatRoom room = chatRoomHandler.getRoomByPath(p.getRoom());
		ChatAvatar sender = new ChatAvatar(player.getCharacterChatName());
		ChatResult result = ChatResult.SUCCESS;
		
		if (room == null)
			result = ChatResult.ROOM_INVALID_NAME;
		else if (!room.isModerated())
			result = ChatResult.CUSTOM_FAILURE;
		else if (!room.isModerator(sender))
			result = ChatResult.ROOM_AVATAR_NO_PERMISSION;
		else if (!room.removeModerator(target))
			result = ChatResult.TARGET_AVATAR_DOESNT_EXIST;
		
		player.sendPacket(new ChatOnRemoveModeratorFromRoom(target, sender, result.getCode(), p.getRoom(), p.getSequence()));
	}
	
	private void handleChatAddModeratorToRoom(Player player, ChatAddModeratorToRoom p) {
		ChatAvatar target = p.getAvatar();
		ChatRoom room = chatRoomHandler.getRoomByPath(p.getRoom());
		ChatAvatar sender = new ChatAvatar(player.getCharacterChatName());
		ChatResult result = ChatResult.SUCCESS;
		
		if (room == null)
			result = ChatResult.ROOM_INVALID_NAME;
		else if (!room.isModerated())
			result = ChatResult.CUSTOM_FAILURE;
		else if (!room.isModerator(sender))
			result = ChatResult.ROOM_AVATAR_NO_PERMISSION;
		else if (!room.removeModerator(target) || PlayerLookup.getPlayerByFirstName(target.getName()) == null)
			result = ChatResult.TARGET_AVATAR_DOESNT_EXIST;
		else if (room.addModerator(target))
			result = ChatResult.NONE;
		
		if (room.removeBanned(target)) {
			// Remove player from the ban list for players that have joined the room, since this player is now a moderator
			sendPacketToMembers(room, new ChatOnUnbanAvatarFromRoom(p.getRoom(), sender, target, ChatResult.SUCCESS.getCode(), 0));
		}
		
		player.sendPacket(new ChatOnAddModeratorToRoom(target, sender, result.getCode(), p.getRoom(), p.getSequence()));
	}
	
	private void handleChatUnbanAvatarFromRoom(Player player, ChatUnbanAvatarFromRoom p) {
		ChatAvatar target = p.getAvatar();
		ChatRoom room = chatRoomHandler.getRoomByPath(p.getRoom());
		ChatAvatar sender = new ChatAvatar(player.getCharacterChatName());
		ChatResult result = ChatResult.SUCCESS;
		
		if (room == null)
			result = ChatResult.ROOM_INVALID_NAME;
		else if (!room.isModerator(sender))
			result = ChatResult.ROOM_AVATAR_NO_PERMISSION;
		else if (!room.isBanned(target) || !room.removeBanned(target))
			result = ChatResult.ROOM_AVATAR_BANNED;
		
		sendPacketToMembers(room, new ChatOnUnbanAvatarFromRoom(p.getRoom(), sender, target, result.getCode(), p.getSequence()));
	}
	
	private void handleChatBanAvatarFromRoom(Player player, ChatBanAvatarFromRoom p) {
		ChatAvatar target = p.getAvatar();
		ChatRoom room = chatRoomHandler.getRoomByPath(p.getRoom());
		ChatAvatar sender = new ChatAvatar(player.getCharacterChatName());
		ChatResult result = ChatResult.SUCCESS;
		
		if (room == null)
			result = ChatResult.ROOM_INVALID_NAME;
		else if (!room.isModerator(sender))
			result = ChatResult.ROOM_AVATAR_NO_PERMISSION;
		else if (room.isBanned(target))
			result = ChatResult.ROOM_AVATAR_BANNED;
		else if (!room.isMember(target))
			result = ChatResult.TARGET_AVATAR_DOESNT_EXIST;
		
		room.removeModerator(target);
		room.removeInvited(target);
		room.addBanned(target);
		
		sendPacketToMembers(room, new ChatOnBanAvatarFromRoom(p.getRoom(), sender, target, result.getCode(), p.getSequence()));
	}
	
	private void handleChatKickAvatarFromRoom(Player player, ChatKickAvatarFromRoom p) {
		ChatAvatar target = p.getAvatar();
		ChatRoom room = chatRoomHandler.getRoomByPath(p.getRoom());
		ChatAvatar sender = new ChatAvatar(player.getCharacterChatName());
		ChatResult result = ChatResult.SUCCESS;
		
		if (room == null)
			result = ChatResult.ROOM_INVALID_NAME;
		else if (!room.isModerator(sender))
			result = ChatResult.ROOM_AVATAR_NO_PERMISSION;
		else if (!room.isMember(target))
			result = ChatResult.TARGET_AVATAR_DOESNT_EXIST;
		else if (PlayerLookup.getPlayerByFirstName(target.getName()) == null)
			result = ChatResult.TARGET_AVATAR_DOESNT_EXIST;
		
		sendPacketToMembers(room, new ChatOnKickAvatarFromRoom(target, sender, result.getCode(), p.getRoom()));
	}
	
	private void handleChatUninviteFromRoom(Player player, ChatUninviteFromRoom p) {
		ChatRoom room = chatRoomHandler.getRoomByPath(p.getRoom());
		ChatAvatar invitee = p.getAvatar();
		ChatAvatar sender = new ChatAvatar(player.getCharacterChatName());
		ChatResult result = ChatResult.SUCCESS;
		
		if (room == null)
			result = ChatResult.ROOM_INVALID_NAME;
		else if (room.isPublic())
			result = ChatResult.CUSTOM_FAILURE;
		else if (!room.isModerator(sender))
			result = ChatResult.ROOM_AVATAR_NO_PERMISSION;
		else if (!room.removeInvited(invitee))
			result = ChatResult.ROOM_PRIVATE;
		
		player.sendPacket(new ChatOnUninviteFromRoom(p.getRoom(), sender, invitee, result.getCode(), p.getSequence()));
	}
	
	private void handleChatInviteToRoom(Player player, ChatInviteAvatarToRoom p) {
		ChatRoom room = chatRoomHandler.getRoomByPath(p.getRoom());
		ChatAvatar invitee = p.getAvatar();
		ChatAvatar sender = new ChatAvatar(player.getCharacterChatName());
		ChatResult result = ChatResult.SUCCESS;
		
		if (room == null)
			result = ChatResult.ROOM_INVALID_NAME;
		else if (room.isPublic())
			result = ChatResult.CUSTOM_FAILURE;
		else if (!room.isModerator(sender))
			result = ChatResult.ROOM_AVATAR_NO_PERMISSION;
		
		Player invitedPlayer = PlayerLookup.getPlayerByFirstName(invitee.getName());
		if (result == ChatResult.SUCCESS && invitedPlayer == null)
			result = ChatResult.TARGET_AVATAR_DOESNT_EXIST;
		
		player.sendPacket(new ChatOnInviteToRoom(p.getRoom(), sender, invitee, result.getCode()));
		
		if (result == ChatResult.SUCCESS) {
			room.addInvited(invitee);
			// Notify the invited client that the room exists if not already in the clients chat lists
			invitedPlayer.sendPacket(new ChatRoomList(room));
			invitedPlayer.sendPacket(new ChatOnReceiveRoomInvitation(sender, p.getRoom()));
		}
	}
	
	private void handleChatDestroyRoom(Player player, ChatDestroyRoom p) {
		ChatRoom room = chatRoomHandler.getRoomById(p.getRoomId());
		ChatAvatar avatar = new ChatAvatar(player.getCharacterChatName());
		ChatResult result = ChatResult.SUCCESS;
		
		if ((room == null || !room.getCreator().equals(avatar) || !room.getOwner().equals(avatar)))
			result = ChatResult.ROOM_AVATAR_NO_PERMISSION;
		else if (!chatRoomHandler.notifyDestroyRoom(avatar, room.getPath(), p.getSequence()))
			result = ChatResult.NONE;
		
		player.sendPacket(new ChatOnDestroyRoom(avatar, result.getCode(), p.getRoomId(), p.getSequence()));
	}
	
	private void handleChatCreateRoom(Player player, ChatCreateRoom p) {
		String path = p.getRoomName();
		ChatResult result = ChatResult.SUCCESS;
		
		if (chatRoomHandler.getRoomByPath(path) != null)
			result = ChatResult.ROOM_ALREADY_EXISTS;
		else
			chatRoomHandler.createRoom(new ChatAvatar(player.getCharacterChatName()), p.isPublic(), p.isModerated(), path, p.getRoomTitle(), true);
		
		player.sendPacket(new ChatOnCreateRoom(result.getCode(), chatRoomHandler.getRoomByPath(path), p.getSequence()));
	}
	
	private void handleChatSendToRoom(Player player, ChatSendToRoom p) {
		chatRoomHandler.sendMessageToRoom(player, p.getRoomId(), p.getSequence(), p.getMessage(), p.getOutOfBandPackage());
	}
	
	private void handleChatQueryRoom(Player player, ChatQueryRoom p) {
		ChatRoom room = chatRoomHandler.getRoomByPath(p.getRoomPath()); // No result code is sent for queries
		if (room == null)
			return;
		
		player.sendPacket(new ChatQueryRoomResults(room, p.getSequence()));
	}
	
	private void handleChatRoomListRequest(Player player) {
		player.sendPacket(new ChatRoomList());
	}
	
	private static void sendMessage(ChatRoom room, ChatAvatar sender, String message, OutOfBandPackage oob) {
		ChatRoomMessage chatRoomMessage = new ChatRoomMessage(sender, room.getId(), message, oob);
		for (ChatAvatar member : room.getMembers()) {
			Player player = PlayerLookup.getPlayerByFirstName(member.getName());
			if (player.getPlayerObject().isIgnored(sender.getName()))
				continue;
			
			player.sendPacket(chatRoomMessage);
		}
	}
	
	private static void sendPacketToMembers(ChatRoom room, SWGPacket... packets) {
		for (ChatAvatar member : room.getMembers()) {
			Player player = PlayerLookup.getPlayerByFirstName(member.getName());
			player.sendPacket(packets);
		}
	}
	
}
