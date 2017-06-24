package com.foxjc.service.file.sync.filesystem;

import java.io.File;
import java.util.ArrayList;

/**
 * 文件迭代器
 * @author 郭跃鹏
 *
 */
public class FileIterator{
	private int pointer = -1;
	private ArrayList<FileStack> queue = new ArrayList<FileStack>(16);

	public FileIterator(File dir) {
		if (dir.exists()) {
			queue.add(new FileStack(dir));
			pointer = 0;
		}
	}

	public boolean moveNext() {
		FileStack stack;
		int state = 0;
		while (!Thread.interrupted()) {
			if(pointer == -1)return false;
			stack = queue.get(pointer);
			state = stack.moveNext();
			if(state == 1){
				break;
			}else if(state == 2){
				queue.add(new FileStack(stack.getFile()));
				pointer++;
			}else if(state == 3){
				queue.remove(pointer--);
			}
		}
		if(state == 1){
			return true;
		}else{
			return false;
		}
	}

	public File getFile() {
		if (pointer == -1)return null;
		return queue.get(pointer).getFile();
	}

	private static class FileStack {
		private File[] files;
		private int pointer = -1;
		private int state;// -1未初始化0等待遍历1已指向文件2指向文件夹3遍历结束

		public FileStack(File file) {
			if (file.isFile()) {
				files = new File[]{file};
			} else {
				files = file.listFiles();
			}
			state = 0;
		}

		/**
		 * 
		 * @return -1未初始化0等待遍历1已指向文件2指向文件夹3遍历结束
		 */
		public int moveNext() {
			int idx = pointer + 1;
			if (idx < files.length) {
				pointer = idx;
				File f = files[pointer];
				if (f.isFile())state = 1;
				else state = 2;
			} else {
				state = 3;
			}

			return state;
		}

		public File getFile() {
			return files[pointer];
		}
	}
	
}