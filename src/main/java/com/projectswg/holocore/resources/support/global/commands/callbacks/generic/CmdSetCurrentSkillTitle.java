package com.projectswg.holocore.resources.support.global.commands.callbacks.generic;

import com.projectswg.holocore.intents.gameplay.player.badge.SetTitleIntent;
import com.projectswg.holocore.resources.support.global.commands.ICmdCallback;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;

public final class CmdSetCurrentSkillTitle implements ICmdCallback {
	
	@Override
	public void execute(Player player, SWGObject target, String args) {
		SetTitleIntent.broadcast(args, player.getPlayerObject());
	}
	
}
