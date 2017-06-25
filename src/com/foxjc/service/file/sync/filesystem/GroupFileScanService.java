package com.foxjc.service.file.sync.filesystem;

import java.io.File;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.task.CompareGroupFileTask;

/**
 * 本地文件信息服务
 * 
 * @author 郭跃鹏 2016/8/26-下午4:06:27
 */
public class GroupFileScanService {

	/**
	 * 扫描文件列表
	 * 
	 * @param member
	 * @return 扫描文件列表
	 */
	public static void scanGroupFile(final GroupInfo group) {
		if (group == null)
			return;
		String path = group.getPath();
		final File dir = new File(path);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		FileManagerDao.updateGroupUnready(group);
		Thread scanFileThread = new Thread(new Runnable() {
			public void run() {
				long s = System.currentTimeMillis();
				//扫描文件
				int fileCount = 0;
				FileManagerDao.lockAllFileInfo(group);
				FileIterator iterator = new FileIterator(dir);
				while (!Thread.interrupted() && iterator.moveNext()) {
					fileCount ++;
					File file = iterator.getFile();
					try {
						recordFileInfo(group, file);
					} catch (Exception e) {
						FileSyncLog.error(e, "%s: 扫描文件%s异常", group.getGroupNo(), file.getAbsolutePath());
					}
				}
				long e = System.currentTimeMillis();
				FileSyncLog.info("%s: 扫描%s文件夹，共计%d文件，耗时%d毫秒", group.getGroupNo(), dir.getAbsolutePath(), fileCount, e - s);
				int delFileCount = FileManagerDao.removeAllDeletedFileInfo();
				if(delFileCount > 0)FileSyncLog.info("%s: 删除无效文件信息removeAllDeletedFileInfo=%s个", group.getGroupNo(), delFileCount);
				int delLockedFileCount = FileManagerDao.removeAllLockedFileInfo();
				if(delLockedFileCount > 0)FileSyncLog.info("%s: 删除无效文件信息removeAllLockedFileInfo=%s个", group.getGroupNo(), delLockedFileCount);
				int distinctCount = FileManagerDao.distinctFileInfo(group);
				if(distinctCount > 0)FileSyncLog.info("%s: 删除重复文件信息distinctFileInfo=%s个", group.getGroupNo(), distinctCount);
				
				//协调双方对比服务
				int delay = RandomUtils.nextInt(200, 1000);
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e1) {
					FileSyncLog.debug("%s: 延迟等待%s毫秒执行文件对比服务异常", group.getGroupNo(), delay);
				}
				FileManagerDao.updateGroupReady(group);
				CompareGroupFileTask.startCompareGroupAllMember(group);
			}
		});
		scanFileThread.setName(String.format("scan-group-%s-file-thread", group.getGroupNo()));
		scanFileThread.start();
		FileSyncLog.info("%s: 启动线程扫描%s文件列表", group.getGroupNo(), group.getPath());
	}
	private  static void recordFileInfo(GroupInfo group, File file) {
		long fileLength = file.length();
		long lastModifyTime = file.lastModified();
		String filePath = FilenameUtils.normalize(file.getAbsolutePath(), true);
		filePath = FileInfoUtils.differencePath(group.getPath(), filePath);
		FileInfo fileInfo = FileManagerDao.getFileInfo(group, filePath);
		
		if(file.exists() && file.length() > 0 && lastModifyTime == 0){
			file.setLastModified(System.currentTimeMillis());
			lastModifyTime = file.lastModified();
		}
		
		if(fileInfo == null){
			//新增文件信息
			String md5 = FileMd5Utils.getFileMD5(file);
			if(md5 == null)md5 = "";
			fileInfo = FileManagerDao.addFileInfo(group, filePath, md5, fileLength, lastModifyTime);
			FileSyncLog.info("%s: 扫描到新文件%s", group.getGroupNo(), filePath);
		}else{
			FileManagerDao.unlockFileInfo(fileInfo);
			if(fileLength == fileInfo.getLength() && lastModifyTime == fileInfo.getLastModifyTime()){
				return;
			}
			String md5 = FileMd5Utils.getFileMD5(file);
			if(md5 == null)md5 = "";
			if(StringUtils.equals(fileInfo.getMd5(), md5)
					&& lastModifyTime == fileInfo.getLastModifyTime()
					&& fileLength == fileInfo.getLength()){
				return;
			}
			FileManagerDao.updateFileMd5(fileInfo.getId(), md5, fileLength, lastModifyTime);
			FileSyncLog.info("%s: 更新文件信息%s", group.getGroupNo(), filePath);
		}
	}
	
}