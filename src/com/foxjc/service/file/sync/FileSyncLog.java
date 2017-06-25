package com.foxjc.service.file.sync;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileSyncLog {

	private static Logger log = LogManager.getFormatterLogger("file-sync");
	private static boolean debug = false;
	
	public static void info(String s, Object...vars){
		log.info(s, vars);
	}
	public static void error(Exception e, String s, Object...vars){
		log.error(s, vars, e);
	}
	public static void error(String s, Object...vars){
		log.error(s, vars);
	}
	public static void debug(String s, Object...vars){
		if(debug)log.debug(s, vars);
	}
	public static void setDebug(boolean debug) {
		FileSyncLog.debug = debug;
	}
	public static boolean isDebug() {
		return debug;
	}
}
