package com.foxjc.service.file.sync.http.filter;

import java.io.IOException;

import com.foxjc.service.file.sync.http.httpserver.Filter;
import com.foxjc.service.file.sync.http.httpserver.HttpExchange;
/**
 * 无用的filter
 * @author 郭跃鹏
 *
 */
public class FoxjcServerFilter extends Filter{

	@Override
	public String description() {
		return "foxjc http server filter";
	}

	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		chain.doFilter(exchange);
	}
}
