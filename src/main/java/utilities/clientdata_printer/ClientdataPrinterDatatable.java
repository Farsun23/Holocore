/************************************************************************************
 * Copyright (c) 2015 /// Project SWG /// www.projectswg.com                        *
 *                                                                                  *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on           *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies.  *
 * Our goal is to create an emulator which will provide a server for players to     *
 * continue playing a game similar to the one they used to play. We are basing      *
 * it on the final publish of the game prior to end-game events.                    *
 *                                                                                  *
 * This file is part of Holocore.                                                   *
 *                                                                                  *
 * -------------------------------------------------------------------------------- *
 *                                                                                  *
 * Holocore is free software: you can redistribute it and/or modify                 *
 * it under the terms of the GNU Affero General Public License as                   *
 * published by the Free Software Foundation, either version 3 of the               *
 * License, or (at your option) any later version.                                  *
 *                                                                                  *
 * Holocore is distributed in the hope that it will be useful,                      *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                   *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                    *
 * GNU Affero General Public License for more details.                              *
 *                                                                                  *
 * You should have received a copy of the GNU Affero General Public License         *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.                *
 *                                                                                  *
 ***********************************************************************************/
package utilities.clientdata_printer;

import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.common.data.swgfile.visitors.DatatableData;
import com.projectswg.common.debug.Log;
import com.projectswg.common.debug.Log.LogLevel;
import com.projectswg.common.debug.log_wrapper.ConsoleLogWrapper;

public class ClientdataPrinterDatatable {
	
	public static void main(String [] args) {
		Log.addWrapper(new ConsoleLogWrapper(LogLevel.VERBOSE));
		printTable("datatables/buildout/areas_tatooine.iff");
	}
	
	private static void printTable(String table) {
		DatatableData data = (DatatableData) ClientFactory.getInfoFromFile(table);
		for (int col = 0; col < data.getColumnCount(); col++) {
			System.out.print(data.getColumnName(col) + ",");
		}
		System.out.println();
		for (int row = 0; row < data.getRowCount(); row++) {
			for (int col = 0; col < data.getColumnCount(); col++) {
				System.out.print(data.getCell(row, col) + ",");
			}
			System.out.println();
		}
	}
	
}
