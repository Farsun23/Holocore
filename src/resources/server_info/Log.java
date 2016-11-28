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

public class Log {
	
	private static final DateFormat LOG_FORMAT = new SimpleDateFormat("dd-MM-yy HH:mm:ss.SSS");
	private static final Log LOG = new Log("log.txt", LogLevel.VERBOSE);
	
	private final File file;
	private BufferedWriter writer;
	private LogLevel level;
	private boolean open;
	
	private Log(String filename, LogLevel level) {
		this.file = new File(filename);
		this.level = level;
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
		this.level = level;
	}
	
	private synchronized LogLevel getLevel() {
		return level;
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
	 * severity, time, tag and message.
	 * @param level the log level of this message between VERBOSE and ASSERT
	 * @param tag the tag to use for the log
	 * @param str the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void log(LogLevel level, String tag, String str, Object ... args) {
		synchronized (LOG) {
			if (LOG.getLevel().compareTo(level) > 0)
				return;
		}
		String date;
		synchronized (LOG_FORMAT) {
			date = LOG_FORMAT.format(System.currentTimeMillis());
		}
		String logStr = String.format(str, args);
		String log = String.format("%s %c/[%s]: %s", date, level.getChar(), tag, logStr);
		if (level.compareTo(LogLevel.WARN) >= 0)
			System.err.println(date + " " + level.getChar() + ": " + logStr);
		else
			System.out.println(date + " " + level.getChar() + ": " + logStr);
		synchronized (LOG) {
			try {
				LOG.write(log);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as VERBOSE, as well as the time, tag and message.
	 * @param tag the tag to use for the log
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void v(String tag, String message, Object ... args) {
		log(LogLevel.VERBOSE, tag, message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as VERBOSE, as well as the time, class name and message.
	 * @param tag the object outputting this log info
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void v(Object tag, String message, Object ... args) {
		log(LogLevel.VERBOSE, tag.getClass().getSimpleName(), message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as DEBUG, as well as the time, tag and message.
	 * @param tag the tag to use for the log
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void d(String tag, String message, Object ... args) {
		log(LogLevel.DEBUG, tag, message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as DEBUG, as well as the time, tag and message.
	 * @param tag the object outputting this log info
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void d(Object tag, String message, Object ... args) {
		log(LogLevel.DEBUG, tag.getClass().getSimpleName(), message, args);
	}
	
	/**
	 * Logs a stack trace to the server log file, formatted to display the log
	 * severity as DEBUG as well as a custom tag
	 * @param tag the object requesting this stack trace
	 */
	public static final void printStackTrace(Object tag) {
		printStackTrace(tag.getClass().getSimpleName());
	}
	
	/**
	 * Logs a stack trace to the server log file, formatted to display the log
	 * severity as DEBUG as well as a custom tag
	 * @param tag the tag to use for the log
	 */
	public static final void printStackTrace(String tag) {
		log(LogLevel.DEBUG, tag, "Stack Trace");
		StackTraceElement [] elements = Thread.currentThread().getStackTrace();
		for (int i = 2; i < elements.length; i++) {
			log(LogLevel.DEBUG, tag, "    " + elements[i].toString());
		}
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as INFO, as well as the time, tag and message.
	 * @param tag the tag to use for the log
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void i(String tag, String message, Object ... args) {
		log(LogLevel.INFO, tag, message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as INFO, as well as the time, class name and message.
	 * @param tag the object outputting this log info
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void i(Object tag, String message, Object ... args) {
		log(LogLevel.INFO, tag.getClass().getSimpleName(), message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as WARN, as well as the time, tag and message.
	 * @param tag the tag to use for the log
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void w(String tag, String message, Object ... args) {
		log(LogLevel.WARN, tag, message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as WARN, as well as the time, tag and message.
	 * @param tag the object outputting this log info
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void w(Object tag, String message, Object ... args) {
		log(LogLevel.WARN, tag.getClass().getSimpleName(), message, args);
	}
	
	/**
	 * Logs the exception to the server log file, formatted to display the log
	 * severity as WARN, as well as the time, and tag.
	 * @param tag the tag to use for the log
	 * @param exception the exception to print
	 */
	public static final void w(String tag, Throwable exception) {
		printException(LogLevel.WARN, tag, exception);
	}
	
	/**
	 * Logs the exception to the server log file, formatted to display the log
	 * severity as WARN, as well as the time, and tag.
	 * @param tag the object outputting this log info
	 * @param exception the exception to print
	 */
	public static final void w(Object tag, Throwable exception) {
		printException(LogLevel.WARN, tag.getClass().getSimpleName(), exception);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as ERROR, as well as the time, tag and message.
	 * @param tag the tag to use for the log
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void e(String tag, String message, Object ... args) {
		log(LogLevel.ERROR, tag, message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as ERROR, as well as the time, tag and message.
	 * @param tag the object outputting this log info
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void e(Object tag, String message, Object ... args) {
		log(LogLevel.ERROR, tag.getClass().getSimpleName(), message, args);
	}
	
	/**
	 * Logs the exception to the server log file, formatted to display the log
	 * severity as ERROR, as well as the time, and tag.
	 * @param tag the tag to use for the log
	 * @param exception the exception to print
	 */
	public static final void e(String tag, Throwable exception) {
		printException(LogLevel.ERROR, tag, exception);
	}
	
	/**
	 * Logs the exception to the server log file, formatted to display the log
	 * severity as ERROR, as well as the time, and tag.
	 * @param tag the object outputting this log info
	 * @param exception the exception to print
	 */
	public static final void e(Object tag, Throwable exception) {
		printException(LogLevel.ERROR, tag.getClass().getSimpleName(), exception);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as ASSERT, as well as the time, tag and message.
	 * @param tag the tag to use for the log
	 * @param message the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void a(String tag, String message, Object ... args) {
		log(LogLevel.ASSERT, tag, message, args);
	}
	
	/**
	 * Logs the string to the server log file, formatted to display the log
	 * severity as ASSERT, as well as the time, tag and message.
	 * @param tag the object outputting this log info
	 * @param str the format string for the log
	 * @param args the string format arguments, if specified
	 */
	public static final void a(Object tag, String message, Object ... args) {
		log(LogLevel.ASSERT, tag.getClass().getSimpleName(), message, args);
	}
	
	/**
	 * Logs the exception to the server log file, formatted to display the log
	 * severity as ASSERT, as well as the time, and tag.
	 * @param tag the tag to use for the log
	 * @param exception the exception to print
	 */
	public static final void a(String tag, Throwable exception) {
		printException(LogLevel.ASSERT, tag, exception);
	}
	
	/**
	 * Logs the exception to the server log file, formatted to display the log
	 * severity as ASSERT, as well as the time, and tag.
	 * @param tag the class outputting this log info
	 * @param exception the exception to print
	 */
	public static final void a(Object tag, Throwable exception) {
		printException(LogLevel.ASSERT, tag.getClass().getSimpleName(), exception);
	}
	
	private static final void printException(LogLevel level, String tag, Throwable exception) {
		synchronized (LOG) {
			log(level, tag, "Exception in thread\"%s\" %s: %s", Thread.currentThread().getName(), exception.getClass().getName(), exception.getMessage());
			log(level, tag, "Caused by: %s: %s", exception.getClass(), exception.getMessage());
			for (StackTraceElement e : exception.getStackTrace()) {
				log(level, tag, "    " + e.toString());
			}
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
