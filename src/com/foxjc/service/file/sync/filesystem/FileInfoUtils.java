package com.foxjc.service.file.sync.filesystem;

import java.io.File;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * 文件工具类
 * @author 郭跃鹏
 *
 */
public class FileInfoUtils {
	
	static {
		String name = System.getProperty("os.name");
		isWindows = StringUtils.containsIgnoreCase(name, "windows");
		isLinux = StringUtils.containsIgnoreCase(name, "linux");
	}

	public final static boolean isWindows;
	public final static boolean isLinux;
	
	public static File getFile(String root, String filename){
		root = FilenameUtils.normalizeNoEndSeparator(root);
		return new File(root + File.separator + filename);
	}
	public static String differencePath(String path1, String path2) {
		int idx = StringUtils.indexOf(path1, path2);
		if (idx != -1) {
			return StringUtils.substring(path1, idx + path2.length());
		}

		idx = StringUtils.indexOf(path2, path1);
		if (idx != -1) {
			return StringUtils.substring(path2, idx + path1.length());
		}
		return null;
	}
	
	public static void cleanEmptyDir(File file){
		if(file.isFile() && !file.exists()){
			cleanEmptyDir(file.getParentFile());
			return;
		}
		String[] files = file.list();
		if(files == null || files.length == 0){
			file.delete();
			cleanEmptyDir(file.getParentFile());
		}
	}
}