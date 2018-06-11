package com.projectswg.holocore.resources.support.global.commands.callbacks.generic;

import com.projectswg.holocore.resources.support.global.commands.ICmdCallback;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.waypoint.WaypointObject;

public final class CmdSetWaypointName implements ICmdCallback {
	
	@Override
	public void execute(Player player, SWGObject target, String args) {
		if (player.getPlayerObject() == null || target == null) {
			return;
		}
		
		WaypointObject waypoint = player.getPlayerObject().getWaypoint(target.getObjectId());
		
		if (waypoint == null) {
			return;
		}
		
		waypoint.setName(args);
		
		player.getPlayerObject().updateWaypoint(waypoint);
	}
	
}
