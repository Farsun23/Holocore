/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/
package com.projectswg.holocore.resources.support.objects.swg.custom;

import com.projectswg.common.data.location.Location;
import com.projectswg.holocore.intents.support.objects.swg.MoveObjectIntent;
import com.projectswg.holocore.resources.support.data.server_info.loader.NpcPatrolRouteLoader.PatrolType;
import com.projectswg.holocore.resources.support.npc.ai.AINavigationSupport;
import com.projectswg.holocore.resources.support.npc.spawn.Spawner.ResolvedPatrolWaypoint;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI object that patrols the specified route
 */
public class PatrolAIObject extends AIObject {
	
	private final List<ResolvedPatrolWaypoint> waypoints;
	private final AtomicReference<PatrolType> patrolType;
	private final AtomicInteger updateCounter;
	private final Queue<Runnable> plannedRoute;
	
	public PatrolAIObject(long objectId) {
		super(objectId);
		this.waypoints = new CopyOnWriteArrayList<>();
		this.patrolType = new AtomicReference<>(PatrolType.LOOP);
		this.updateCounter = new AtomicInteger(0);
		this.plannedRoute = new LinkedList<>();
	}
	
	public void setPatrolWaypoints(List<ResolvedPatrolWaypoint> waypoints) {
		if (!waypoints.isEmpty())
			this.patrolType.set(waypoints.get(0).getPatrolType());
		this.waypoints.clear();
		this.waypoints.addAll(waypoints);
	}
	
	@Override
	protected void aiInitialize() {
		setSchedulerProperties((int) (Math.random()*1000), 1000, TimeUnit.MILLISECONDS);
	}
	
	@Override
	protected void aiLoop() {
		if (plannedRoute.isEmpty()) {
			createPlannedRoute();
		}
		Runnable nextAction = plannedRoute.poll();
		if (nextAction != null)
			nextAction.run();
	}
	
	private void createPlannedRoute() {
		{ // Creates the full route
			Location prevLocation = getLocation();
			SWGObject prevParent = getParent();
			for (ResolvedPatrolWaypoint waypoint : waypoints) {
				appendPlannedRouteWaypoint(prevParent, prevLocation, waypoint);
				prevParent = waypoint.getParent();
				prevLocation = waypoint.getLocation();
			}
		}
		
		// Creates a route in reverse for flip patrol types
//		if (patrolType.get() == PatrolType.FLIP) {
//			List<Runnable> reversed = new ArrayList<>(plannedRoute);
//			Collections.reverse(reversed);
//			plannedRoute.addAll(reversed);
//		}
	}
	
	private void appendPlannedRouteWaypoint(SWGObject prevParent, Location prevLocation, ResolvedPatrolWaypoint waypoint) {
		if (prevParent == waypoint.getParent()) {
			Queue<Location> route = AINavigationSupport.navigateTo(prevLocation, waypoint.getLocation(), calculateWalkSpeed());
			while (!route.isEmpty()) {
				Location l = route.poll();
				assert l != null;
				addToPlannedRoute(prevParent, l);
			}
		} else {
			// Simple teleport to the location within/out of the cell
			addToPlannedRoute(waypoint.getParent(), waypoint.getLocation());
		}
		for (int i = 0; i < waypoint.getDelay(); i++) {
			addNopToPlannedRoute();
		}
	}
	
	private void addToPlannedRoute(SWGObject parent, Location location) {
		plannedRoute.add(() -> MoveObjectIntent.broadcast(this, parent, location, calculateWalkSpeed(), updateCounter.incrementAndGet()));
	}
	
	private void addNopToPlannedRoute() {
		plannedRoute.add(() -> {});
	}
	
}
