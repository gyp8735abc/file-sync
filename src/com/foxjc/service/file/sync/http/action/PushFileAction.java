package com.foxjc.service.file.sync.http.action;

import java.io.File;
import java.util.Date;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import org.apache.commons.lang3.math.NumberUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.FileEventHandler;
import com.foxjc.service.file.sync.filesystem.IoUtils;
import com.foxjc.service.file.sync.filesystem.event.FileCreateEvent;
import com.foxjc.service.file.sync.filesystem.event.FileModifiyEvent;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

/**
 * 接受member发送过来的文件信息 如果文件md5和lastModify一致，则更新直接返回，不做修改
 * 
 * @author 郭跃鹏
 * 
 */
public class PushFileAction implements RequestHandler {

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		try{
		String fromGroupNo = request.getParamter("fromGroupNo");
		String toGroupNo = request.getParamter("toGroupNo");
		String filePath = request.getParamter("filePath");
		String md5 = request.getParamter("md5");
		// String clearFile = request.getParamter("clearFile");
		long lastModifyTime = NumberUtils.toLong(request.getParamter("lastModifyTime"));
		long fileLength = NumberUtils.toLong(request.getParamter("fileLength"));

		GroupInfo group = FileManagerDao.getGroupInfoByNo(toGroupNo);
		if(group == null){
			FileSyncLog.error("没有找到叫%s的Group，无法同步文件", toGroupNo);
			response.writeMessage("ERROR");
			return;
		}
		String basePath = group.getPath();
		File file = new File(basePath + filePath);
		File parent = file.getParentFile();
		if (!parent.exists()) {
			// 如果父目录不存在，则创建
			parent.mkdirs();
		}

		FileInfo fileInfo = FileManagerDao.getFileInfo(group, filePath);
		// 加入抑制事件，防止事件回传
		if(fileInfo == null){
			fileInfo = FileManagerDao.addFileInfo(group, filePath, md5, fileLength, lastModifyTime);
		}else{
			FileManagerDao.updateFileInfo(group, filePath, md5, fileLength, lastModifyTime);
		}
		
		BufferedSink sink = null;
		BufferedSource source = null;
		FileCreateEvent fileCreateEvent = new FileCreateEvent(group, file);
		FileModifiyEvent fileModifiyEvent = new FileModifiyEvent(group, file);
		try {
			FileEventHandler.addInhibitionEvent(fileModifiyEvent);
			FileEventHandler.addInhibitionEvent(fileCreateEvent);
			
			sink = Okio.buffer(Okio.sink(file));
			source = Okio.buffer(Okio.source(request.getRequestBody()));
			source.readAll(sink);
			FileSyncLog.info("同步文件%s->%s同步文件%s, 最后更新时间：%tc", fromGroupNo, group.getGroupNo(), filePath, new Date(lastModifyTime));
			response.writeMessage("Y");
		} catch (Exception e) {
			FileSyncLog.error(e, "%s->%s同步文件异常: %s", fromGroupNo, group.getGroupNo(), file.getAbsolutePath());
			response.writeMessage("ERROR");
		} finally {
			IoUtils.close(sink);
			
			FileEventHandler.removeInhibitionEvent(fileCreateEvent);
			FileEventHandler.removeInhibitionEvent(fileModifiyEvent);
		}
		
		file.setLastModified(lastModifyTime);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

}