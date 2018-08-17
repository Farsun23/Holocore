/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of PSWGCommon.                                                *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * PSWGCommon is free software: you can redistribute it and/or modify              *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * PSWGCommon is distributed in the hope that it will be useful,                   *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with PSWGCommon.  If not, see <http://www.gnu.org/licenses/>.             *
 ***********************************************************************************/

package com.projectswg.holocore.services.gameplay.world.travel;

import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.location.Terrain;
import com.projectswg.holocore.intents.gameplay.world.travel.pet.*;
import com.projectswg.holocore.intents.support.objects.swg.MoveObjectIntent;
import com.projectswg.holocore.intents.support.objects.swg.ObjectCreatedIntent;
import com.projectswg.holocore.resources.support.objects.ObjectCreator;
import com.projectswg.holocore.resources.support.objects.awareness.AwarenessType;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject;
import com.projectswg.holocore.resources.support.objects.swg.tangible.OptionFlag;
import com.projectswg.holocore.runners.TestRunnerSimulatedWorld;
import com.projectswg.holocore.test_resources.GenericCreatureObject;
import me.joshlarson.jlcommon.log.Log;
import me.joshlarson.jlcommon.log.log_wrapper.ConsoleLogWrapper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.List;

@RunWith(JUnit4.class)
public class TestPlayerMountService extends TestRunnerSimulatedWorld {
	
	@Test
	public void testGenerate() {
		registerService(new PlayerMountService());
		
		CreatureObject creature = createCreature();
		SWGObject deed = ObjectCreator.createObjectFromTemplate(getUniqueId(), "object/tangible/deed/vehicle_deed/shared_speederbike_swoop_deed.iff");
		deed.systemMove(creature.getInventory());
		ObjectCreatedIntent.broadcast(creature);
		ObjectCreatedIntent.broadcast(deed);
		
		broadcastAndWait(new VehicleDeedGenerateIntent(creature.getOwner(), deed));
		
		// PCD [object/intangible/vehicle/shared_speederbike_swoop_pcd.iff] should be in the datapad
		Collection<SWGObject> datapadData = creature.getDatapad().getContainedObjects();
		Assert.assertEquals(1, datapadData.size());
		Assert.assertEquals("object/intangible/vehicle/shared_speederbike_swoop_pcd.iff", datapadData.iterator().next().getTemplate());
	}
	
	@Test
	public void testCall() {
		registerService(new PlayerMountService());
		
		CreatureObject creature = createCreature();
		ObjectCreatedIntent.broadcast(creature);
		
		SWGObject pcd = ObjectCreator.createObjectFromTemplate(getUniqueId(), "object/intangible/vehicle/shared_speederbike_swoop_pcd.iff");
		CreatureObject vehicle = (CreatureObject) ObjectCreator.createObjectFromTemplate(getUniqueId(), "object/mobile/vehicle/shared_speederbike_swoop.iff");
		ObjectCreatedIntent.broadcast(creature);
		ObjectCreatedIntent.broadcast(pcd);
		ObjectCreatedIntent.broadcast(vehicle);
		vehicle.systemMove(pcd);
		pcd.systemMove(creature.getDatapad());
		vehicle.addOptionFlags(OptionFlag.MOUNT);
		
		broadcastAndWait(new PetDeviceCallIntent(creature.getOwner(), pcd));
		assertCorrectDismount(creature, vehicle);
		
		// Vehicle [object/mobile/vehicle/shared_speederbike_swoop.iff] should now be in the world alongside the player, and aware of eachother
		Assert.assertEquals(creature.getLocation(), vehicle.getLocation());
	}
	
