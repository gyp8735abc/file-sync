package com.foxjc.service.file.sync.http.server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import com.foxjc.service.file.sync.http.httpserver.HttpExchange;


/**
 * 默认的http响应对象
 * @author 郭跃鹏
 *
 */
public class HttpResponse implements Response {
	private HttpExchange httpExchange;
	private int statusCode = 200;
	private int bodyLength;
	private StringBuffer sb = new StringBuffer();
	private String charsetEncodingName;

	public HttpResponse(HttpExchange httpExchange) {
		this.httpExchange = httpExchange;
		charsetEncodingName = Objects.toString(httpExchange.getAttribute("charsetEncodingName"), "UTF-8");
	}

	@Override
	public void writeMessage(String message) {
		sb.append(message);
	}

	@Override
	public void writeMessage(int statusCode, String message) {
		this.statusCode = statusCode;
		sb.append(message);
	}


	@Override
	public OutputStream getOutputStream() {
		if(bodyLength == 0){
			throw new RuntimeException("bodyLength长度为0，无法输出内容");
		}
		try {
			httpExchange.sendResponseHeaders(statusCode, bodyLength);
			return httpExchange.getResponseBody();
		} catch (IOException e) {
			throw new RuntimeException("输出内容异常", e);
		}
	}

	@Override
	public void extrusionContent() {
		try {
			byte[] data = sb.toString().getBytes(charsetEncodingName);
			bodyLength = data.length;
			httpExchange.sendResponseHeaders(statusCode, bodyLength);// 设置响应头属性及响应信息的长度
			OutputStream out = httpExchange.getResponseBody(); // 获得输出流
			out.write(data);
			out.flush();
			out.close();
		} catch (IOException e) {
			throw new RuntimeException("输出内容异常", e);
		}
	}

	public void setBodyLength(int bodyLength) {
		this.bodyLength = bodyLength;
	}

	@Override
	public void setHeader(String key, String value) {
		httpExchange.getResponseHeaders().add(key, value);// 设置响应头属性及响应信息的长度
	}

	

}