package com.foxjc.service.file.sync.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileChangeType;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.bean.MemberInfo;
import com.foxjc.service.file.sync.filesystem.IoUtils;

/**
 * 文件同步数据访问类
 * 
 * @author 郭跃鹏 2016/8/26-下午3:21:42
 */
public class FileManagerDao {

	/*
	 * 初始化表结构 盟友表 文件信息表 文件变更历史表（只保留最近1个月的记录）
	 */
	public static void initTable() {
		String sql = null;
		InputStream is = null;
		try {
			is = FileManagerDao.class.getResourceAsStream("db.sql");
			sql = IOUtils.toString(is, "UTF-8");
		} catch (IOException e) {
			FileSyncLog.error(e, "初始化数据库异常");
		} finally {
			IoUtils.close(is);
		}
		if (sql != null) {
			H2Utils.update(sql);
			FileSyncLog.info("初始化数据库完成");
		}
	}

	public static GroupInfo getGroupInfoById(String groupId) {
		String sql = "select * from groups_info where id = ?";
		return H2Utils.queryBean(sql, GroupInfo.class, groupId);
	}

	public static GroupInfo getGroupInfoByNo(String groupNo) {
		String sql = "select * from groups_info where group_no = ?";
		return H2Utils.queryBean(sql, GroupInfo.class, groupNo);
	}

	public static List<GroupInfo> getAllGroupInfo() {
		String sql = "select * from groups_info";
		return H2Utils.queryBeanList(sql, GroupInfo.class);
	}

	public static void removeGroupFileInfos(GroupInfo group) {
		String sql = "delete from group_files where group_id = ?";
		H2Utils.update(sql, group.getId());
	}


	public static void clearGroupAndMembers() {
		String sql = "delete from groups_info where id is not null";
		H2Utils.update(sql);
		sql = "delete from group_members where id is not null";
		H2Utils.update(sql);
	}

	public static List<MemberInfo> getGroupMembers(GroupInfo group) {
		String sql = "select * from group_members where group_id = ?";
		return H2Utils.queryBeanList(sql, MemberInfo.class, group.getId());
	}

	public static GroupInfo addGroupInfo(String groupId, String groupName, String path) {
		String sql = "select id from groups_info where group_no = ?";
		String id = H2Utils.queryScalarString(sql, groupId);
		if (StringUtils.isBlank(id)) {
			sql = "insert into groups_info (group_no, group_name, path) values (?, ?, ?)";
			id = H2Utils.insert(sql, groupId, groupName, path);
		}
		return getGroupInfoById(id);
	}
	
	public static void updateGroupReady(GroupInfo group) {
		String sql = "update groups_info set ready_time = CURRENT_TIMESTAMP where id = ?";
		H2Utils.update(sql, group.getId());
	}
	public static void updateGroupUnready(GroupInfo group) {
		String sql = "update groups_info set ready_time = null where id = ?";
		H2Utils.update(sql, group.getId());
	}
	public static long getGroupReadyTime(GroupInfo group) {
		String sql = "select ready_time from groups_info where id = ?";
		Timestamp time = (Timestamp)H2Utils.queryScalarObject(sql, group.getId());
		return time == null ? -1:time.getTime();
	}

	public static MemberInfo addGroupMember(GroupInfo group, String ip, int port, String member_no) {
		String sql = "insert into group_members (group_id, ip, port, member_no, online) values (?, ?, ?, ?, 'N')";
		String id = H2Utils.insert(sql, group.getId(), ip, port, member_no);
		return getMemberInfoById(id);
	}

	public static MemberInfo getMemberInfoById(String memberId) {
		String sql = "select * from group_members where id = ?";
		return H2Utils.queryBean(sql, MemberInfo.class, memberId);
	}

