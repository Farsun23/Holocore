/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
 * *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 * *
 * This file is part of Holocore.                                                  *
 * *
 * --------------------------------------------------------------------------------*
 * *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 * *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 * *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http:></http:>//www.gnu.org/licenses/>.               *
 */

package com.projectswg.holocore.resources.support.objects.swg.creature

import com.projectswg.common.data.location.Location
import com.projectswg.common.network.packets.SWGPacket
import com.projectswg.common.network.packets.swg.zone.*
import com.projectswg.common.network.packets.swg.zone.baselines.Baseline.BaselineType
import com.projectswg.common.network.packets.swg.zone.building.UpdateCellPermissionMessage
import com.projectswg.common.network.packets.swg.zone.object_controller.DataTransform
import com.projectswg.common.network.packets.swg.zone.object_controller.DataTransformWithParent
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import com.projectswg.holocore.resources.support.objects.swg.building.BuildingObject
import com.projectswg.holocore.resources.support.objects.swg.cell.CellObject

import java.util.*
import java.util.concurrent.atomic.AtomicReference

class CreatureObjectAwareness(private val creature: CreatureObject) {
	
	private val aware = HashSet<SWGObject>()
	private val awareIds = HashSet<Long>()
	private val finalTeleportPacket = AtomicReference<SWGPacket>(null)
	private val flushAwarenessData = FlushAwarenessData(creature)
	
	@Synchronized
	fun setTeleportDestination(parent: SWGObject?, location: Location) {
		if (parent == null)
			finalTeleportPacket.set(DataTransform(creature.objectId, 0, creature.nextUpdateCount, location, 0f))
		else
			finalTeleportPacket.set(DataTransformWithParent(creature.objectId, 0, creature.nextUpdateCount, parent.objectId, location, 0f))
	}
	
	@Synchronized
	fun flushNoPlayer() {
		handleFlush({}, {}, {}) // No action required for NPCs
	}
	
	@Synchronized
	fun flush(target: Player) {
		handleFlush(
			createHandler = { create ->
				// Send all SceneCreateObject's and Baselines
				val createStack = LinkedList<SWGObject>()
				for (obj in create) {
					if (obj.isBundledWithin(obj.parent, creature))
						popStackUntil(target, createStack, obj.parent)
					else
						popStackAll(target, createStack)
					createStack.add(obj)
					createObject(obj, target)
				}
				popStackAll(target, createStack)
			},
			intermediateCallback = {
				// Perform a teleport if necessary
				val finalTeleportPacket = this.finalTeleportPacket.getAndSet(null)
				if (finalTeleportPacket != null)
					creature.sendSelf(finalTeleportPacket)
			},
			destroyHandler = { destroy ->
				// Destroy the objects on the client
				for (obj in destroy) {
					destroyObject(obj, target)
				}
			})
	}
	
	private inline fun handleFlush(createHandler: (List<SWGObject>) -> Unit, intermediateCallback: () -> Unit, destroyHandler: (List<SWGObject>) -> Unit) {
		val newAware = creature.aware
		handleFlushCreate(newAware, createHandler)
		intermediateCallback()
		handleFlushDestroy(newAware, destroyHandler)
	}
	
	private inline fun handleFlushCreate(newAware: Set<SWGObject>, createHandler: (List<SWGObject>) -> Unit) {
		val create = flushAwarenessData.buildCreate(aware, newAware)
		createHandler(create)
		
		for (add in create) { // Using "create" here because it's filtered to ensure no crashes
			aware.add(add)
			awareIds.add(add.objectId)
			add.addObserver(creature)
			creature.onObjectEnteredAware(add)
		}
		assert(!creature.isLoggedInPlayer || aware.contains(creature.getSlottedObject("ghost"))) { "not aware of ghost $creature" }
		assert(!creature.isLoggedInPlayer || aware.contains(creature)) { "not aware of creature" }
	}
	
	private inline fun handleFlushDestroy(newAware: Set<SWGObject>, destroyHandler: (List<SWGObject>) -> Unit) {
		val destroy = flushAwarenessData.buildDestroy(aware, newAware)
		// Remove destroyed objects immediately - before sending anything to the client
		val it = aware.iterator()
		while (it.hasNext()) {
			val currentAware = it.next()
			for (remove in destroy) { // Since the "create" is filtered, aware could also have been filtered
				if (isParent(currentAware, remove)) {
					it.remove()
					awareIds.remove(currentAware.objectId)
					remove.removeObserver(creature)
					creature.onObjectExitedAware(remove)
					break
				}
			}
		}
		destroyHandler(destroy)
		assert(!creature.isLoggedInPlayer || aware.contains(creature.getSlottedObject("ghost"))) { "not aware of ghost $creature" }
		assert(!creature.isLoggedInPlayer || aware.contains(creature)) { "not aware of creature" }
	}
	
	@Synchronized
	fun resetObjectsAware() {
		for (obj in aware) {
			obj.removeObserver(creature)
		}
		aware.clear()
		awareIds.clear()
	}
	
