package com.foxjc.service.file.sync;

import org.apache.log4j.Logger;

public class FileSyncLog {

	private static Logger log = Logger.getLogger("FoxSync");
	private static boolean debug = false;
	
	public static void info(String s, Object...vars){
		log.info(String.format(s, vars));
	}
	public static void error(Exception e, String s, Object...vars){
		log.error(String.format(s, vars), e);
	}
	public static void error(String s, Object...vars){
		log.error(String.format(s, vars));
	}
	public static void debug(String s, Object...vars){
		if(debug)log.info(String.format(s, vars));
	}
	public static void setDebug(boolean debug) {
		FileSyncLog.debug = debug;
	}
	public static boolean isDebug() {
		return debug;
	}
}
