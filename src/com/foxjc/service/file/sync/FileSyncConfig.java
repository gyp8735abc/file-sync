package com.foxjc.service.file.sync;

/**
 * 文件同步配置
 * @author 郭跃鹏
 * 2016/8/26-下午2:58:44
 */
public class FileSyncConfig {

	public static String dbFilePath;//h2数据库文件存放的位置
	public static String serverName;
	public static String localIp;
	public static int dbPort;
	public static int httpPort;
	public static String defaultCharsetName;
	public static int maxRequest = 256;
	public static int initThreadPoolSize;
	public static int maxThreadPoolSize;
}
