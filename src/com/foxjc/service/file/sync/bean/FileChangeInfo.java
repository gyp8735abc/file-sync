package com.foxjc.service.file.sync.bean;


/**
 * 文件变化信息
 * @author 郭跃鹏
 * 2016/8/26-下午3:25:22
 */
public class FileChangeInfo {

	private FileInfo file;
	private FileChangeType changeType;
	public FileInfo getFile() {
		return file;
	}
	public void setFile(FileInfo file) {
		this.file = file;
	}
	public FileChangeType getChangeType() {
		return changeType;
	}
	public void setChangeType(FileChangeType changeType) {
		this.changeType = changeType;
	}
}