	@Test
	public void testMountDismount() {
		registerService(new PlayerMountService());
		
		CreatureObject friend = createCreature();
		friend.systemMove(null, Location.builder(friend.getLocation()).setPosition(110, 110, 110).build());
		ObjectCreatedIntent.broadcast(friend);
		
		CreatureObject creature = createCreature();
		ObjectCreatedIntent.broadcast(creature);
		// Make the deed 
		SWGObject deed = ObjectCreator.createObjectFromTemplate(getUniqueId(), "object/tangible/deed/vehicle_deed/shared_speederbike_swoop_deed.iff");
		broadcastAndWait(new VehicleDeedGenerateIntent(creature.getOwner(), deed));
		SWGObject pcd = creature.getAware().stream().filter(obj -> obj.getTemplate().equals("object/intangible/vehicle/shared_speederbike_swoop_pcd.iff")).findFirst().orElseThrow();
		CreatureObject vehicle = (CreatureObject) creature.getAware().stream().filter(obj -> obj.getTemplate().equals("object/mobile/vehicle/shared_speederbike_swoop.iff")).findFirst().orElseThrow();
		
		assertCorrectDismount(creature, vehicle, friend);
		
		broadcastAndWait(new PetDeviceStoreIntent(creature.getOwner(), pcd));
		assertCorrectStored(creature, vehicle, friend);
		
		broadcastAndWait(new PetDeviceCallIntent(creature.getOwner(), pcd));
		assertCorrectDismount(creature, vehicle, friend);
		
		broadcastAndWait(new MountIntent(creature.getOwner(), vehicle));
		assertCorrectMount(creature, vehicle, friend);
		
		broadcastAndWait(new MoveObjectIntent(creature, creature.getLocation(), 7.3, creature.getNextUpdateCount()));
		assertCorrectMount(creature, vehicle, friend);
		
		broadcastAndWait(new DismountIntent(creature.getOwner(), vehicle));
		assertCorrectDismount(creature, vehicle, friend);
	}
	
	@BeforeClass
	public static void enableLogging() {
		Log.addWrapper(new ConsoleLogWrapper());
	}
	
	private static CreatureObject createCreature() {
		CreatureObject creature = new GenericCreatureObject(getUniqueId());
		creature.setLocation(Location.builder().setTerrain(Terrain.TATOOINE).setPosition(100, 100, 100).build());
		return creature;
	}
	
	private void assertCorrectStored(CreatureObject creature, CreatureObject vehicle, SWGObject ... awareness) {
		Assert.assertNull(creature.getParent());
		Assert.assertEquals(creature, vehicle.getSuperParent());
		
		Assert.assertTrue(creature.getAware(AwarenessType.OBJECT).containsAll(List.of(awareness)));
		Assert.assertFalse(vehicle.getAware(AwarenessType.OBJECT).containsAll(List.of(awareness)));
		Assert.assertTrue(creature.getAware(AwarenessType.SELF).contains(vehicle));
		Assert.assertTrue(vehicle.getAware(AwarenessType.SELF).contains(creature));
	}
	
	private void assertCorrectMount(CreatureObject creature, CreatureObject vehicle, SWGObject ... awareness) {
		Assert.assertEquals(vehicle, creature.getParent());
		Assert.assertNull(vehicle.getParent());
		Assert.assertTrue(creature.isExposeWithWorld());
		Assert.assertTrue(creature.isObserveWithParent());
		
		Assert.assertTrue(creature.getAware(AwarenessType.OBJECT).containsAll(List.of(awareness)));
		Assert.assertTrue(vehicle.getAware(AwarenessType.OBJECT).containsAll(List.of(awareness)));
		Assert.assertTrue(creature.getAware(AwarenessType.SELF).contains(vehicle));
		Assert.assertTrue(vehicle.getAware(AwarenessType.SELF).contains(creature));
	}
	
	private void assertCorrectDismount(CreatureObject creature, CreatureObject vehicle, SWGObject ... awareness) {
		Assert.assertNull(creature.getParent());
		Assert.assertNull(vehicle.getParent());
		Assert.assertFalse(creature.isExposeWithWorld());
		Assert.assertTrue(creature.isObserveWithParent());
		
		Assert.assertTrue(creature.getAware(AwarenessType.OBJECT).containsAll(List.of(awareness)));
		Assert.assertTrue(vehicle.getAware(AwarenessType.OBJECT).containsAll(List.of(awareness)));
		Assert.assertFalse(creature.getAware(AwarenessType.SELF).contains(vehicle));
		Assert.assertFalse(vehicle.getAware(AwarenessType.SELF).contains(creature));
	}
	
}
