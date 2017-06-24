package com.foxjc.service.file.sync.http.server;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.foxjc.service.file.sync.FileSyncConfig;
import com.foxjc.service.file.sync.http.action.CompareFileAction;
import com.foxjc.service.file.sync.http.action.FileCreatedAction;
import com.foxjc.service.file.sync.http.action.FileDeletedAction;
import com.foxjc.service.file.sync.http.action.FileModifiedAction;
import com.foxjc.service.file.sync.http.action.FileRenamedAction;
import com.foxjc.service.file.sync.http.action.GroupEventChangeAction;
import com.foxjc.service.file.sync.http.action.MemberOfflineAction;
import com.foxjc.service.file.sync.http.action.MemberOnlineAction;
import com.foxjc.service.file.sync.http.action.PullFileAction;
import com.foxjc.service.file.sync.http.action.PushFileAction;
import com.foxjc.service.file.sync.http.action.QueryMemberReadyTimeAction;
import com.foxjc.service.file.sync.http.filter.FoxjcServerFilter;
import com.foxjc.service.file.sync.http.httpserver.HttpContext;
import com.foxjc.service.file.sync.http.httpserver.HttpServer;
import com.foxjc.service.file.sync.http.httpserver.spi.HttpServerProvider;

/**
 * http服务器
 * 
 * @author 郭跃鹏
 * 
 */
public class FoxjcHttpServer {

	private Logger log = Logger.getLogger(FoxjcHttpServer.class);

	private String contextPath = "/rsync";
	private HttpServerProvider provider;
	private HttpServer httpserver;
	private ExecutorService threadPool;

	public void start() {
		threadPool = new ThreadPoolExecutor(FileSyncConfig.initThreadPoolSize, FileSyncConfig.maxThreadPoolSize, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>());
		log.info(String.format("http服务线程池初始化大小%d，最大%d", FileSyncConfig.initThreadPoolSize, FileSyncConfig.maxThreadPoolSize));
		provider = HttpServerProvider.provider();
		try {
			httpserver = provider.createHttpServer(new InetSocketAddress(FileSyncConfig.httpPort), FileSyncConfig.maxRequest, null);
			log.info(String.format("http服务绑定%d端口，接收最大连接%d", FileSyncConfig.httpPort, FileSyncConfig.maxRequest));
		} catch (Exception e) {
			log.error(String.format("创建http服务异常:%s", e.getMessage()));
			stop();
			return;
		}

		// actions
		FoxjcServerHandler handler = new FoxjcServerHandler();
		handler.addRequestHandler(String.format("%s%s", contextPath, "/memberOnline"), new MemberOnlineAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/memberOffline"), new MemberOfflineAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/fileCreated"), new FileCreatedAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/fileDeleted"), new FileDeletedAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/fileModified"), new FileModifiedAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/fileRenamed"), new FileRenamedAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/compareFile"), new CompareFileAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/pushFile"), new PushFileAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/pullFile"), new PullFileAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/queryMemberReadyTime"), new QueryMemberReadyTimeAction());
		handler.addRequestHandler(String.format("%s%s", contextPath, "/groupEventChange"), new GroupEventChangeAction());
		HttpContext httpContext = httpserver.createContext(contextPath, handler);
		// filter
		httpContext.getFilters().add(new FoxjcServerFilter());
		// 上下文
		Map<String, Object> attrs_ = httpContext.getAttributes();
		attrs_.put("serverName", FileSyncConfig.serverName);
		attrs_.put("charsetEncodingName", FileSyncConfig.defaultCharsetName);

		httpserver.setExecutor(threadPool);
		httpserver.start();
		log.info(String.format("http服务启动"));
	}

	public void stop() {
		if (httpserver != null) {
			httpserver.stop(0);
		}
		if (threadPool != null) {
			threadPool.shutdown();
		}
	}
}
