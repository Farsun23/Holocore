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
package resources.server_info;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicReference;

public class Log {
	
	private static final DateFormat LOG_FORMAT = new SimpleDateFormat("dd-MM-yy HH:mm:ss.SSS");
	private static final Log LOG = new Log("log.txt", LogLevel.VERBOSE);
	
	private final File file;
	private final AtomicReference<LogLevel> level;
	private BufferedWriter writer;
	private boolean open;
	
	private Log(String filename, LogLevel level) {
		this.file = new File(filename);
		this.level = new AtomicReference<>();
		open = false;
	}
	
	private synchronized void open() throws IOException {
		if (!open)
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
		open = true;
	}
	
	private synchronized void close() throws IOException {
		if (open)
			writer.close();
		open = false;
	}
	
	private synchronized void write(String str) throws IOException {
		if (open) {
			writer.write(str);
			writer.newLine();
			writer.flush();
		}
	}
	
	private synchronized void setLevel(LogLevel level) {
		this.level.set(level);
	}
	
	private synchronized LogLevel getLevel() {
		return level.get();
	}
	
	private synchronized void logRaw(LogLevel level, String logStr) {
		if (level.compareTo(LogLevel.WARN) >= 0)
			System.err.println(logStr);
		else
			System.out.println(logStr);
		try {
			write(logStr);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected static final void start() {
		try {
			LOG.open();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected static final void stop() {
		try {
			LOG.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Sets the minimum level for logs to be reported. This is according to the
	 * following order: VERBOSE, DEBUG, INFO, WARNING, ERROR, then ASSERT.
	 * @param level the minimum log level, inclusively. Default is VERBOSE
	 */
	public static final void setLogLevel(LogLevel level) {
		synchronized (LOG) {
			LOG.setLevel(level);
		}
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity, time and message.
	 * @param level the log level of this message between VERBOSE and ASSERT
	 * @param tag the tag to use for the log
	 * @param str the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void log(LogLevel level, String str, Object ... args) {
		if (LOG.getLevel().compareTo(level) > 0)
			return;
		String date;
		synchronized (LOG_FORMAT) {
			date = LOG_FORMAT.format(System.currentTimeMillis());
		}
		if (args.length == 0)
			LOG.logRaw(level, date + ' ' + level.getChar() + ": " + str);
		else
			LOG.logRaw(level, date + ' ' + level.getChar() + ": " + String.format(str, args));
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as VERBOSE, as well as the time and message.
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void v(String message, Object ... args) {
		log(LogLevel.VERBOSE, message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as DEBUG, as well as the time and message.
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void d(String message, Object ... args) {
		log(LogLevel.DEBUG, message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as INFO, as well as the time and message.
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void i(String message, Object ... args) {
		log(LogLevel.INFO, message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as WARN, as well as the time and message.
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void w(String message, Object ... args) {
		log(LogLevel.WARN, message, args);
	}
	
	
	/**
	 * Logs the exception to the server log file, formatted to display the log
	 * severity as WARN, as well as the time, and tag.
	 * @param exception the exception to print
	 */
	public static final void w(Throwable exception) {
		printException(LogLevel.WARN, exception);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as ERROR, as well as the time and message.
	 * @param tag the tag to use for the log
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void e(String message, Object ... args) {
		log(LogLevel.ERROR, message, args);
	}
	
	/**
	 * Logs the exception to the server log file, formatted to display the log
	 * severity as ERROR, as well as the time, and tag.
	 * @param exception the exception to print
	 */
	public static final void e(Throwable exception) {
		printException(LogLevel.ERROR, exception);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as ASSERT, as well as the time and message.
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void a(String message, Object ... args) {
		log(LogLevel.ASSERT, message, args);
	}
	
	/**
	 * Logs the exception to the server log file, formatted to display the log
	 * severity as ASSERT, as well as the time, and tag.
	 * @param exception the exception to print
	 */
	public static final void a(Throwable exception) {
		printException(LogLevel.ASSERT, exception);
	}
	
	private static final void printException(LogLevel level, Throwable exception) {
		String header1 = String.format("Exception in thread \"%s\" %s: %s", Thread.currentThread().getName(), exception.getClass().getName(), exception.getMessage());
		String header2 = String.format("Caused by: %s: %s", exception.getClass().getCanonicalName(), exception.getMessage());
		StackTraceElement [] elements = exception.getStackTrace();
		log(level, header1);
		log(level, header2);
		for (StackTraceElement e : elements) {
			log(level, "    " + e.toString());
		}
	}
	
	public static enum LogLevel {
		VERBOSE	('V'),
		DEBUG	('D'),
		INFO	('I'),
		WARN	('W'),
		ERROR	('E'),
		ASSERT	('A');
		
		private char c;
		
		LogLevel(char c) {
			this.c = c;
		}
		
		public char getChar() { return c; }
	}
	
}
