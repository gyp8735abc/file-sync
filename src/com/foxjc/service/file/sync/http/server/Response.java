package com.foxjc.service.file.sync.http.server;

import java.io.OutputStream;

/**
 * 简易http服务器的响应对象接口
 * @author 郭跃鹏
 *
 */
public interface Response {

	/**
	 * 向客户端输出文字内容
	 * @param result
	 */
	public void writeMessage(String message);
	/**
	 * 向客户端输出文字内容
	 * 并指定状态码
	 * @param statusCode http_response 状态码
	 * @param result 消息
	 */
	public void writeMessage(int statusCode, String message);
	/**
	 * 获取响应体的流对象
	 * 输出流对象前，请先设置bodyLength
	 * @return
	 */
	public OutputStream getOutputStream();
	/**
	 * 输出结果
	 */
	public void extrusionContent();
	/**
	 * 设置响应对象的header
	 * @param key
	 * @param value
	 */
	public void setHeader(String key, String value);
	/**
	 * 设置响应体对象的长度，字节长度
	 * @param bodyLength
	 */
	public void setBodyLength(int bodyLength);

}