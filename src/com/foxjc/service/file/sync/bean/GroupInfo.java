package com.foxjc.service.file.sync.bean;

import java.util.Date;
import java.util.List;

/**
 * 盟友信息
 * @author 郭跃鹏
 * 2016/8/26-下午4:09:34
 */
public class GroupInfo {

	private String id;
	private String groupNo;
	private String groupName;
	private String path;
	private Date recordTime;
	private List<MemberInfo> members;
	
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public List<MemberInfo> getMembers() {
		return members;
	}
	public void setMembers(List<MemberInfo> members) {
		this.members = members;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public Date getRecordTime() {
		return recordTime;
	}
	public void setRecordTime(Date recordTime) {
		this.recordTime = recordTime;
	}
	public String getGroupNo() {
		return groupNo;
	}
	public void setGroupNo(String groupNo) {
		this.groupNo = groupNo;
	}
	public String getGroupName() {
		return groupName;
	}
	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}
}