	public static boolean updateGroupMemberOnlineStatus(String groupNo, String memberNo) {
		GroupInfo group = getGroupInfoByNo(groupNo);
		if (group == null) {
			FileSyncLog.info("盟友%s发来消息，没有找到GroupNo=%s的组", memberNo, groupNo);
			return false;
		}
		List<MemberInfo> members = getGroupMembers(group);
		MemberInfo member = null;
		for (int i = 0; i < members.size(); i++) {
			member = members.get(i);
			if (StringUtils.equals(member.getMemberNo(), memberNo)) {
				break;
			}
			member = null;
		}
		if (member == null) {
			FileSyncLog.info("组%s中没有找到%s的member", groupNo, memberNo);
			return false;
		}
		String sql = "update group_members t set t.online = 'Y' where id = ?";
		H2Utils.update(sql, member.getId());
		return true;
	}
	

	public static boolean updateGroupMemberOfflineStatus(String groupNo, String memberNo) {
		GroupInfo group = getGroupInfoByNo(groupNo);
		if (group == null) {
			FileSyncLog.info("盟友%s发来消息，没有找到GroupNo=%s的组", memberNo, groupNo);
			return false;
		}
		List<MemberInfo> members = getGroupMembers(group);
		MemberInfo member = null;
		for (int i = 0; i < members.size(); i++) {
			member = members.get(i);
			if (StringUtils.equals(member.getMemberNo(), memberNo)) {
				break;
			}
			member = null;
		}
		if (member == null) {
			FileSyncLog.info("组%s中没有找到%s的member", groupNo, memberNo);
			return false;
		}
		String sql = "update group_members t set t.online = 'N' where id = ?";
		H2Utils.update(sql, member.getId());
		return true;
	}

	/**
	 * 更新文件信息
	 */
	public static void updateFileInfo(GroupInfo group, String filePath, String md5, long fileLength, long lastModifyTime) {
		String sql = "select id from group_files where group_id = ? and path = ? and del = 0";
		String id = H2Utils.queryScalarString(sql, group.getId(), filePath);
		if(StringUtils.isNotEmpty(id)){
			sql = "update group_files set last_modify_time = ?, md5 = ?, length = ?, modify_time = CURRENT_TIMESTAMP where id = ?";
			H2Utils.update(sql, lastModifyTime, md5, fileLength, id);
		}else{
			FileSyncLog.info("没有找到组%s中的文件%s信息，无法更新文件信息", group.getId(), filePath);
		}
	}
	public static void updateFileMd5(String fileId, String md5, long fileLength, long lastModifyTime) {
		String sql = "update group_files set md5 = ?, length = ?, last_modify_time = ?, modify_time = CURRENT_TIMESTAMP where id = ?";
		H2Utils.update(sql, md5, fileLength, lastModifyTime, fileId);
	}
	
	public static FileInfo addFileInfo(GroupInfo group, String filePath, String md5, long fileLength, long lastModifyTime) {
		FileInfo fileInfo = getFileInfo(group, filePath);
		if(fileInfo == null){
			String sql = "insert into group_files (group_id, path, md5, last_modify_time, length) values (?, ?, ?, ?, ?)";
			H2Utils.update(sql, group.getId(), filePath, md5, lastModifyTime, fileLength);
			fileInfo = getFileInfo(group, filePath);
		}else{
			String sql = "update group_files set last_modify_time = ?, md5 = ?, length = ?, modify_time = CURRENT_TIMESTAMP where id = ?";
			H2Utils.update(sql, lastModifyTime, md5, fileLength, fileInfo.getId());
			fileInfo.setLastModifyTime(lastModifyTime);
			fileInfo.setMd5(md5);
			fileInfo.setLength(fileLength);
		}
		return fileInfo;
	}
	

	public static FileInfo getFileInfo(GroupInfo group, String filePath) {
		String sql = "select * from group_files where group_id = ? and path = ? and del = 0";
		return H2Utils.queryBean(sql, FileInfo.class, group.getId(), filePath);
	}
	public static FileInfo getFileInfoById(String id) {
		String sql = "select * from group_files where id = ?";
		return H2Utils.queryBean(sql, FileInfo.class, id);
	}

