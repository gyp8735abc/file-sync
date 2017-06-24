package com.foxjc.service.file.sync.filesystem.event;

import java.io.File;
import java.util.Date;

import com.foxjc.service.file.sync.bean.FileChangeType;
import com.foxjc.service.file.sync.bean.GroupInfo;

public interface FileEvent {

	public void execute();
	public GroupInfo getGroup(); 
	public File getFile();
	public Date getRecordTime();
	public int getState();
	public void setState(int state);
	public FileChangeType getEventType();
}
