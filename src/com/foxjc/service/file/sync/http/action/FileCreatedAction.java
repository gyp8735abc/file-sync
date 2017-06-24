package com.foxjc.service.file.sync.http.action;

import java.io.File;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.FileEventHandler;
import com.foxjc.service.file.sync.filesystem.FileInfoUtils;
import com.foxjc.service.file.sync.filesystem.event.FileCreateEvent;
import com.foxjc.service.file.sync.filesystem.event.FileModifiyEvent;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

/**
 * 先更新数据库
 * 再更新文件
 * @author 郭跃鹏
 *
 */
public class FileCreatedAction implements RequestHandler{

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		String fromGroupNo = request.getParamter("fromGroupNo");
		String toGroupNo = request.getParamter("toGroupNo");
		String filePath = request.getParamter("filePath");
		boolean isFile = StringUtils.equals(request.getParamter("isFile"), "Y");
		String md5 = request.getParamter("md5");
		long lastModifyTime = NumberUtils.toLong(request.getParamter("lastModifyTime"));
		long fileLength = NumberUtils.toLong(request.getParamter("fileLength"));
		
		GroupInfo group = FileManagerDao.getGroupInfoByNo(toGroupNo);
		String basePath = group.getPath();
		File file = FileInfoUtils.getFile(basePath, filePath);
		
		FileCreateEvent fileCreateEvent = new FileCreateEvent(group, file);
		FileModifiyEvent fileModifiyEvent = new FileModifiyEvent(group, file);
		FileEventHandler.addInhibitionEvent(fileCreateEvent);
		FileEventHandler.addInhibitionEvent(fileModifiyEvent);
		
		try {
			FileInfo fileInfo = FileManagerDao.getFileInfo(group, filePath);
			if(fileInfo == null){
				fileInfo = FileManagerDao.addFileInfo(group, filePath, md5, fileLength, lastModifyTime);
			}
			File parent = file.getParentFile();
			if(!parent.exists()){
				String parentPath = FileInfoUtils.differencePath(FilenameUtils.normalize(parent.getAbsolutePath()), group.getPath());
				fileInfo = FileManagerDao.getFileInfo(group, parentPath);
				if(fileInfo == null){
					fileInfo = FileManagerDao.addFileInfo(group, parentPath, null, 0, System.currentTimeMillis());
				}
				parent.mkdirs();
			}
			if(!file.exists() && isFile){
				FileUtils.touch(file);
				file.setLastModified(lastModifyTime);
				FileSyncLog.info("ACTION: %s->%s新建文件%s, 最后更新时间：%tc", fromGroupNo, group.getGroupNo(), filePath, new Date(lastModifyTime));
			}
			
			response.writeMessage("Y");
		} catch (Exception e) {
			FileSyncLog.error(e, "ACTION: %s->%s建立文件异常: %s", fromGroupNo, group.getGroupNo(), file.getAbsolutePath());
			response.writeMessage("N");
		} finally {
			FileEventHandler.removeInhibitionEvent(fileCreateEvent);
			FileEventHandler.removeInhibitionEvent(fileModifiyEvent);
		}
	}

}