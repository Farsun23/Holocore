package network.packets.swg.zone.chat;

import java.nio.ByteBuffer;

import network.packets.swg.SWGPacket;

public class ChatPersistentMessageToServer extends SWGPacket {
	public static final int CRC = 0x25A29FA6;
	
	private String message;
	private String outOfBand;
	private int counter;
	private String subject;
	private String galaxy;
	private String recipient;
	
	public ChatPersistentMessageToServer() {
		message = "";
		outOfBand = "";
		subject = "";
		
		galaxy = "";
		recipient = "";
	}
	
	@Override
	public void decode(ByteBuffer data) {
		if (!super.decode(data, CRC))
			return;
		message = getUnicode(data);
		outOfBand = getUnicode(data);
		counter = getInt(data);
		subject = getUnicode(data);
		
		getAscii(data); // "SWG"
		galaxy = getAscii(data);
		recipient = getAscii(data);
	}
	
	@Override
	public ByteBuffer encode() {
		int dataLength = 31 + message.length()*2+outOfBand.length()*2+subject.length()*2+galaxy.length()+recipient.length();
		ByteBuffer data = ByteBuffer.allocate(dataLength);
		addUnicode(data, message);
		addUnicode(data, outOfBand);
		addInt(data, counter);
		addUnicode(data, subject);
		addAscii(data, "SWG");
		addAscii(data, galaxy);
		addAscii(data, recipient);
		return data;
	}
	
	public String getMessage() {
		return message;
	}
	
	public String getOutOfBand() {
		return outOfBand;
	}
	
	public int getCounter() {
		return counter;
	}
	
	public String getSubject() {
		return subject;
	}
	
	public String getCluster() {
		return galaxy;
	}
	
	public String getRecipient() {
		return recipient;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
	
	public void setOutOfBand(String outOfBand) {
		this.outOfBand = outOfBand;
	}
	
	public void setCounter(int counter) {
		this.counter = counter;
	}
	
	public void setSubject(String subject) {
		this.subject = subject;
	}
	
	public void setCluster(String cluster) {
		this.galaxy = cluster;
	}
	
	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}
	
}
