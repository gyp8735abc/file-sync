package com.foxjc.service.file.sync.filesystem.event;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.foxjc.service.file.sync.FileSyncConfig;
import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileChangeType;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.bean.MemberInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.FileInfoUtils;
import com.foxjc.service.file.sync.filesystem.FileMd5Utils;
import com.foxjc.service.file.sync.http.HttpClient;

public class FileRenameEvent implements FileEvent{
	private GroupInfo group;
	private File oldFile;
	private File newFile;
	private FileChangeType eventType = FileChangeType.RENAME;
	private Date recordTime;
	private int state = 0;

	public FileRenameEvent(GroupInfo group, File oldFile, File newFile){
		this.group = group;
		this.oldFile = oldFile;
		this.newFile = newFile;
		recordTime = new Date();
	}
	@Override
	public File getFile() {
		return newFile;
	}
	public GroupInfo getGroup() {
		return group;
	}
	public void setGroup(GroupInfo group) {
		this.group = group;
	}
	public File getOldFile() {
		return oldFile;
	}
	public void setOldFile(File oldFile) {
		this.oldFile = oldFile;
	}
	public File getNewFile() {
		return newFile;
	}
	public void setNewFile(File newFile) {
		this.newFile = newFile;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof FileRenameEvent){
			FileRenameEvent t = (FileRenameEvent) obj;
			if(StringUtils.equals(this.group.getId(), t.getGroup().getId())
					&& StringUtils.equals(this.newFile.getAbsolutePath(), t.getNewFile().getAbsolutePath())
					&& StringUtils.equals(this.oldFile.getAbsolutePath(), t.getOldFile().getAbsolutePath())
					){
				return true;
			}
		}
		return false;
	}
	public void execute() {
		final GroupInfo group = this.getGroup();
		File oldFile = this.getOldFile();
		File newFile = this.getNewFile();
		String oldFilePath = FilenameUtils.normalize(oldFile.getAbsolutePath(), true);
		String newFilePath = FilenameUtils.normalize(newFile.getAbsolutePath(), true);
		String md5 = newFile.isFile()?FileMd5Utils.getFileMD5(newFile):null;
		if(md5 == null)md5 = "";

		final String oldFileGroupPath = FileInfoUtils.differencePath(group.getPath(), oldFilePath);
		final String newFileGroupPath = FileInfoUtils.differencePath(group.getPath(), newFilePath);

		// 如果文件的最后更新时间 和数据库中的一致，则停止更新
		FileInfo fileInfo = null;
		if(newFile.isFile()){
			fileInfo = FileManagerDao.getFileInfo(group, oldFileGroupPath);
			if(fileInfo == null){
				fileInfo = FileManagerDao.addFileInfo(group, newFilePath, md5, newFile.length(), newFile.lastModified());
			}
			FileManagerDao.renameFileInfo(group, oldFileGroupPath, newFileGroupPath, newFile.lastModified());
			fileInfo.setPath(newFileGroupPath);
			fileInfo.setLastModifyTime(newFile.lastModified());
		}

		List<MemberInfo> members = FileManagerDao.getGroupMembers(group);
		for (int i = 0; i < members.size(); i++) {
			final MemberInfo member = members.get(i);
			if (!StringUtils.equals(member.getOnline(), "Y")){
				FileManagerDao.addFileNotSyncLog(group, member, String.format("%s->%s", oldFilePath, newFilePath), eventType);
				FileSyncLog.debug("%s[EVENT]: 通知%s重命名文件%s->%s，member不在线，添加到未同步文件池", group.getGroupNo(), member.getMemberNo(), oldFileGroupPath, newFileGroupPath);
				continue;
			}
			
			Map<String, String> params = new HashMap<String, String>();
			String url = String.format("http://%s:%d/rsync/fileRenamed", member.getIp(), member.getPort());
			params.put("ip", FileSyncConfig.localIp);
			params.put("port", Integer.toString(FileSyncConfig.httpPort));
			params.put("fromGroupNo", group.getGroupNo());
			params.put("toGroupNo", member.getMemberNo());
			params.put("oldFilePath", oldFileGroupPath);
			params.put("newFilePath", newFileGroupPath);
			params.put("lastModifyTime", Long.toString(newFile.lastModified()));
			params.put("isFile", newFile.isFile()?"Y":"N");
			params.put("md5", md5);

			HttpClient.postAsync(url, params, new Callback() {
				@Override
				public void onResponse(Call call, Response response) throws IOException {
					String result = response.body().string();
					if("Y".equals(result)){
						FileSyncLog.info("%s[EVENT]: 通知%s重命名文件%s->%s完成", group.getGroupNo(), member.getMemberNo(), oldFileGroupPath, newFileGroupPath);
					}else{
						FileSyncLog.info("%s[EVENT]: 通知%s重命名文件%s->%s返回异常，稍後重试", group.getGroupNo(), member.getMemberNo(), oldFileGroupPath, newFileGroupPath);
						FileManagerDao.addFileNotSyncLog(group, member, String.format("%s->%s", oldFileGroupPath, newFileGroupPath), eventType);
					}
				}

				@Override
				public void onFailure(Call call, IOException e) {
					FileSyncLog.error(e, "%s[EVENT]: 通知%s重命名文件%s->%s异常，稍後重试", group.getGroupNo(), member.getMemberNo(), oldFileGroupPath, newFileGroupPath);
					FileManagerDao.addFileNotSyncLog(group, member, String.format("%s->%s", oldFileGroupPath, newFileGroupPath), eventType);
				}
			});
		}
	}
	@Override
	public String toString() {
		return String.format("gid=%s,file=%s->,event=%s", group.getGroupNo(), oldFile.getAbsolutePath(), newFile.getAbsolutePath(), "Modify");
	}
	@Override
	public Date getRecordTime() {
		return recordTime;
	}
	@Override
	public int getState() {
		return state;
	}

	@Override
	public void setState(int state) {
		this.state = state;
	}
	@Override
	public FileChangeType getEventType() {
		return eventType;
	}
}
