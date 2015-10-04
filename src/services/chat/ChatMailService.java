package services.chat;

import intents.PlayerEventIntent;
import intents.chat.PersistentMessageIntent;
import intents.network.GalacticPacketIntent;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import network.packets.Packet;
import network.packets.swg.SWGPacket;
import network.packets.swg.zone.chat.ChatDeletePersistentMessage;
import network.packets.swg.zone.chat.ChatOnSendPersistentMessage;
import network.packets.swg.zone.chat.ChatPersistentMessageToClient;
import network.packets.swg.zone.chat.ChatPersistentMessageToServer;
import network.packets.swg.zone.chat.ChatRequestPersistentMessage;
import resources.chat.ChatResult;
import resources.control.Intent;
import resources.control.Service;
import resources.encodables.player.Mail;
import resources.objects.player.PlayerObject;
import resources.player.Player;
import resources.server_info.CachedObjectDatabase;
import resources.server_info.ObjectDatabase;
import services.player.PlayerManager;

public class ChatMailService extends Service {

	private final ObjectDatabase<Mail> mails;
	private int maxMailId;
	
	public ChatMailService() {
		mails = new CachedObjectDatabase<>("odb/mails.db");
		maxMailId = 1;
	}
	
	@Override
	public boolean initialize() {
		registerForIntent(GalacticPacketIntent.TYPE);
		registerForIntent(PersistentMessageIntent.TYPE);
		registerForIntent(PlayerEventIntent.TYPE);
		mails.load();
		mails.traverse(mail -> {
			if (mail.getId() >= maxMailId)
				maxMailId = mail.getId() + 1;
		});
		return super.initialize();
	}
	
	@Override
	public boolean terminate() {
		mails.close();
		return super.terminate();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		switch (i.getType()) {
			case GalacticPacketIntent.TYPE:
				if (i instanceof GalacticPacketIntent)
					processPacket((GalacticPacketIntent) i);
				break;
			case PersistentMessageIntent.TYPE:
				if (i instanceof PersistentMessageIntent)
					handlePersistentMessageIntent((PersistentMessageIntent) i);
				break;
			case PlayerEventIntent.TYPE:
				if (i instanceof PlayerEventIntent)
					handlePlayerEventIntent((PlayerEventIntent) i);
				break;
		}
	}
	
	private void handlePlayerEventIntent(PlayerEventIntent intent) {
		Player player = intent.getPlayer();
		if (player == null)
			return;

		switch (intent.getEvent()) {
			case PE_FIRST_ZONE:
				sendPersistentMessageHeaders(player, intent.getGalaxy());
				break;
			default:
				break;
		}
	}
	
	private void processPacket(GalacticPacketIntent intent) {
		Player player = intent.getPlayerManager().getPlayerFromNetworkId(intent.getNetworkId());
		if (player == null)
			return;
		Packet p = intent.getPacket();
		if (!(p instanceof SWGPacket))
			return;
		SWGPacket swg = (SWGPacket) p;
		String galaxyName = intent.getGalaxy().getName();
		switch (swg.getPacketType()) {
			/* Mails */
			case CHAT_PERSISTENT_MESSAGE_TO_SERVER:
				if (p instanceof ChatPersistentMessageToServer)
					handleSendPersistentMessage(intent.getPlayerManager(), player, galaxyName, (ChatPersistentMessageToServer) p);
				break;
			case CHAT_REQUEST_PERSISTENT_MESSAGE:
				if (p instanceof ChatRequestPersistentMessage)
					handlePersistentMessageRequest(player, galaxyName, (ChatRequestPersistentMessage) p);
				break;
			case CHAT_DELETE_PERSISTENT_MESSAGE:
				if (p instanceof ChatDeletePersistentMessage)
					deletePersistentMessage(((ChatDeletePersistentMessage) p).getMailId());
				break;
			default: break;
		}
	}
	
