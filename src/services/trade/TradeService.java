package services.trade;

import java.util.ArrayList;
import java.util.List;

import com.projectswg.common.control.Service;
import com.projectswg.common.debug.Log;

import intents.PlayerEventIntent;
import intents.chat.ChatBroadcastIntent;
import intents.network.GalacticPacketIntent;
import network.packets.Packet;
import network.packets.swg.SWGPacket;
import network.packets.swg.zone.object_controller.SecureTrade;
import network.packets.swg.zone.object_controller.SecureTrade.TradeMessageType;
import network.packets.swg.zone.trade.AbortTradeMessage;
import network.packets.swg.zone.trade.AcceptTransactionMessage;
import network.packets.swg.zone.trade.AddItemMessage;
import network.packets.swg.zone.trade.BeginTradeMessage;
import network.packets.swg.zone.trade.BeginVerificationMessage;
import network.packets.swg.zone.trade.DenyTradeMessage;
import network.packets.swg.zone.trade.GiveMoneyMessage;
import network.packets.swg.zone.trade.RemoveItemMessage;
import network.packets.swg.zone.trade.TradeCompleteMessage;
import network.packets.swg.zone.trade.UnAcceptTransactionMessage;
import network.packets.swg.zone.trade.VerifyTradeMessage;
import resources.Posture;
import resources.containers.ContainerPermissionsType;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.objects.custom.AIObject;
import resources.player.Player;
import resources.sui.SuiButtons;
import resources.sui.SuiMessageBox;
import services.objects.ObjectManager;

public class TradeService extends Service {
	
	private List<TradeSession> tradeSessions;
	
	public TradeService() {
		tradeSessions = new ArrayList<TradeSession>();
		
		registerForIntent(GalacticPacketIntent.class, gpi -> handleGalacticPacketIntent(gpi));
		registerForIntent(PlayerEventIntent.class, pei -> handlePlayerEventIntent(pei));
	}
	
	@Override
	public boolean terminate() {
		for (TradeSession tradeSession : tradeSessions) {
			tradeSession.getAccepter().sendSelf(new AbortTradeMessage());
			tradeSession.getAccepter().sendSelf(new TradeCompleteMessage());	
			tradeSession.getAccepter().setTradeSession(null);
			tradeSession.getInitiator().sendSelf(new AbortTradeMessage());
			tradeSession.getInitiator().sendSelf(new TradeCompleteMessage());	
			tradeSession.getInitiator().setTradeSession(null);
		}
		return super.terminate();
	}
	
	private void handlePlayerEventIntent(PlayerEventIntent pei) {
		switch (pei.getEvent()) {
			case PE_FIRST_ZONE:
				if(pei.getPlayer().getCreatureObject().getTradeSession() != null){
					abortTrade(pei.getPlayer());
				}
				break;
			case PE_LOGGED_OUT:
				if(pei.getPlayer().getCreatureObject().getTradeSession() != null){
					abortTrade(pei.getPlayer());
				}
				break;
			default:
				break;
		}
	}

	private void handleGalacticPacketIntent(GalacticPacketIntent gpi) {
		Packet packet = gpi.getPacket();
		if (packet instanceof SWGPacket){
		    Log.d("RX Packet: %s, Packet Name: %s", ((SWGPacket) gpi.getPacket()).getPacketType(), gpi.getPacket().toString());
		}
				
		if (packet instanceof SecureTrade) {
			handleSecureTrade((SecureTrade) packet,gpi.getPlayer(), gpi.getObjectManager());
		} else if (packet instanceof AbortTradeMessage){
			handleAbortTradeMessage(gpi.getPlayer());
		} else if (packet instanceof DenyTradeMessage){
			handleDenyTradeMessage(gpi.getPlayer());
		} else if (packet instanceof AcceptTransactionMessage){
			handleAcceptTransactionMessage(gpi.getPlayer());
		} else if (packet instanceof UnAcceptTransactionMessage){
			handleUnAcceptTransactionMessage(gpi.getPlayer());
		} else if (packet instanceof AddItemMessage){
			handleAddItemMessage((AddItemMessage) packet, gpi.getPlayer(), gpi.getObjectManager());
		} else if (packet instanceof GiveMoneyMessage){
			handleGiveMoneyMessage((GiveMoneyMessage) packet, gpi.getPlayer());
		} else if (packet instanceof BeginVerificationMessage){
			handleBeginVerificationMessage(gpi.getPlayer());
		} else if (packet instanceof VerifyTradeMessage){
			handleVerifyTradeMessage(gpi.getPlayer(), gpi.getObjectManager());
		} else if (packet instanceof TradeCompleteMessage){
			handleTradeCompleteMessage(gpi.getPlayer());
		}
	}

