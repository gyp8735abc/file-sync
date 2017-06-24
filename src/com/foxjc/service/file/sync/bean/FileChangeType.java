package com.foxjc.service.file.sync.bean;

/**
 * 文件变更类型
 * 1创建文件
 * 2修改文件
 * 3删除文件
 * 4重命名文件
 * 5修改文件最後更新时间
 * @author 郭跃鹏
 * 2016/8/26-下午3:39:06
 */
public enum FileChangeType {
	
	CREATE(1),MODIFY(2),DELETE(3),RENAME(4),UPDATE_LAST_MODIFY_DATE(5);
	private int value;
	FileChangeType(int value){
		this.value = value;
	}
	public int getValue() {
		return value;
	}
	@Override
	public String toString() {
		return String.format("%s(%s)", super.toString(), value);
	}
	
}
