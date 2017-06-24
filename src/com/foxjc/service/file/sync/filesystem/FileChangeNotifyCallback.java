package com.foxjc.service.file.sync.filesystem;

import java.io.File;

import net.contentobjects.jnotify.JNotifyListener;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.filesystem.event.FileCreateEvent;
import com.foxjc.service.file.sync.filesystem.event.FileDeleteEvent;
import com.foxjc.service.file.sync.filesystem.event.FileModifiyEvent;
import com.foxjc.service.file.sync.filesystem.event.FileRenameEvent;

/**
 * 文件系统变更事件通知
 * 这个通知和实际文件操作是异步的
 * @author 郭跃鹏
 * 
 */
public class FileChangeNotifyCallback implements JNotifyListener {

	private GroupInfo group;

	public FileChangeNotifyCallback(GroupInfo group) {
		this.group = group;
	}

	@Override
	public void fileCreated(int wd, String rootPath, String name) {
//		File file = FileInfoUtils.getFile(rootPath, name);
//		FileCreateEvent event = new FileCreateEvent(group, file);
//		FileEventHandler.push(event);
//		System.out.println(String.format("create file %s", file.getAbsolutePath()));
	}

	@Override
	public void fileDeleted(int wd, String rootPath, String name) {
		File file = FileInfoUtils.getFile(rootPath, name);
		FileSyncLog.debug("FileChangeNotify组%s删除文件%s", group.getGroupNo(), file.getAbsolutePath());
		FileDeleteEvent event = new FileDeleteEvent(group, file);
		FileEventHandler.push(event);
	}

	/**
	 * windows的修改事件=文件写入事件+文件写完成事件
	 * 所以剔除第一个事件，取第二个事件
	 */
	@Override
	public void fileModified(int wd, String rootPath, String name) {
		File file = FileInfoUtils.getFile(rootPath, name);
		if(file.isDirectory())return;
		FileSyncLog.debug("FileChangeNotify组%s修改文件%s", group.getGroupNo(), file.getAbsolutePath());
		FileModifiyEvent event = new FileModifiyEvent(group, file);
		FileEventHandler.push(event, 1000);
	}

	@Override
	public void fileRenamed(int wd, String rootPath, String oldName, String newName) {
		File oldFile = FileInfoUtils.getFile(rootPath, oldName);
		File newFile = FileInfoUtils.getFile(rootPath, newName);
		FileSyncLog.debug("FileChangeNotify组%s重命名文件%s->%s", group.getGroupNo(), oldFile.getAbsolutePath(), newFile.getAbsolutePath());
		if(oldName == null || "null".equals(oldName)){
			FileCreateEvent event = new FileCreateEvent(group, newFile);
			FileEventHandler.push(event);
			FileSyncLog.debug("%dRename,源文件为空，直接同步文件%s", wd, newFile.getAbsolutePath());
			if(newFile.length() > 0){
				FileModifiyEvent modifyFileEvent = new FileModifiyEvent(group, newFile);
				FileEventHandler.push(modifyFileEvent);
			}
		}else{
			FileRenameEvent event = new FileRenameEvent(group, oldFile, newFile	);
			FileEventHandler.push(event);
		}
	}

}
