package com.foxjc.service.file.sync.http.action;

import java.io.File;

import org.apache.commons.io.FileUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.FileEventHandler;
import com.foxjc.service.file.sync.filesystem.FileInfoUtils;
import com.foxjc.service.file.sync.filesystem.event.FileDeleteEvent;
import com.foxjc.service.file.sync.filesystem.event.FileModifiyEvent;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

public class FileDeletedAction implements RequestHandler{

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		String fromGroupNo = request.getParamter("fromGroupNo");
		String toGroupNo = request.getParamter("toGroupNo");
		String filePath = request.getParamter("filePath");
		
		GroupInfo group = FileManagerDao.getGroupInfoByNo(toGroupNo);
		String basePath = group.getPath();
		File file = FileInfoUtils.getFile(basePath, filePath);
		
		FileModifiyEvent fileModifiyEvent = new FileModifiyEvent(group, file);
		FileDeleteEvent fileDeleteEvent = new FileDeleteEvent(group, file);
		try {
			if(!file.exists()){
				response.writeMessage("Y");
				return;
			}
			FileEventHandler.addInhibitionEvent(fileModifiyEvent);
			FileEventHandler.addInhibitionEvent(fileDeleteEvent);
			
			if(file.isFile()){
				FileInfo fileInfo = FileManagerDao.getFileInfo(group, filePath);
				if(fileInfo != null){
					FileManagerDao.markDeleteFileInfo(fileInfo);
				}
				FileUtils.deleteQuietly(file);
			}else{
				FileUtils.deleteDirectory(file);
			}
			
			FileSyncLog.info("ACTION: %s->%s删除文件%s", fromGroupNo, group.getGroupNo(), filePath);
			response.writeMessage("Y");
		} catch (Exception e) {
			FileSyncLog.error(e, "ACTION: %s->%s删除文件异常: %s", fromGroupNo, group.getGroupNo(), file.getAbsolutePath());
			response.writeMessage("N");
		} finally {
			FileEventHandler.removeInhibitionEvent(fileModifiyEvent);
			FileEventHandler.removeInhibitionEvent(fileDeleteEvent);
		}
	}

}