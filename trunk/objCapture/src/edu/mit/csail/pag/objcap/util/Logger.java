package edu.mit.csail.pag.objcap.util;

/**
 * Simple logging
 * 
 * @author hunkim
 * 
 */
public class Logger {

	// verbose
	public static boolean verbose = true;

	public static enum Level {
		TRACE, DEBUG, INFO, WARN, ERROR, FATAL
	}

	// decide logging level
	private static Level level = Level.INFO;

	static private void log(Level level, String message) {
		if (verbose && Logger.level.compareTo(level) <= 0) {
			println(message);
		}
	}

	static public void trace(String message) {
		log(Level.TRACE, message);
	}

	static public void debug(String message) {
		log(Level.DEBUG, message);
	}

	static public void info(String message) {
		log(Level.INFO, message);
	}

	static public void warn(String message) {
		log(Level.WARN, message);
	}

	static public void error(String message) {
		log(Level.ERROR, message);
	}

	static public void fatal(String message) {
		println(message);
	}

	public static void println(String message) {
		System.out.println(message);

	}
}
