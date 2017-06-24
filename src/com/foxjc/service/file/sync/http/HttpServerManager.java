package com.foxjc.service.file.sync.http;

import com.foxjc.service.file.sync.http.server.FoxjcHttpServer;

/**
 * http服务器
 * @author 郭跃鹏
 * 2016/8/27-下午1:57:47
 */
public class HttpServerManager {

	private static FoxjcHttpServer httpServer = new FoxjcHttpServer();
	
	public static void start(){
		httpServer.start();
	}
	public static void stop(){
		httpServer.stop();
	}
}
