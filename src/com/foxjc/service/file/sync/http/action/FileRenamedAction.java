
package com.foxjc.service.file.sync.http.action;

import java.io.File;

import org.apache.commons.lang3.math.NumberUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.FileEventHandler;
import com.foxjc.service.file.sync.filesystem.FileInfoUtils;
import com.foxjc.service.file.sync.filesystem.event.FileCreateEvent;
import com.foxjc.service.file.sync.filesystem.event.FileDeleteEvent;
import com.foxjc.service.file.sync.filesystem.event.FileModifiyEvent;
import com.foxjc.service.file.sync.filesystem.event.FileRenameEvent;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

public class FileRenamedAction implements RequestHandler{

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		String fromGroupNo = request.getParamter("fromGroupNo");
		String toGroupNo = request.getParamter("toGroupNo");
		String oldFilePath = request.getParamter("oldFilePath");
		String newFilePath = request.getParamter("newFilePath");
		String md5 = request.getParamter("md5");
		long lastModifyTime = NumberUtils.toLong(request.getParamter("lastModifyTime"));
		
		GroupInfo group = FileManagerDao.getGroupInfoByNo(toGroupNo);
		String basePath = group.getPath();
		File oldFile = FileInfoUtils.getFile(basePath, oldFilePath);
		File newFile = FileInfoUtils.getFile(basePath, newFilePath);
		if(!oldFile.exists())return;
		
		FileCreateEvent fileCreateEvent = new FileCreateEvent(group, newFile);
		FileModifiyEvent fileModifiyEvent1 = new FileModifiyEvent(group, oldFile);
		FileModifiyEvent fileModifiyEvent2 = new FileModifiyEvent(group, newFile);
		FileDeleteEvent fileDeleteEvent = new FileDeleteEvent(group, oldFile);
		FileRenameEvent fileRenameEvent = new FileRenameEvent(group, oldFile, newFile);
		
		FileEventHandler.addInhibitionEvent(fileRenameEvent);
		FileEventHandler.addInhibitionEvent(fileCreateEvent);
		FileEventHandler.addInhibitionEvent(fileModifiyEvent1);
		FileEventHandler.addInhibitionEvent(fileModifiyEvent2);
		FileEventHandler.addInhibitionEvent(fileDeleteEvent);
		try {
			if(oldFile.isDirectory()){
				//文件夹重命名
				oldFile.renameTo(newFile);
				FileSyncLog.info("ACTION: %s->%s重命名文件%s->%s", fromGroupNo, group.getGroupNo(), oldFilePath, newFilePath);
				response.writeMessage("Y");
				return;
			}
			
			FileInfo fileInfo = FileManagerDao.getFileInfo(group, oldFilePath);
			if(fileInfo == null){
				fileInfo = FileManagerDao.addFileInfo(group, newFilePath, md5, oldFile.isDirectory() ? 0 : oldFile.length(), oldFile.lastModified());
			}
			
			oldFile.renameTo(newFile);
			oldFile.setLastModified(lastModifyTime);

			FileSyncLog.info("ACTION: %s->%s重命名文件%s->%s", fromGroupNo, group.getGroupNo(), oldFilePath, newFilePath);
			response.writeMessage("Y");
		} catch (Exception e) {
			FileEventHandler.removeInhibitionEvent(fileRenameEvent);
			FileEventHandler.removeInhibitionEvent(fileCreateEvent);
			FileEventHandler.removeInhibitionEvent(fileModifiyEvent1);
			FileEventHandler.removeInhibitionEvent(fileModifiyEvent2);
			FileEventHandler.removeInhibitionEvent(fileDeleteEvent);
			FileSyncLog.error(e, "ACTION: %s->%s重命名文件异常: %s", fromGroupNo, group.getGroupNo(), oldFile.getAbsolutePath());
			response.writeMessage("N");
		}
	}

}