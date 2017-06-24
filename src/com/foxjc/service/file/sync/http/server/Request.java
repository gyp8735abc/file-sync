package com.foxjc.service.file.sync.http.server;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

/**
 * 建议http服务器 请求对象接口
 * @author 郭跃鹏
 *
 */
public interface Request {
	/**
	 * 获取请求参数
	 * @param param
	 * @return
	 */
	public String getParamter(String param);

	/**
	 * 获取请求方式
	 * @return
	 */
	public String getMethod();
	/**
	 * 获取header内容
	 * @param key
	 * @return
	 */
	public String getHeader(String key);
	/**
	 * 获取header的数组值
	 * @param key
	 * @return
	 */
	public List<String> getHeaders(String key);

	/**
	 * 获取请求的地址
	 * @return
	 */
	public URI getReuestURI();
	/**
	 * 获取请求的stream对象
	 * @return
	 */
	public InputStream getRequestBody();
	/**
	 * 获取请求内容字符串内容
	 * @return
	 */
	public String getRequestBodyAsString();
}