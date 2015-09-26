package services.galaxy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import network.packets.Packet;
import network.packets.swg.zone.EnterTicketPurchaseModeMessage;
import network.packets.swg.zone.PlanetTravelPointListRequest;
import network.packets.swg.zone.PlanetTravelPointListResponse;
import intents.chat.ChatBroadcastIntent;
import intents.network.GalacticPacketIntent;
import intents.object.ObjectTeleportIntent;
import intents.travel.*;
import resources.Location;
import resources.Terrain;
import resources.TravelPoint;
import resources.client_info.ClientFactory;
import resources.client_info.visitors.DatatableData;
import resources.control.Intent;
import resources.control.Service;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.player.Player;
import resources.server_info.RelationalServerData;
import resources.sui.ISuiCallback;
import resources.sui.SuiButtons;
import resources.sui.SuiEvent;
import resources.sui.SuiListBox;
import resources.sui.SuiListBox.SuiListBoxItem;
import resources.sui.SuiMessageBox;
import services.objects.ObjectManager;

public final class TravelService extends Service {
	
	private static final String DBTABLENAME = "travel";
	private static final String TRAVELPOINTSFORPLANET = "SELECT name, x, y, z FROM " + DBTABLENAME + " WHERE planet=";
	private static final byte PLANETNAMESCOLUMNINDEX = 0;
	private static final short TICKETUSERADIUS = 50;	// The distance a player needs to be within in order to use their ticket
	
	private final ObjectManager objectManager;
	private final RelationalServerData travelPointDatabase;
	private final Map<Terrain, Collection<TravelPoint>> travelPoints;
	
	public TravelService(ObjectManager objectManager) {
		this.objectManager = objectManager;
		
		travelPointDatabase = new RelationalServerData("serverdata/static/travel.db");
		travelPoints = new HashMap<>();
		
		if(!travelPointDatabase.linkTableWithSdb(DBTABLENAME, "serverdata/static/travel.sdb")) {
			throw new main.ProjectSWG.CoreException("Unable to load sdb files for TravelService");
		}
	}
	
	@Override
	public boolean initialize() {
		registerForIntent(TravelPointSelectionIntent.TYPE);
		registerForIntent(GalacticPacketIntent.TYPE);
		registerForIntent(TicketPurchaseIntent.TYPE);
		registerForIntent(TicketUseIntent.TYPE);
		
		return super.initialize() && loadTravelPoints();
	}
	
	@Override
	public boolean terminate() {
		return super.terminate() && travelPointDatabase.close();
	}
	
	@Override
	public void onIntentReceived(Intent i) {
		switch(i.getType()) {
			case TravelPointSelectionIntent.TYPE:	handlePointSelection((TravelPointSelectionIntent) i); break;
			case GalacticPacketIntent.TYPE:			handleTravelPointRequest((GalacticPacketIntent) i); break;
			case TicketPurchaseIntent.TYPE:			handleTicketPurchase((TicketPurchaseIntent) i); break;
			case TicketUseIntent.TYPE:				handleTicketUse((TicketUseIntent) i); break;
		}
	}
	
