package com.foxjc.service.file.sync.filesystem;

import java.io.File;
import java.util.Vector;

/**
 * 自定义文件所
 * @author 郭跃鹏
 *
 */
public class FileLockUtils {

	private volatile static Vector<File> lockPool = new Vector<File>();
	
	public static void lock(File file){
		int idx = lockPool.indexOf(file);
		if(idx == -1){
			lockPool.add(file);
		}else{
			long startTime = System.currentTimeMillis();
			while(!Thread.interrupted() && System.currentTimeMillis() - startTime < 1000*30){
				try {
					if(lockPool.contains(file)){
						Thread.sleep(300);
					}else{
						lockPool.add(file);
						return;
					}
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}
	public static void unlock(File file){
		int idx = lockPool.indexOf(file);
		if(idx == -1){
			return ;
		}else{
			lockPool.remove(idx);
		}
	}
}
