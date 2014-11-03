package network.packets.swg.zone.chat;

import java.nio.ByteBuffer;

import network.packets.swg.SWGPacket;

public class ChatOnEnteredRoom extends SWGPacket {
	
	public static final int CRC = 0xE69BDC0A;
	
	private String galaxy;
	private String character;
	private ChannelStatus status;
	private int chatRoomId;
	
	public ChatOnEnteredRoom() {
		this("", "", ChannelStatus.SUCCESS, 0);
	}
	
	public ChatOnEnteredRoom(String galaxy, String character, ChannelStatus status, int chatRoomId) {
		this.galaxy = galaxy;
		this.character = character;
		this.status = status;
		this.chatRoomId = chatRoomId;
	}
	
	public ChatOnEnteredRoom(ByteBuffer data) {
		decode(data);
	}
	
	public void decode(ByteBuffer data) {
		if (!super.decode(data, CRC))
			return;
		getAscii(data);
		galaxy = getAscii(data);
		character = getAscii(data);
		status = ChannelStatus.fromInteger(getInt(data));
		chatRoomId = getInt(data);
		getInt(data);
	}
	
	public ByteBuffer encode() {
		ByteBuffer data = ByteBuffer.allocate(27 + galaxy.length() + character.length());
		addShort(data, 5);
		addInt  (data, CRC);
		addAscii(data, "SWG");
		addAscii(data, galaxy);
		addAscii(data, character);
		addInt  (data, status.getBitmask());
		addInt  (data, chatRoomId);
		addInt  (data, 0);
		return data;
	}
	
	public enum ChannelStatus {
		SUCCESS(0), // "You have joined the channel."
		NOT_INVITED(0x10), // "You cannot join '%TU (room name)' because you are not invted to the room."
		UNKNOWN(0x20); // "Chatroom '%TU (room name)' join failed for an unknown reason."
		
		private int bitmask;
		
		ChannelStatus(int bitmask) {
			this.bitmask = bitmask;
		}
		public int getBitmask() {
			return bitmask;
		}
		public static final ChannelStatus fromInteger(int i) {
			if (i == 0)
				return SUCCESS;
			if (i == 0x10)
				return NOT_INVITED;
			return UNKNOWN;
		}
	};	
	
}