	/**
	 * Travel points are loaded from /serverdata/static/travel.sdb
	 * A travel point represents a travel destination.
	 * @author Mads
	 * @return true if all points were loaded succesfully and false if not.
	 */
	private boolean loadTravelPoints() {
		boolean success = true;
		
		DatatableData travelFeeTable = (DatatableData) ClientFactory.getInfoFromFile("datatables/travel/travel.iff");
		Map<String, Integer> planetFees;
		
		// Load the planet names
		String[] planetNames = new String[travelFeeTable.getRowCount()];
		
		travelFeeTable.handleRows(currentRow -> planetNames[currentRow] = (String) travelFeeTable.getCell(currentRow, PLANETNAMESCOLUMNINDEX));
		
		for(int i = 0; i < travelFeeTable.getRowCount(); i++) {
			String planetName = planetNames[i];
			
			
			planetFees = new HashMap<>();
				
			for(int j = 0; j < travelFeeTable.getRowCount(); j++) {
				int price = (int) travelFeeTable.getCell(j, travelFeeTable.getColumnFromName(planetName));
				
				if(price > 0)
					planetFees.put((String) planetNames[j], price);
			}
				
			Terrain terrain = Terrain.getTerrainFromName(planetName);
			
			String query = TRAVELPOINTSFORPLANET + "'" + planetName + "'";
			
			for(String availablePlanet : planetFees.keySet())
				if(!availablePlanet.equals(planetName))
					query += " OR planet='" + availablePlanet + "'";
			
			try(ResultSet travelPointTable = travelPointDatabase.prepareStatement(query).executeQuery()) {
				while(travelPointTable.next()) {
					Location loc = new Location(travelPointTable.getDouble("x"), travelPointTable.getDouble("y"), travelPointTable.getDouble("z"), terrain);
					TravelPoint tp = new TravelPoint(travelPointTable.getString("name"), loc, planetFees, 0);
					Collection<TravelPoint> travelPointsForPlanet = travelPoints.get(terrain);
					
					if(travelPointsForPlanet == null) {
						travelPointsForPlanet = new ArrayList<>();
						
						travelPoints.put(terrain, travelPointsForPlanet);
					}
					
					travelPointsForPlanet.add(tp);
				}
			} catch(SQLException e) {
				e.printStackTrace();
				success = false;
			}
		}
		
		return success;
	}
	
	private void handlePointSelection(TravelPointSelectionIntent tpsi) {
		CreatureObject traveler = tpsi.getCreature();
		
		traveler.sendSelf(new EnterTicketPurchaseModeMessage(traveler.getTerrain().getName(), nearestTravelPoint(traveler.getWorldLocation()).getName(), tpsi.isInstant()));
	}
	
	private void handleTravelPointRequest(GalacticPacketIntent i) {
		Packet p = i.getPacket();
		
		if(p instanceof PlanetTravelPointListRequest ) {
			PlanetTravelPointListRequest req = (PlanetTravelPointListRequest) p;
			
			i.getPlayerManager().getPlayerFromNetworkId(i.getNetworkId()).sendPacket(new PlanetTravelPointListResponse(req.getPlanetName(), travelPoints.get(Terrain.getTerrainFromName(req.getPlanetName()))));
		}
	}
	
	private void handleTicketPurchase(TicketPurchaseIntent i) {
		CreatureObject purchaser = i.getPurchaser();
		Location purchaserWorldLocation = purchaser.getWorldLocation();
		TravelPoint nearestPoint = nearestTravelPoint(purchaserWorldLocation);
		TravelPoint destinationPoint = destinationPoint(purchaserWorldLocation.getTerrain(), i.getDestinationName());
		String suiMessage = "@travel:";
		Player purchaserOwner = purchaser.getOwner();
		boolean roundTrip = i.isRoundTrip();
		
		if(nearestPoint == null || destinationPoint == null)
			return;
		
		int purchaserBankBalance = purchaser.getBankBalance();
		int ticketPrice = nearestPoint.totalTicketPrice(i.getDestinationPlanet());
		
		if(roundTrip)
			ticketPrice += destinationPoint.totalTicketPrice(nearestPoint.getLocation().getTerrain().getName());
			
		if(ticketPrice > purchaserBankBalance) {
			// Make the message in the SUI window reflect the fail
			suiMessage += "short_funds";
		} else {
			// Make the message in the SUI window reflect the success
			suiMessage += "ticket_purchase_complete";
			
			// Also send the purchaser a system message
			// TODO is there a STF containing this?
			new ChatBroadcastIntent(purchaserOwner, String.format("You succesfully make a payment of %d credits to the Galactic Travel Commission.", ticketPrice)).broadcast();
			
			purchaser.setBankBalance(purchaserBankBalance - ticketPrice);
			
			// Create the ticket object
			SWGObject ticket = objectManager.createObject("object/tangible/travel/travel_ticket/base/shared_base_travel_ticket.iff", false);
			
			// Departure attributes
			ticket.addAttribute("@obj_attr_n:travel_departure_planet", "@planet_n:" + purchaserWorldLocation.getTerrain().getName());
			ticket.addAttribute("@obj_attr_n:travel_departure_point", nearestPoint.getName());
			
			// Arrival attributes
			ticket.addAttribute("@obj_attr_n:travel_arrival_planet", "@planet_n:" + destinationPoint.getLocation().getTerrain().getName());
			ticket.addAttribute("@obj_attr_n:travel_arrival_point", destinationPoint.getName());
			
			// Put the ticket in their inventory
			purchaser.getSlottedObject("inventory").addObject(ticket);
			
			if(roundTrip) {
				// Create the return ticket object
				SWGObject returnTicket = objectManager.createObject("object/tangible/travel/travel_ticket/base/shared_base_travel_ticket.iff", false);
				
				// Departure attributes
				returnTicket.addAttribute("@obj_attr_n:travel_departure_planet", "@planet_n:" + destinationPoint.getLocation().getTerrain().getName());
				returnTicket.addAttribute("@obj_attr_n:travel_departure_point", destinationPoint.getName());
				
				// Arrival attributes
				returnTicket.addAttribute("@obj_attr_n:travel_arrival_planet", "@planet_n:" + purchaserWorldLocation.getTerrain().getName());
				returnTicket.addAttribute("@obj_attr_n:travel_arrival_point", nearestPoint.getName());
				
				// Put the ticket in their inventory
				purchaser.getSlottedObject("inventory").addObject(returnTicket);
			}
		}
		
		// Create the SUI window
		SuiMessageBox messageBox = new SuiMessageBox(SuiButtons.OK, "STAR WARS GALAXIES", suiMessage);
		
		// Display the window to the purchaser
		messageBox.display(purchaserOwner);
	}
	