	private void handleSendPersistentMessage(PlayerManager playerMgr, Player sender, String galaxy, ChatPersistentMessageToServer request) {
		String recipientStr = request.getRecipient().toLowerCase(Locale.ENGLISH);
		
		if (recipientStr.contains(" "))
			recipientStr = recipientStr.split(" ")[0];
		
		Player recipient = playerMgr.getPlayerByCreatureFirstName(recipientStr);
		long recId = (recipient == null ? playerMgr.getCharacterIdByName(request.getRecipient()) : recipient.getCreatureObject().getObjectId());
		ChatResult result = ChatResult.SUCCESS;
		
		if (recId == 0)
			result = ChatResult.TARGET_AVATAR_DOESNT_EXIST;

		if (sender.getPlayerObject().isIgnored(recipientStr))
			result = ChatResult.IGNORED;

		sender.sendPacket(new ChatOnSendPersistentMessage(result, request.getCounter()));

		if (result != ChatResult.SUCCESS)
			return;

		Mail mail = new Mail(sender.getCharacterName().split(" ")[0].toLowerCase(), request.getSubject(), request.getMessage(), recId);
		mail.setId(maxMailId++);
		mail.setTimestamp((int) (new Date().getTime() / 1000));
		mail.setOutOfBandPackage(request.getOutOfBandPackage());
		mails.put(mail.getId(), mail);
		
		if (recipient != null)
			sendPersistentMessage(recipient, mail, MailFlagType.HEADER_ONLY, galaxy);
	}
	
	private void handlePersistentMessageIntent(PersistentMessageIntent intent) {
		if (intent.getReceiver() == null)
			return;
		
		Player recipient = intent.getReceiver().getOwner();
		
		if (recipient == null)
			return;
		
		Mail mail = intent.getMail();
		mail.setId(maxMailId);
		maxMailId++;
		mail.setTimestamp((int) (new Date().getTime() / 1000));
		
		mails.put(mail.getId(), mail);
		
		sendPersistentMessage(recipient, mail, MailFlagType.HEADER_ONLY, intent.getGalaxy());
	}
	
	private void handlePersistentMessageRequest(Player player, String galaxy, ChatRequestPersistentMessage request) {
		Mail mail = mails.get(request.getMailId());
		
		if (mail == null)
			return;
		
		if (mail.getReceiverId() != player.getCreatureObject().getObjectId())
			return;
		
		mail.setStatus(Mail.READ);
		sendPersistentMessage(player, mail, MailFlagType.FULL_MESSAGE, galaxy);
	}
	
	private void sendPersistentMessageHeaders(Player player, String galaxy) {
		if (player == null || player.getCreatureObject() == null)
			return;
		
		final List <Mail> playersMail = new LinkedList<>();
		final long receiverId = player.getCreatureObject().getObjectId();

		mails.traverse(element -> {
			if (element.getReceiverId() == receiverId)
				playersMail.add(element);
		});
		
		for (Mail mail : playersMail)
			sendPersistentMessage(player, mail, MailFlagType.HEADER_ONLY, galaxy);
	}
	
	private void sendPersistentMessage(Player receiver, Mail mail, MailFlagType requestType, String galaxy) {
		if (receiver == null || receiver.getCreatureObject() == null)
			return;

		PlayerObject ghost = receiver.getPlayerObject();
		if (ghost.isIgnored(mail.getSender())) {
			mails.remove(mail.getId());
			return;
		}

		ChatPersistentMessageToClient packet = null;
		
		switch (requestType) {
			case FULL_MESSAGE:
				packet = new ChatPersistentMessageToClient(mail, galaxy, false);
				break;
			case HEADER_ONLY:
				packet = new ChatPersistentMessageToClient(mail, galaxy, true);
				break;
		}
		
		receiver.sendPacket(packet);
	}
	
	private void deletePersistentMessage(int mailId) {
		mails.remove(mailId);
	}

	private enum MailFlagType {
		FULL_MESSAGE,
		HEADER_ONLY
	}
	
}
