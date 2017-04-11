/***********************************************************************************
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
package utilities;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.projectswg.common.debug.Log;


public class Scripts {

	private static final String SCRIPTS = "scripts/";
	private static final String EXTENSION = ".js";
	private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("nashorn");
	private static final Invocable INVOCABLE = (Invocable) ENGINE;
	
	static {
		ENGINE.put("intentFactory", new IntentFactory());
		try {
			ENGINE.eval("var RadialOption = Java.type('resources.radial.RadialOption')");
			ENGINE.eval("var RadialItem = Java.type('resources.radial.RadialItem')");
			ENGINE.eval("var Log = Java.type('resources.server_info.Log')");
			ENGINE.eval("var SuiWindow = Java.type('resources.sui.SuiWindow')");
			ENGINE.eval("var SuiButtons = Java.type('resources.sui.SuiButtons')");
			ENGINE.eval("var SuiEvent = Java.type('resources.sui.SuiEvent')");
		} catch (ScriptException e) {
			Log.e(e);
		}
	}
	
	// Prevents instantiation.
	private Scripts() {}
	
	/**
	 * @param script name of the script, relative to the scripts folder.
	 * @param function name of the specific function within the script.
	 * @param args to pass to the function.
	 * @return whatever the function returns. If the function doesn't have a return statement, this method returns {@code null}.
	 * If an exception occurs, {@code null} is returned.
	 * @throws java.io.FileNotFoundException if the script file wasn't found
	 */
	@SuppressWarnings("unchecked")
	public static <T> T invoke(String script, String function, Object... args) throws FileNotFoundException {
		try {
			ENGINE.eval(new InputStreamReader(new FileInputStream(SCRIPTS + script + EXTENSION), StandardCharsets.UTF_8));
			return (T) INVOCABLE.invokeFunction(function, args);
		} catch (ScriptException | NoSuchMethodException t) {
			Log.e("Error invoking script: " + script + "  with function: " + function);
			Log.e("    Args: " + Arrays.toString(args));
			Log.e(t);
			return null;
		}
	}
	
}
