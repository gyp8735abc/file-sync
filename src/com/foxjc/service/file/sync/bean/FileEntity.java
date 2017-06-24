package com.foxjc.service.file.sync.bean;

import java.io.Serializable;

public class FileEntity implements Serializable{

	private static final long serialVersionUID = 3926554163127747594L;
	private String filename;
	private long lastModifyTime;
	private String md5;
	
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public long getLastModifyTime() {
		return lastModifyTime;
	}
	public void setLastModifyTime(long lastModifyTime) {
		this.lastModifyTime = lastModifyTime;
	}
	public String getMd5() {
		return md5;
	}
	public void setMd5(String md5) {
		this.md5 = md5;
	}
	
}
