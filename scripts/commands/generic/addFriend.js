var execute = function(objManager, player, target, args) {
	var ChatAvatarRequestIntent = Java.type("intents.chat.ChatAvatarRequestIntent");
	var RequestType = Java.type("intents.chat.ChatAvatarRequestIntent.RequestType");
	var ghost = player.getPlayerObject();
	var name;
	
	if(ghost == null || args == null) {
		return;
	}
	
	name = args.split(" ")[0].toLowerCase(java.util.Locale.English);
	
	if(name == null) {
		return;
	}
	
	new ChatAvatarRequestIntent(player, name, RequestType.FRIEND_ADD_TARGET).broadcast();
}