	@Synchronized fun isAware(objectId: Long) = awareIds.contains(objectId)
	@Synchronized fun isAware(obj: SWGObject) = aware.contains(obj)
	
	private fun createObject(obj: SWGObject, target: Player) {
		val id = obj.objectId
		
		// SceneCreateObjectByCrc
		val create = SceneCreateObjectByCrc()
		create.objectId = id
		create.location = obj.location
		create.objectCrc = obj.crc
		target.sendPacket(create)
		
		// Baselines
		val owner = obj.owner === target
		
		target.sendPacket(obj.createBaseline3(target))
		target.sendPacket(obj.createBaseline6(target))
		
		if (owner) {
			target.sendPacket(obj.createBaseline1(target))
			target.sendPacket(obj.createBaseline4(target))
			target.sendPacket(obj.createBaseline8(target))
			target.sendPacket(obj.createBaseline9(target))
		}
		
		// Miscellaneous
		if (obj is CellObject)
			target.sendPacket(UpdateCellPermissionMessage(1.toByte(), id))
		// ? UpdatePostureMessage for PlayerObject ?
		if (obj is CreatureObject && obj.isGenerated()) {
			target.sendPacket(UpdatePostureMessage(obj.posture.id.toInt(), id))
			target.sendPacket(UpdatePvpStatusMessage(obj.pvpFaction, id, this.creature.getPvpFlagsFor(obj)))
		}
		
		// UpdateContainmentMessage
		val parent = obj.parent
		if (parent != null)
			target.sendPacket(UpdateContainmentMessage(obj.objectId, parent.objectId, obj.slotArrangement))
	}
	
	private fun popStackAll(target: Player, createStack: LinkedList<SWGObject>) {
		var parent: SWGObject? = createStack.pollLast()
		while (parent != null) {
			target.sendPacket(SceneEndBaselines(parent.objectId))
			parent = createStack.pollLast()
		}
	}
	
	private fun popStackUntil(target: Player, createStack: LinkedList<SWGObject>, parent: SWGObject?) {
		var last: SWGObject? = createStack.peekLast()
		while (last != null) {
			if (last === parent)
				break
			createStack.pollLast()
			target.sendPacket(SceneEndBaselines(last.objectId))
			last = createStack.peekLast()
		}
	}
	
	private fun destroyObject(obj: SWGObject, target: Player) {
		target.sendPacket(SceneDestroyObject(obj.objectId))
	}
	
	class FlushAwarenessData(private val creature: CreatureObject) {
		
		private val objectComparator = Comparator.comparingInt<SWGObject> { getObjectDepth(it) }.thenComparingDouble { getDistance(creature, it) }
		private val create = ArrayList<SWGObject>()
		private val destroy = ArrayList<SWGObject>()
		
		fun buildCreate(oldAware: Set<SWGObject>, newAware: Set<SWGObject>): List<SWGObject> {
			val create = this.create
			create.clear()
			val added = newAware.filter { !oldAware.contains(it) }.sortedWith(compareBy({ getObjectDepth(it) }, { getDistance(creature, it) }))
			for (obj in added) {
				val parent = obj.parent
				if (parent != null && !oldAware.contains(parent) && !create.contains(parent)) {
					assert(obj !is CellObject)
					continue
				}
				assert(obj !is BuildingObject || added.containsAll(obj.getContainedObjects())) { "All cells must be sent with the building" }
				if (!obj.isBundledWithin(parent, creature) || oldAware.contains(parent)) {
					create.add(obj)
				} else {
					val parentIndex = create.indexOf(parent)
					assert(parentIndex != -1) { "parent isn't added along with child" }
					create.add(parentIndex + 1, obj)
				}
			}
			return create
		}
		
		fun buildDestroy(oldAware: Set<SWGObject>, newAware: Set<SWGObject>): List<SWGObject> {
			val destroy = this.destroy
			destroy.clear()
			oldAware.asSequence()
					.filter { !newAware.contains(it) }       // Removed from old to new awareness
					.filter { !isParent(creature, it) }      // Only remove if it isn't our parent
					.sortedWith(objectComparator)            // Sorted for proper ordering
					.forEach {
						if (!destroy.contains(it.parent))    // Optimization for the client - destroying the parent destroys the children
							destroy.add(it)
					}
			
			return destroy
		}
		
	}
	
	companion object {
		
		private fun getDistance(creature: CreatureObject, obj: SWGObject) = if (obj.parent != null) 0.0 else creature.worldLocation.distanceTo(obj.location)
		private fun getObjectDepth(obj: SWGObject?): Int = if (obj == null) 0 else (1 + getObjectDepth(obj.parent))
		private fun SWGObject.isBundledWithin(parent: SWGObject?, creature: CreatureObject) = (parent != null && (this.slotArrangement == -1 || this.baselineType == BaselineType.PLAY || parent === creature))
		
		private fun isParent(child: SWGObject, testParent: SWGObject): Boolean {
			var parent: SWGObject? = child
			while (parent != null) {
				if (parent === testParent)
					return true
				parent = parent.parent
			}
			return false
		}
		
	}
	
}
