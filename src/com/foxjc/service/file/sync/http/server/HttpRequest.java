package com.foxjc.service.file.sync.http.server;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.foxjc.service.file.sync.http.httpserver.HttpExchange;


/**
 * 简单的http请求对象
 * @author 郭跃鹏
 *
 */
public class HttpRequest implements Request {

	private HttpExchange httpExchange;
	private Map<String, Object> paramMap = new HashMap<String, Object>();
	private Map<String, List<String>> headMap = new HashMap<String, List<String>>();
	private final static String REQUEST_LENGTH_KEY = "Content-Length";
	private int bodyLength;
	private String method;
	private String contextPath;
	private String charsetEncodingName;

	public HttpRequest(HttpExchange httpExchange) {
		this.httpExchange = httpExchange;
		method = httpExchange.getRequestMethod();
		charsetEncodingName = Objects.toString(httpExchange.getAttribute("charsetEncodingName"), "UTF-8");

		contextPath = httpExchange.getHttpContext().getPath();
		
		//处理paramter
		String query = getReuestURI().getRawQuery();
		if(query != null){
			String[] arrayStr = StringUtils.split(query, "&");
			if (arrayStr != null) {
				for (String str : arrayStr) {
					String key = StringUtils.substringBefore(str, "=");
					String value = StringUtils.substringAfter(str, "=");
					if (key != null && StringUtils.isNotBlank(value)){
						try {
							paramMap.put(key, URLDecoder.decode(value, charsetEncodingName));
						} catch (Exception e) {
							paramMap.put(key, value);
						}
					}
				}
			}
		}

		for (String s : httpExchange.getRequestHeaders().keySet()) {
			headMap.put(s.toLowerCase(), httpExchange.getRequestHeaders().get(s));
		}
		String contentType = getHeader("Content-Type");
		if(StringUtils.isBlank(contentType))contentType = "application/x-www-form-urlencoded";
		
		bodyLength = NumberUtils.toInt(getHeader(REQUEST_LENGTH_KEY), 0);
		
		if(StringUtils.equals(contentType, "application/x-www-form-urlencoded")){
			String body = getRequestBodyAsString();
			String[] ps = StringUtils.split(body, "&");
			if (ps != null) {
				for (String str : ps) {
					String key = StringUtils.substringBefore(str, "=");
					String value = StringUtils.substringAfter(str, "=");
					if (key != null){
						try {
							paramMap.put(key, URLDecoder.decode(value, charsetEncodingName));
						} catch (Exception e) {
							paramMap.put(key, value);
						}
					}
				}
			}
		}
		if(httpExchange.getHttpContext().getAttributes() != null){
			paramMap.putAll(httpExchange.getHttpContext().getAttributes());
		}
	}

	@Override
	public String getRequestBodyAsString() {
		if(bodyLength < 1)return "";
		InputStream is = getRequestBody();
		StringBuffer sb = new StringBuffer();
		try {
			InputStreamReader reader = new InputStreamReader(is, charsetEncodingName);
			char[] cbuf = new char[1024];
			int readCount = 0, n;
			while ((n = reader.read(cbuf)) > 0) {
				sb.append(cbuf, 0, n);
				readCount += n;
				if(readCount >= bodyLength)break;
			}
			return sb.toString();
		} catch (Exception e) {
			throw new RuntimeException("获取request body异常", e);
		}
	}
	
	@Override
	public String getParamter(String param) {
		Object o = paramMap.get(param);
		return o == null ? "": o.toString();
	}

	@Override
	public String getMethod() {
		return method;
	}

	@Override
	public URI getReuestURI() {
		return httpExchange.getRequestURI();
	}

	@Override
	public InputStream getRequestBody() {
		return httpExchange.getRequestBody();
	}

	@Override
	public String getHeader(String key) {
		if(key == null)return null;
		List<String> head = headMap.get(key.toLowerCase());
		if(head == null)return null;
		if(head.size() == 1)return head.get(0);
		else throw new RuntimeException("不唯一的Head");
	}
	@Override
	public List<String> getHeaders(String key) {
		if(key == null)return null;
		return headMap.get(key.toLowerCase());
	}


	public int getBodyLength() {
		return bodyLength;
	}


	public String getContextPath() {
		return contextPath;
	}

}