	private void handleSecureTrade(SecureTrade packet, Player player, ObjectManager objectManager) {
		CreatureObject initiator = player.getCreatureObject();
		CreatureObject accepter = (CreatureObject) objectManager.getObjectById(packet.getAccepterId());
		
		if(accepter.isInCombat()){
			return;
		}
		
		if(initiator.isInCombat()){
			return;
		}
		
		if(accepter instanceof AIObject){
			sendSystemMessage(initiator.getOwner(), "start_fail_target_not_player");
			return;
		}
		
		if(initiator.getPosture() == Posture.INCAPACITATED){
			sendSystemMessage(initiator.getOwner(), "player_incapacitated");
			return;
		}
		
		if(initiator.getPosture() == Posture.DEAD){
			sendSystemMessage(initiator.getOwner(), "player_dead");
			return;
		}
		
		if(accepter.getPosture() == Posture.INCAPACITATED){
			sendSystemMessage(initiator.getOwner(), "target_incapacitated");
			return;
		}
		
		if(accepter.getPosture() == Posture.DEAD){
			sendSystemMessage(initiator.getOwner(), "target_dead");
			return;
		}
		
		TradeSession tradeSession = new TradeSession(initiator, accepter);
		tradeSessions.add(tradeSession);
		initiator.setTradeSession(tradeSession);
		tradeSessions.add(tradeSession);
		handleTradeSessionRequest(packet, player, initiator, accepter);
		Log.d("Trade Session Request. Type=%s  Initiator=%d  Receipient=%d PacketSenderID: %d", packet.getType(), packet.getStarterId(), packet.getAccepterId(), player.getCreatureObject().getObjectId());
	}

	private void handleAbortTradeMessage(Player player) {	
		TradeSession tradeSession = player.getCreatureObject().getTradeSession();
		
		if(tradeSession == null)
			return;

		abortTrade(player);
	}
	
	private void handleDenyTradeMessage(Player player) {
		TradeSession tradeSession = player.getCreatureObject().getTradeSession();
		tradeSession.sendToPartner(player.getCreatureObject(), new DenyTradeMessage());
	}

	private void handleAcceptTransactionMessage(Player player) {
		TradeSession tradeSession = player.getCreatureObject().getTradeSession();
		tradeSession.sendToPartner(player.getCreatureObject(), new AcceptTransactionMessage());
	}
	
	private void handleUnAcceptTransactionMessage(Player player) {
		TradeSession tradeSession = player.getCreatureObject().getTradeSession();
		tradeSession.sendToPartner(player.getCreatureObject(), new UnAcceptTransactionMessage());
	}
	
	private void handleAddItemMessage(AddItemMessage packet, Player player, ObjectManager objectManager) {
		CreatureObject creature = player.getCreatureObject();
		TradeSession tradeSession = creature.getTradeSession();
		SWGObject tradeObject = objectManager.getObjectById(packet.getObjectId());		
		if (creature.getTradeSession() == null) {
			Log.w("Invalid Tradesession");
			return;
		}
		
		CreatureObject partner = creature.getTradeSession().getTradePartner(creature);
		if (partner == null) {
			Log.w("Invalid trading session ....");
			return;
		}
		
		if(tradeObject.hasAttribute("no_trade")){
			sendSystemMessage(player, "add_item_failed_prose");
			tradeSession.sendToPartner(creature, new RemoveItemMessage(packet.getObjectId()));
			tradeSession.removeFromItemList(creature, packet.getObjectId());
		}
		
		creature.getTradeSession().addItem(creature, tradeObject);
		partner.setContainerPermissions(ContainerPermissionsType.INVENTORY);
		tradeSession.sendToPartner(partner, new AddItemMessage(packet.getObjectId()));	
		partner.addCustomAware(tradeObject);
	}
	
	private void handleGiveMoneyMessage(GiveMoneyMessage packet, Player player) {
		TradeSession tradeSession = player.getCreatureObject().getTradeSession();
		tradeSession.setMoneyAmount(player.getCreatureObject(), packet.getMoneyAmount());
		tradeSession.sendToPartner(player.getCreatureObject(), new GiveMoneyMessage(packet.getMoneyAmount()));
	}
	
