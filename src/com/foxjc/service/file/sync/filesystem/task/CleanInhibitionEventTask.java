package com.foxjc.service.file.sync.filesystem.task;

import com.foxjc.service.file.sync.filesystem.FileEventHandler;

/**
 * 压缩抑制事件
 * 移除过期的抑制事件
 * @author 郭跃鹏
 *
 */
public class CleanInhibitionEventTask implements Runnable{

	@Override
	public void run() {
		FileEventHandler.cleanInhibitionEvent();
	}

}
