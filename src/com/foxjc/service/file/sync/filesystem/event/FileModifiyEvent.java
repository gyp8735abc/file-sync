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

public class FileModifiyEvent implements FileEvent{
	private GroupInfo group; 
	private File file;
	private FileChangeType eventType = FileChangeType.MODIFY;
	private Date recordTime;
	private int state = 0;
	
	public FileModifiyEvent(GroupInfo group, File file){
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
		if(obj instanceof FileModifiyEvent){
			FileModifiyEvent t = (FileModifiyEvent) obj;
			if(StringUtils.equals(this.group.getId(), t.getGroup().getId())
					&& StringUtils.equals(this.file.getAbsolutePath(), t.getFile().getAbsolutePath())){
				return true;
			}
		}
		return false;
	}

	
	public void execute() {
		final GroupInfo group = this.getGroup();
		File file = this.getFile();
		String filePath = FilenameUtils.normalize(file.getAbsolutePath(), true);
		long lastModifyTime = file.lastModified();
		long fileLength = file.isDirectory() ? 0 : file.length();
		String md5 = null;
		
		final String fileGroupPath = FileInfoUtils.differencePath(group.getPath(), filePath);
		if(!file.exists() || file.length() == 0){
			FileManagerDao.addFileNotSyncLogForAllMember(group, fileGroupPath, eventType);
			FileSyncLog.debug("%s[EVENT]: 修改文件%s，文件大小为0，添加到未同步文件池", group.getGroupNo(), filePath);
			return ;
		}
		FileInfo fileInfo = FileManagerDao.getFileInfo(group, fileGroupPath);
		if(fileInfo == null){
			fileInfo = FileManagerDao.addFileInfo(group, fileGroupPath, md5, fileLength, lastModifyTime);
		}
		try {
			md5 = file.isFile()?FileMd5Utils.getFileMD5(file):null;
			if(md5 == null)md5 = "";
		} catch (Exception e1) {
			FileSyncLog.info("%s[EVENT]: 文件%s修改通知: 获取md5异常，文件可能被占用，同步文件服务取消，添加到未同步文件池", group.getGroupNo(), filePath);
			FileManagerDao.addFileNotSyncLogForAllMember(group, fileGroupPath, eventType);
			return;
		}

		// 如果文件的最后更新时间 和数据库中的一致，则停止更新
		if(!StringUtils.equals(fileInfo.getMd5(), md5)){
			FileManagerDao.updateFileMd5(fileInfo.getId(), md5, fileLength, lastModifyTime);
			fileInfo.setMd5(md5);
			fileInfo.setLength(fileLength);
			fileInfo.setLastModifyTime(lastModifyTime);
		}

		List<MemberInfo> members = FileManagerDao.getGroupMembers(group);
		for (int i = 0; i < members.size(); i++) {
			final MemberInfo member = members.get(i);
			if (!StringUtils.equals(member.getOnline(), "Y")){
				FileManagerDao.addFileNotSyncLog(group, member, fileGroupPath, eventType);
				FileSyncLog.debug("%s[EVENT]: 通知%s修改文件%s，member不在线，添加到未同步文件池", group.getGroupNo(), member.getMemberNo(), filePath);
				continue;
			}
			
			Map<String, String> params = new HashMap<String, String>();
			String url = String.format("http://%s:%d/rsync/fileModified", member.getIp(), member.getPort());
			params.put("ip", FileSyncConfig.localIp);
			params.put("port", Long.toString(FileSyncConfig.httpPort));
			params.put("fromGroupNo", group.getGroupNo());
			params.put("toGroupNo", member.getMemberNo());
			params.put("filePath", fileGroupPath);
			params.put("lastModifyTime", Long.toString(lastModifyTime));
			params.put("fileLength", Long.toString(fileLength));
			params.put("isFile", file.isFile()?"Y":"N");
			params.put("md5", md5);
			params.put("clearFile", Boolean.toString(fileLength == 0));

			try {
				HttpClient.file(url, params, file, new Callback() {
					@Override
					public void onResponse(Call call, Response response) throws IOException {
						String result = response.body().string();
						if("Y".equals(result)){
							FileSyncLog.info("%s[EVENT]: 通知%s修改文件%s完成", group.getGroupNo(), member.getMemberNo(), fileGroupPath);
						}else{
							FileSyncLog.info("%s[EVENT]: 通知%s修改文件%s返回异常，稍後重试", group.getGroupNo(), member.getMemberNo(), fileGroupPath);
							FileManagerDao.addFileNotSyncLog(group, member, fileGroupPath, eventType);
						}
					}

					@Override
					public void onFailure(Call call, IOException e) {
						FileSyncLog.error(e, "%s[EVENT]: 通知%s修改文件%s异常，稍後重试", group.getGroupNo(), member.getMemberNo(), fileGroupPath);
						FileManagerDao.addFileNotSyncLog(group, member, fileGroupPath, eventType);
					}
				});
			} catch (Exception e) {
				FileSyncLog.error(e, "%s[EVENT]: 通知%s修改文件%s异常，稍後重试", group.getGroupNo(), member.getMemberNo(), fileGroupPath);
				FileManagerDao.addFileNotSyncLog(group, member, fileGroupPath, eventType);
			}
		}
	}
	@Override
	public String toString() {
		return String.format("gid=%s,file=%s,event=%s", group.getGroupNo(), file.getAbsolutePath(), "Modify");
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