	private void handleVerifyTradeMessage(Player player, ObjectManager objectManager) {
		TradeSession tradeSession = player.getCreatureObject().getTradeSession();
		CreatureObject initiator = tradeSession.getInitiator();
		CreatureObject accepter = tradeSession.getAccepter();
				
		if(!tradeSession.getFromItemList(accepter).isEmpty()){
			tradeSession.moveToPartnerInventory(accepter, tradeSession.getFromItemList(accepter));		
		} 
		
		if(!tradeSession.getFromItemList(initiator).isEmpty()){
			tradeSession.moveToPartnerInventory(initiator, tradeSession.getFromItemList(initiator));
		}
		
		long oldMoneyBalanceInititater = initiator.getCashBalance();
		long oldMoneyBalanceAccepter = accepter.getCashBalance();
		
		if(tradeSession.getMoneyAmount(initiator) != 0){
			accepter.setCashBalance(oldMoneyBalanceAccepter + tradeSession.getMoneyAmount(initiator));
			initiator.setCashBalance(oldMoneyBalanceInititater - tradeSession.getMoneyAmount(initiator)); 
		} else {
			initiator.setCashBalance(oldMoneyBalanceInititater + tradeSession.getMoneyAmount(accepter));
			accepter.setCashBalance(oldMoneyBalanceAccepter - tradeSession.getMoneyAmount(accepter));
		}
		
		accepter.sendSelf(new TradeCompleteMessage());
		initiator.sendSelf(new TradeCompleteMessage());
	}
	
	private void handleTradeCompleteMessage(Player player) {
		TradeSession tradeSession = player.getCreatureObject().getTradeSession();
		tradeSession.sendToPartner(player.getCreatureObject(), new TradeCompleteMessage());
		tradeSession.getAccepter().setTradeSession(null);
		tradeSession.getInitiator().setTradeSession(null);
	}
	
	private void handleBeginVerificationMessage(Player player) {
		TradeSession tradeSession = player.getCreatureObject().getTradeSession();		
		tradeSession.getAccepter().sendSelf(new VerifyTradeMessage());
		tradeSession.getInitiator().sendSelf(new VerifyTradeMessage());
	}
	
	private void handleTradeSessionRequest(SecureTrade packet, Player packetSender , CreatureObject initiator, CreatureObject accepter) {		
		SuiMessageBox requestBox = new SuiMessageBox(SuiButtons.OK_CANCEL, "Trade Request",	accepter.getOwner().getCharacterName() + " wants to trade with you.\nDo you want to accept the request?");
		requestBox.display(accepter.getOwner());
		requestBox.addOkButtonCallback("handleTradeRequest", (player, actor, event, paramenters)-> {
			if(initiator.getTradeSession() != null && initiator.getTradeSession().getInitiator() != null){
				accepter.setTradeSession(initiator.getTradeSession());
				accepter.sendSelf(new SecureTrade(TradeMessageType.REQUEST_TRADE, initiator.getObjectId(), accepter.getObjectId()));
				initiator.sendSelf(new SecureTrade(TradeMessageType.REQUEST_TRADE, initiator.getObjectId(), accepter.getObjectId()));
				initiator.sendSelf(new BeginTradeMessage(accepter.getObjectId()));
				accepter.sendSelf(new BeginTradeMessage(initiator.getObjectId()));
				Log.d("Trade Session Request. Type=%s  Initiator=%d  Receipient=%d PacketSenderID: %d", packet.getType(), packet.getStarterId(), packet.getAccepterId(), player.getCreatureObject().getObjectId());
			}
		});
		requestBox.addCancelButtonCallback("handleTradeRequestDeny", (player, actor, event, paramenters)-> {
			if(packetSender.getCreatureObject().getObjectId() != accepter.getObjectId()){
				initiator.sendSelf(new DenyTradeMessage());
				initiator.sendSelf(new AbortTradeMessage());
			} else {
				accepter.sendSelf(new DenyTradeMessage());
				accepter.sendSelf(new AbortTradeMessage());
			}
		});
		Log.i("Player: %s sent TradeRequest to Player %s", initiator.getOwner().getCharacterName(), accepter.getOwner().getCharacterName());
	}
	
	private void sendSystemMessage(Player player, String str) {
		new ChatBroadcastIntent(player, "@ui_trade:" + str).broadcast();
	}

	private void abortTrade(Player player) {
		TradeSession tradeSession = player.getCreatureObject().getTradeSession();
		tradeSession.getAccepter().sendSelf(new AbortTradeMessage());
		tradeSession.getAccepter().sendSelf(new TradeCompleteMessage());
		tradeSession.getInitiator().sendSelf(new AbortTradeMessage());
		tradeSession.getInitiator().sendSelf(new TradeCompleteMessage());
		tradeSession.getAccepter().setTradeSession(null);
		tradeSession.getInitiator().setTradeSession(null);		
	}	
}