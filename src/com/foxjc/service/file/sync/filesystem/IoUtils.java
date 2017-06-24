package com.foxjc.service.file.sync.filesystem;

import java.io.Closeable;

public class IoUtils {

	public static void close(Closeable os ){
		if(os == null)return;
		try {
			os.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void close(AutoCloseable os ){
		if(os == null)return;
		try {
			os.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
