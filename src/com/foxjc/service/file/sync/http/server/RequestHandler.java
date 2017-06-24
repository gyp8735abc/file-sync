package com.foxjc.service.file.sync.http.server;

/**
 * 简易http服务器-处理器
 * @author 郭跃鹏
 *
 */
public interface RequestHandler {
	/**
	 * 处理请求
	 * @param request 请求对象
	 * @param response 响应对象
	 */
	public void handle(HttpRequest request, HttpResponse response);
}