	private void handleTicketUse(TicketUseIntent i) {
		if(i.getTicket() == null)
			handleTicketUseSui(i);
		else
			handleTicketUseClick(i);
	}
	
	private void handleTicketUseSui(TicketUseIntent i) {
		Player player = i.getPlayer();
		CreatureObject creature = player.getCreatureObject();
		Collection<SWGObject> tickets = creature.getItemsByTemplate("inventory", "object/tangible/travel/travel_ticket/base/shared_base_travel_ticket.iff");
		SuiListBox destinationSelection;
		List<SWGObject> usableTickets = new ArrayList<>();
		
		for(SWGObject ticket : tickets)
			if(objectHasTicketAttributes(ticket))
				if(ticketCanBeUsedAtNearestPoint(ticket))
					usableTickets.add(ticket);
		
		if(usableTickets.isEmpty())	// They don't have a valid ticket. 
			new ChatBroadcastIntent(player, "@travel:no_ticket_for_shuttle").broadcast();
		else {
			destinationSelection = new SuiListBox(SuiButtons.OK_CANCEL, "@travel:select_destination", "@travel:select_destination");
			
			for(SWGObject usableTicket : usableTickets) {
				TravelPoint destinationPoint = destinationPoint(usableTicket);
				
				destinationSelection.addListItem(destinationPoint.toString(), destinationPoint);
			}
			
			destinationSelection.addOkButtonCallback("handleSelectedItem", new DestinationSelectionSuiCallback(destinationSelection, usableTickets));
			destinationSelection.display(player);
		}
	}
	
	private void handleTicketUseClick(TicketUseIntent i) {
		CreatureObject traveler = i.getPlayer().getCreatureObject();
		Location worldLoc = traveler.getWorldLocation();
		TravelPoint nearestPoint = nearestTravelPoint(worldLoc);
		double distanceToNearestPoint = worldLoc.distanceTo(nearestPoint.getLocation());
		SWGObject ticket = i.getTicket();
		Player player = i.getPlayer();
		
		if(objectHasTicketAttributes(ticket)) {
			if(ticketCanBeUsedAtNearestPoint(ticket)) {
				if(distanceToNearestPoint <= TICKETUSERADIUS) {
					// They can use their ticket if they're within range.
					teleportAndDestroyTicket(destinationPoint(ticket), ticket, traveler);
				} else {
					// They're out of range - let them know.
					new ChatBroadcastIntent(player, "@travel:boarding_too_far").broadcast();
				}
			} else {
				// This ticket isn't valid for this point
				new ChatBroadcastIntent(player, "@travel:wrong_shuttle").broadcast();
			}
		}
	}
	
