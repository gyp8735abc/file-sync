package com.foxjc.service.file.sync.http.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.foxjc.service.file.sync.http.httpserver.HttpExchange;
import com.foxjc.service.file.sync.http.httpserver.HttpHandler;

/**
 * 默认http请求处理器
 * @author 郭跃鹏
 *
 */
public class FoxjcServerHandler implements HttpHandler{

	private Logger log = Logger.getLogger(FoxjcServerHandler.class);
	private Map<String, RequestHandler> handlers = new HashMap<String, RequestHandler>();
	
	
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		HttpRequest request = new HttpRequest(exchange);
		HttpResponse response = new HttpResponse(exchange);
		
		String uri = exchange.getRequestURI().toString();
		RequestHandler handler = getHandler(uri);
		if(handler != null){
			handler.handle(request, response);
		}

		response.extrusionContent();
	}
	/**
	 * 根据请求地址，获取相关的处理器
	 * @param uri 请求地址
	 * @return 处理器
	 */
	public RequestHandler getHandler(String uri){
		RequestHandler handler = handlers.get(uri);
		if(handler == null){
			for (String key : handlers.keySet()) {
				if(StringUtils.startsWith(uri, key)){
					handler = handlers.get(key);
					break;
				}
			}
		}
		return handler;
	}
	/**
	 * 添加http请求处理器
	 * @param uri 请求地址
	 * @param handler 处理器
	 */
	public void addRequestHandler(String uri, RequestHandler handler){
		if(StringUtils.isBlank(uri) || handler == null){
			throw new RuntimeException(String.format("地址或处理器不能为空"));
		}
		if(handlers.containsKey(uri)){
			throw new RuntimeException(String.format("地址%s已存在处理器", uri));
		}
		handlers.put(uri, handler);
		log.info(String.format("%s的处理器被添加", uri));
	}
}
