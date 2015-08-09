package utilities;

import intents.sui.SuiWindowIntent;
import resources.Terrain;
import resources.encodables.OutOfBandPackage;
import resources.encodables.ProsePackage;
import resources.encodables.StringId;
import resources.encodables.player.Mail;
import resources.objects.SWGObject;
import resources.objects.waypoint.WaypointObject;
import resources.player.Player;
import intents.chat.ChatBroadcastIntent;
import intents.chat.ChatBroadcastIntent.BroadcastType;
import intents.chat.PersistentMessageIntent;
import resources.sui.SuiWindow;

/**
 * @author Mads
 * This class is to be used exclusively in cases where using the intents directly isn't practical.
 */
public final class IntentFactory {

	private void broadcast(String message, Player source, BroadcastType type) {
		new ChatBroadcastIntent(message, source, source.getCreatureObject().getTerrain(), type).broadcast();
	}

	/**
	 * Sends a system message around the observing players for the source {@link Player}.
	 * @param message System message to broadcast.
	 * @param source Source of the system message, anyone observing this player will receive the system message.
	 */
	public void broadcastArea(String message, Player source) {
		broadcast(message, source, BroadcastType.AREA);
	}

	/**
	 * Sends a system message to the entire galaxy.
	 * @param message System message to broadcast.
	 */
	public void broadcastGalaxy(String message) {
		broadcast(message, null, BroadcastType.GALAXY);
	}

	/**
	 * Sends a system message to all players who are on the specified {@link Terrain}.
	 * @param terrain Terrain to broadcast system message on.
	 * @param message System message to broadcast.
	 */
	public void broadcastPlanet(Terrain terrain, String message) {
		new ChatBroadcastIntent(message, null, terrain, BroadcastType.PLANET).broadcast();
	}

	/**
	 * Sends a system message to the target.
	 * @param target Player receiving the message.
	 * @param message System message to send.
	 */
	public void sendSystemMessage(Player target, String message) {
		broadcast(message, target, BroadcastType.PERSONAL);
	}

	/**
	 * Sends a system message to the target as a ProsePackage.
	 * @param target Player receiving the message.
	 * @param table Table for the StringId
	 * @param key Key to use within the StringId
	 */
	public void sendSystemMessage(Player target, String table, String key) {
		new ChatBroadcastIntent(target, new ProsePackage(table, key)).broadcast();
	}

	/**
	 * Sends a system message to the target as a ProsePackage which allows prose keys to be used.
	 * <br><br>
	 * Example: <br>
	 * &nbsp&nbsp&nbsp&nbsp <i>sendSystemMessage(target, "base_player", "prose_deposit_success", "DI", 500);</i>
	 * <br><br>
	 * Using this method is the same as: <br>
	 * &nbsp <i>new ChatBroadcastIntent(target, new ProsePackage("StringId", new StringId(table, key), objects)).broadcast();</i>
	 * @param target Player receiving the system message.
	 * @param table Table for the StringId.
	 * @param key Key to use within the StringId.
	 * @param objects Collection of prose keys followed by with the value for the prose key.<br>As an example, <i>("DI", 500)</i> would
	 *                set the %DI to the value of 500 for the StringId.
	 *                Note that the prose key must always come first and the value for that key must always come second.
	 */
	public void sendSystemMessage(Player target, String table, String key, Object... objects) {
		new ChatBroadcastIntent(target, new ProsePackage("StringId", new StringId(table, key), objects)).broadcast();
	}

	/**
	 * Sends a mail to the receiver with the specified subject, message, from the specified sender.
	 * @param receiver Player receiving the mail.
	 * @param sender The sender of this mail.
	 * @param subject Subject of the mail.
	 * @param message Message for the mail.
	 */
	public void sendMail(SWGObject receiver, String sender, String subject, String message) {
		Mail mail = new Mail(sender, subject, message, receiver.getObjectId());

		new PersistentMessageIntent(receiver, mail, receiver.getOwner().getGalaxyName()).broadcast();
	}

	/**
	 * Sends a mail to the receiver with the specified subject, message, from the specified sender. In addition to a normal
	 * string based mail, the provided waypoints are added as attachments to the message.
	 * @param receiver Player receiving the mail.
	 * @param sender The sender of this mail.
	 * @param subject Subject of the mail.
	 * @param message Message for the mail.
	 * @param waypoints Waypoints to attach to the mail.
	 */
	public void sendMail(SWGObject receiver, String sender, String subject, String message, WaypointObject... waypoints) {
		Mail mail = new Mail(sender, subject, message, receiver.getObjectId());
		mail.setOutOfBandPackage(new OutOfBandPackage(waypoints));

		new PersistentMessageIntent(receiver, mail, receiver.getOwner().getGalaxyName()).broadcast();
	}

	/**
	 * Displays the provided {@link SuiWindow} to the player.
	 * @param player Player to be shown the {@link SuiWindow}.
	 * @param suiWindow {@link SuiWindow} to be shown
	 */
	public void showSuiWindow(Player player, SuiWindow suiWindow) {
		new SuiWindowIntent(player, suiWindow, SuiWindowIntent.SuiWindowEvent.NEW).broadcast();
	}
}
