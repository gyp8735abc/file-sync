package com.foxjc.service.file.sync.http;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Objects;

import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.FormBody.Builder;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.foxjc.service.file.sync.FileSyncConfig;
import com.mzlion.core.http.ContentType;

public abstract class HttpClient {
	
	private static OkHttpClient okHttpClient = new OkHttpClient();

	public static Response get(String url, Map<String, String> params){
		// step 2： 创建一个请求，不指定请求方法时默认是GET。
		Request.Builder requestBuilder = new Request.Builder().url(buildUrl(url, params));
		//可以省略，默认是GET请求
		requestBuilder.method("GET",null);
		// step 3：创建 Call 对象
		try {
			return okHttpClient.newCall(requestBuilder.build()).execute();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public static void getAsync(String url, Map<String, String> params, Callback callback){
        // step 2： 创建一个请求，不指定请求方法时默认是GET。
        Request.Builder requestBuilder = new Request.Builder().url(buildUrl(url, params));
        //可以省略，默认是GET请求
        requestBuilder.method("GET",null);
        // step 3：创建 Call 对象
        okHttpClient.newCall(requestBuilder.build()).enqueue(callback);
	}
	public static Response post(String url, Map<String, String> params){
		//step 2: 创建  FormBody.Builder
		Builder builder = new FormBody.Builder();
		if(params != null){
			for (Map.Entry<String, String> entry : params.entrySet()) {
				builder.add(entry.getKey(), Objects.toString(entry.getValue()));
			}
		}
		
		//step 3: 创建请求
		Request request = new Request.Builder().url(url)
				.post(builder.build())
				.build();
		
		//step 4： 建立联系 创建Call对象
		try {
			return okHttpClient.newCall(request).execute();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public static void postAsync(String url, Map<String, String> params, Callback callback){
        //step 2: 创建  FormBody.Builder
        Builder builder = new FormBody.Builder();
        if(params != null){
        	for (Map.Entry<String, String> entry : params.entrySet()) {
        		builder.add(entry.getKey(), Objects.toString(entry.getValue()));
			}
        }
        FormBody formBody = builder.build();

        //step 3: 创建请求
        Request request = new Request.Builder().url(url)
                .post(formBody)
                .build();

        //step 4： 建立联系 创建Call对象
        okHttpClient.newCall(request).enqueue(callback);
	}
	public static void file(String url, Map<String, String> params, File file, Callback callback){
		// step 1: 创建 OkHttpClient 对象
        

        //step 2:创建 RequestBody 以及所需的参数
        //2.1 获取文件
        //2.2 创建 MediaType 设置上传文件类型
        MediaType mediaType = MediaType.parse(ContentType.DEFAULT_BINARY.getMimeType());
        //2.3 获取请求体
        RequestBody requestBody = RequestBody.create(mediaType, file);
        //step 3：创建请求
        Request request = new Request.Builder().url(buildUrl(url, params))
                .post(requestBody)
                .build();

        //step 4 建立联系
        okHttpClient.newCall(request).enqueue(callback);
	}
	
	private static String buildUrl(String url, Map<String, String> params){
		StringBuilder sb = new StringBuilder(url);
		if(params == null)return sb.toString();
		char split = '?';
		if(url.indexOf(split) != -1)split = '&';
		for (Map.Entry<String, String> entry : params.entrySet()) {
			try {
				sb.append(split).append(entry.getKey()).append('=').append(URLEncoder.encode(Objects.toString(entry.getValue()), FileSyncConfig.defaultCharsetName));
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(split == '?')split = '&';
		}
		
		return sb.toString();
	}
	
	public static void close(){
		try {
			okHttpClient.dispatcher().executorService().shutdown();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