	public static void removeFileInfo(FileInfo fileInfo) {
		String sql = "delete from group_files where id = ?";
		H2Utils.update(sql, fileInfo.getId());
	}
	public static void markDeleteFileInfo(FileInfo fileInfo) {
		String sql = "update group_files set del = 1, modify_time = CURRENT_TIMESTAMP where id = ?";
		H2Utils.update(sql, fileInfo.getId());
	}
	public static int removeAllDeletedFileInfo(){
		String sql = "delete from group_files where del = 1";
		return H2Utils.update(sql);
	}
	public static void lockAllFileInfo(GroupInfo group){
		String sql = "update group_files set lock = 1 where group_id = ?";
		H2Utils.update(sql, group.getId());
	}
	public static void unlockFileInfo(FileInfo fileInfo){
		String sql = "update group_files set lock = 0 where id = ?";
		H2Utils.update(sql, fileInfo.getId());
	}
	public static int removeAllLockedFileInfo(){
		String sql = "delete from group_files where lock = 1";
		return H2Utils.update(sql);
	}

	public static void renameFileInfo(GroupInfo group, String oldFilePath, String newFilePath, long lastModifyTime) {
		String sql = "update group_files set path = ?, last_modify_time = ?, modify_time = CURRENT_TIMESTAMP where group_id = ? and path = ? and del = 0";
		H2Utils.update(sql, newFilePath, lastModifyTime, group.getId(), oldFilePath);
	}

	public static List<FileInfo> getAllFileInfo(GroupInfo group) {
		String sql = "SELECT * FROM GROUP_FILES where group_id = ? and del = 0";
		return H2Utils.queryBeanList(sql, FileInfo.class, group.getId());
	}

	/**
	 * 获取分组盟友
	 * @param group
	 * @param fromGroupNo
	 */
	public static MemberInfo getGroupMember(GroupInfo group, String memberNo) {
		String sql = "select * from group_members where group_id = ? and member_no = ?";
		return H2Utils.queryBean(sql, MemberInfo.class, group.getId(), memberNo);
	}

	public static void updateFileInfoStatus(String id, String status) {
		String sql = "update group_files set status = ? where id = ?";
		H2Utils.update(sql, status, id);
	}
	public static void addFileNotSyncLogForAllMember(GroupInfo group, String filePath, FileChangeType eventType){
		List<MemberInfo> members = getGroupMembers(group);
		for (int i = 0; i < members.size(); i++) {
			MemberInfo member = members.get(i);
			addFileNotSyncLog(group, member, filePath, eventType);
		}
	}
	public static void addFileNotSyncLog(GroupInfo group, MemberInfo member, String filePath, FileChangeType eventType){
		String sql = "insert into group_file_non_sync (file_path, member_id, event_type, add_time) values (?, ?, ?, ?)";
		H2Utils.update(sql, filePath, member.getId(), eventType.getValue(), System.currentTimeMillis());
	}
	public static void markFileNotSyncRecordForDel(String id){
		String sql = "update group_file_non_sync set del = 1 where id = ?";
		H2Utils.update(sql, id);
	}
	public static void removeNotSyncRecordByPath(MemberInfo member, String path){
		String sql = "delete from group_file_non_sync where file_path = ? and member_id = ?";
		H2Utils.update(sql, path, member.getId());
	}
	public static void removeExpiredNotSyncRecord(){
		String sql = "delete from group_file_non_sync where del = ?";
		H2Utils.update(sql, 1);
	}
	public static boolean isMemberOnline(MemberInfo member) {
		String sql = "select ONLINE from group_members where id = ?";
		String status = H2Utils.queryScalarString(sql, member.getId());
		return "Y".equals(status);
	}
	public static int distinctFileInfo(GroupInfo group) {
		String sql = "DELETE FROM GROUP_FILES e WHERE e.id > (SELECT MIN(x.id) FROM GROUP_FILES x WHERE x.GROUP_ID = e.GROUP_ID AND x.PATH = e.PATH) and GROUP_ID = ?";
		return H2Utils.update(sql, group.getId());
	}
}