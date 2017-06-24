package com.foxjc.service.file.sync.filesystem.task;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileChangeType;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.FileNonSyncLog;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.bean.MemberInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.db.H2Utils;
import com.foxjc.service.file.sync.filesystem.FileEventHandler;
import com.foxjc.service.file.sync.filesystem.FileMd5Utils;
import com.foxjc.service.file.sync.filesystem.event.FileDeleteEvent;
import com.foxjc.service.file.sync.filesystem.event.FileRenameEvent;
import com.foxjc.service.file.sync.http.HttpServiceUtils;

/**
 * 同步未同步的group file
 * 
 * @author 郭跃鹏
 * 
 */
public class SyncGroupFileTask implements Runnable {

	@Override
	public void run() {
		String sql = "SELECT top 50 * FROM GROUP_FILE_NON_SYNC where del = 0 and add_time < ?";
		try {
			while (!Thread.interrupted()) {
				//循环获取文件同步信息，每次50条，直到没有需要同步的信息
				List<FileNonSyncLog> list = H2Utils.queryBeanList(sql, FileNonSyncLog.class, System.currentTimeMillis() - 1500);
				if (list == null || list.size() == 0)
					break;

				for (int i = 0; i < list.size(); i++) {
					FileNonSyncLog log = list.get(i);
					String id = log.getId();
					String filePath = log.getFilePath();
					String memberId = log.getMemberId();
					int eventType = log.getEventType();

					MemberInfo member = FileManagerDao.getMemberInfoById(memberId);
					GroupInfo group = FileManagerDao.getGroupInfoById(member.getGroupId());
					FileInfo fileInfo = FileManagerDao.getFileInfo(group, filePath);
					if ("N".equals(member.getOnline())) {
						FileSyncLog.debug("文件同步终止 group=%s, member=%s不在线，稍後重试", group.getGroupNo(), member.getMemberNo());
						continue;
					}
					File file = new File(group.getPath() + filePath);
					if (fileInfo == null) {
						if (file.exists()) {
							String md5 = FileMd5Utils.getFileMD5(file);
							if (md5 == null)
								md5 = "";
							fileInfo = FileManagerDao.addFileInfo(group, filePath, md5, file.length(), file.lastModified());
						} else {
							// 如果需要同步的文件不存在了，则删除文件同步信息
							FileSyncLog.debug("文件信息 group=%s, file=%s不存在，终止文件同步", group.getGroupNo(), filePath);
							// TO DO 有需要同步的事件，说明原文件存在，现不存在了，说明後来被删除，所以此种情况等同於删除文件
							eventType = FileChangeType.DELETE.getValue();
						}
					} else {
						// 如果文件信息存在
						if (file.exists()) {
							if (file.lastModified() != fileInfo.getLastModifyTime()) {
								// 文件信息有变，需要更新文件信息
								String md5 = FileMd5Utils.getFileMD5(file);
								if (md5 == null)
									md5 = "";
								FileManagerDao.updateFileMd5(fileInfo.getId(), md5, file.length(), file.lastModified());
								fileInfo = FileManagerDao.getFileInfoById(fileInfo.getId());
							}
						} else {
							FileManagerDao.markDeleteFileInfo(fileInfo);
							FileSyncLog.debug("文件信息 group=%s, file=%s不存在，终止文件同步", group.getGroupNo(), filePath);
							// TO DO 未实现@文件信息存在，现文件被删除了，则需要比对两边的文件进行同步文件
							eventType = FileChangeType.DELETE.getValue();
						}
					}

					if (eventType == FileChangeType.CREATE.getValue()) {
						// 忽略
						FileManagerDao.markFileNotSyncRecordForDel(id);
					} else if (eventType == FileChangeType.MODIFY.getValue()) {
						if (HttpServiceUtils.compareFile(group, member, fileInfo, false)) {
							FileManagerDao.markFileNotSyncRecordForDel(id);
						}
					} else if (eventType == FileChangeType.DELETE.getValue()) {
						FileDeleteEvent event = new FileDeleteEvent(group, new File(group.getPath() + filePath));
						FileEventHandler.push(event);
						FileManagerDao.markFileNotSyncRecordForDel(id);
					} else if (eventType == FileChangeType.RENAME.getValue()) {
						String[] paths = StringUtils.split(filePath, "->");
						FileRenameEvent event = new FileRenameEvent(group, new File(group.getPath() + paths[0]), new File(group.getPath() + paths[1]));
						FileEventHandler.push(event);
						FileManagerDao.markFileNotSyncRecordForDel(id);
					}
				}
				FileManagerDao.removeExpiredNotSyncRecord();
			}
		} catch (Exception e) {
			FileSyncLog.error(e, "同步文件任务异常");
		} finally {
			// IoUtils.close(conn);
		}
	}

}
