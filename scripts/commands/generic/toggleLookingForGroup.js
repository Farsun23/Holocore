function executeCommand(galacticManager, player, target, args) {
	var PlayerFlags = Java.type("resources.player.PlayerFlags");
	player.getPlayerObject().toggleFlag(PlayerFlags.LFG);
}