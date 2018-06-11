package com.projectswg.holocore.resources.support.global.commands.callbacks.chat.friend;

import com.projectswg.holocore.intents.support.global.chat.ChatAvatarRequestIntent;
import com.projectswg.holocore.resources.support.global.commands.ICmdCallback;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;

import java.util.Locale;

public final class CmdAddIgnore implements ICmdCallback {
	
	@Override
	public void execute(Player player, SWGObject target, String args) {
		if (args == null)
			return;
		
		String name = args.toLowerCase(Locale.ENGLISH);
		
		new ChatAvatarRequestIntent(player, name, ChatAvatarRequestIntent.RequestType.IGNORE_ADD_TARGET).broadcast();
	}
	
}
