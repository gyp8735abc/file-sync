package com.foxjc.service.file.sync.http.server;

import com.alibaba.fastjson.JSONObject;

/**
 * 简易的json操作工具类
 * @author 郭跃鹏
 *
 */
public class JsonUtils {

	public static double getDouble(JSONObject json, String key, double nullValue){
		double value = -1;
		if(json.containsKey(key)){
			value = json.getDoubleValue(key);
		}else{
			return nullValue;
		}
		return value;
	}
	public static int getInt(JSONObject json, String key, int nullValue){
		int value = -1;
		if(json.containsKey(key)){
			value = json.getIntValue(key);
		}else{
			return nullValue;
		}
		return value;
	}
	public static String getString(JSONObject json, String key, String nullValue){
		String value = null;
		if(json.containsKey(key)){
			value = json.getString(key);
		}else{
			return nullValue;
		}
		return value;
	}
	public static boolean getBoolean(JSONObject json, String key, boolean nullValue){
		if(json.containsKey(key)){
			return json.getBoolean(key);
		}else{
			return nullValue;
		}
	}
}