	private void teleportAndDestroyTicket(TravelPoint destination, SWGObject ticket, CreatureObject traveler) {
		objectManager.destroyObject(ticket);
		
		new ObjectTeleportIntent(traveler, destination.getLocation()).broadcast();
	}
	
	private boolean objectHasTicketAttributes(SWGObject object) {
		String departurePlanet = object.getAttribute("@obj_attr_n:travel_departure_planet");
		String departureDestination = object.getAttribute("@obj_attr_n:travel_departure_point");
		String arrivalPlanet = object.getAttribute("@obj_attr_n:travel_arrival_planet");
		String arrivalPoint = object.getAttribute("@obj_attr_n:travel_arrival_point");
		
		return departurePlanet != null && departureDestination != null && arrivalPlanet != null && arrivalPoint != null;
	}
	
	private boolean ticketCanBeUsedAtNearestPoint(SWGObject ticket) {
		CreatureObject ticketOwner = ticket.getOwner().getCreatureObject();
		Location worldLoc = ticketOwner.getWorldLocation();
		TravelPoint nearest = nearestTravelPoint(worldLoc);
		String departurePoint = ticket.getAttribute("@obj_attr_n:travel_departure_point");
		String departurePlanet = ticket.getAttribute("@obj_attr_n:travel_departure_planet");
		Terrain departureTerrain = Terrain.getTerrainFromName(departurePlanet.split(":")[1]);
		Terrain currentTerrain = worldLoc.getTerrain();
		
		return departureTerrain == currentTerrain && departurePoint.equals(nearest.getName());
	}
	
	private TravelPoint destinationPoint(SWGObject ticket) {
		return destinationPoint(Terrain.getTerrainFromName(ticket.getAttribute("@obj_attr_n:travel_arrival_planet").split(":")[1]), ticket.getAttribute("@obj_attr_n:travel_arrival_point"));
	}
	
	private TravelPoint destinationPoint(Terrain terrain, String pointName) {
		TravelPoint currentResult = null;
		Iterator<TravelPoint> pointIterator = travelPoints.get(terrain).iterator();
		
		while(pointIterator.hasNext()) {
			TravelPoint candidate = pointIterator.next();
			
			if(candidate.getName().equals(pointName)) {
				currentResult = candidate;
				break;
			}
		}
		
		return currentResult;
	}
	
	private TravelPoint nearestTravelPoint(Location objectLocation) {
		TravelPoint currentResult = null;
		double currentResultDistance = Double.MAX_VALUE;
		double candidateDistance;
		Collection<TravelPoint> pointsForPlanet = travelPoints.get(objectLocation.getTerrain());
		
		for(TravelPoint candidate : pointsForPlanet) {
			
			if(currentResult == null) { // Will occur upon the first iteration.
				currentResult = candidate; // The first candidate will always be the first possible result.
			} else {
				candidateDistance = candidate.getLocation().distanceTo(objectLocation);
				
				if(candidateDistance < currentResultDistance) {
					currentResult = candidate;
					currentResultDistance = candidateDistance;
				}
			}
		}
		return currentResult;
	}

	private class DestinationSelectionSuiCallback implements ISuiCallback {

		private final SuiListBox destinationSelection;
		private final List<SWGObject> usableTickets;
		
		private DestinationSelectionSuiCallback(SuiListBox destinationSelection, List<SWGObject> usableTickets) {
			this.destinationSelection = destinationSelection;
			this.usableTickets = usableTickets;
		}
		
		@Override
		public void handleEvent(Player player, SWGObject actor, SuiEvent event,
				Map<String, String> parameters) {
			int selection = SuiListBox.getSelectedRow(parameters);
			SuiListBoxItem selectedItem = destinationSelection.getListItem(selection);
			TravelPoint selectedDestination = (TravelPoint) selectedItem.getObject();
			
			teleportAndDestroyTicket(selectedDestination, usableTickets.get(selection), player.getCreatureObject());
		}
	}
}
