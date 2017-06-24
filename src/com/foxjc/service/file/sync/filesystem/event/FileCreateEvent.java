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
import com.foxjc.service.file.sync.http.HttpClient;

public class FileCreateEvent implements FileEvent {
	private GroupInfo group;
	private File file;
	private FileChangeType eventType = FileChangeType.CREATE;
	private Date recordTime;
	private int state = 0;

	public FileCreateEvent(GroupInfo group, File file) {
		this.group = group;
		this.file = file;
		recordTime = new Date();
	}

	public GroupInfo getGroup() {
		return group;
	}

	public void setGroup(GroupInfo group) {
		this.group = group;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof FileCreateEvent) {
			FileCreateEvent t = (FileCreateEvent) obj;
			if (StringUtils.equals(this.group.getId(), t.getGroup().getId())
					&& StringUtils.equals(this.file.getAbsolutePath(), t.getFile().getAbsolutePath())) {
				return true;
			}
		}
		return false;
	}

	public void execute() {
		final GroupInfo group = this.getGroup();
		File file = this.getFile();
		String filePath = FilenameUtils.normalize(file.getAbsolutePath(), true);
		String md5 = null;

		if (!file.exists()) {
			FileSyncLog.info("文件%s不存在，无法执行fileCreated操作", filePath);
			return;
		}
		final String fileGroupPath = FileInfoUtils.differencePath(group.getPath(), filePath);
		long lastModifyTime = file.lastModified();
		long fileLength = file.isDirectory() ? 0 : file.length();

		FileInfo fileInfo = FileManagerDao.getFileInfo(group, fileGroupPath);
		if(fileInfo != null){
			FileManagerDao.removeFileInfo(fileInfo);
		}
		fileInfo = FileManagerDao.addFileInfo(group, fileGroupPath, md5, 0, lastModifyTime);

		List<MemberInfo> members = FileManagerDao.getGroupMembers(group);
		for (int i = 0; i < members.size(); i++) {
			final MemberInfo member = members.get(i);
			if (!StringUtils.equals(member.getOnline(), "Y")){
				FileManagerDao.addFileNotSyncLog(group, member, fileGroupPath, eventType);
				FileSyncLog.debug("EVENT: %s->%s新建文件%s，member不在线，添加到未同步文件池", group.getGroupNo(), member.getMemberNo(), filePath);
				continue;
			}

			Map<String, String> params = new HashMap<String, String>();
			String url = String.format("http://%s:%d/rsync/fileCreated", member.getIp(), member.getPort());
			params.put("ip", FileSyncConfig.localIp);
			params.put("port", FileSyncConfig.httpPort+"");
			params.put("fromGroupNo", group.getGroupNo());
			params.put("toGroupNo", member.getMemberNo());
			params.put("lastModifyTime", lastModifyTime+"");
			params.put("filePath", fileGroupPath);
			params.put("isFile", file.isFile()?"Y":"N");
			params.put("fileLength", fileLength+"");
			params.put("md5", md5);
			
			HttpClient.postAsync(url, params, new Callback() {
				@Override
				public void onResponse(Call call, Response response) throws IOException {
					String result = response.body().string();
					if("Y".equals(result)){
						FileSyncLog.info("EVENT: %s->%s新建文件%s完成", group.getGroupNo(), member.getMemberNo(), fileGroupPath);
					}else{
						FileSyncLog.info("EVENT: %s->%s新建文件%s返回异常，稍後重试", group.getGroupNo(), member.getMemberNo(), fileGroupPath);
						FileManagerDao.addFileNotSyncLog(group, member, fileGroupPath, eventType);
					}
				}

				@Override
				public void onFailure(Call call, IOException e) {
					FileSyncLog.error(e, "EVENT: %s->%s新建文件%s异常，稍後重试", group.getGroupNo(), member.getMemberNo(), fileGroupPath);
					FileManagerDao.addFileNotSyncLog(group, member, fileGroupPath, eventType);
				}
			});
		}
	}

	@Override
	public String toString() {
		return String.format("gid=%s,file=%s,event=%s", group.getGroupNo(), file.getAbsolutePath(), "Create");
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
