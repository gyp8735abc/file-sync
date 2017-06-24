package com.foxjc.service.file.sync.http.action;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.FileEventHandler;
import com.foxjc.service.file.sync.filesystem.FileMd5Utils;
import com.foxjc.service.file.sync.filesystem.IoUtils;
import com.foxjc.service.file.sync.filesystem.event.FileCreateEvent;
import com.foxjc.service.file.sync.filesystem.event.FileModifiyEvent;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

/**
 * 更新文件
 * 
 * @author 郭跃鹏
 * 
 */
public class FileModifiedAction implements RequestHandler {

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		int length = request.getBodyLength();
		String fromGroupNo = request.getParamter("fromGroupNo");
		String toGroupNo = request.getParamter("toGroupNo");
		String filePath = request.getParamter("filePath");
		String md5 = request.getParamter("md5");
		long lastModifyTime = NumberUtils.toLong(request.getParamter("lastModifyTime"));
		long fileLength = NumberUtils.toLong(request.getParamter("fileLength"));

		GroupInfo group = FileManagerDao.getGroupInfoByNo(toGroupNo);
		String basePath = group.getPath();
		File file = new File(basePath + filePath);
		File parent = file.getParentFile();
		// 更新文件的文件夹不存在，则不需要执行更新
		if (!parent.exists()) {
			parent.mkdirs();
		}
		// 更新文件信息
		FileInfo fileInfo = FileManagerDao.getFileInfo(group, filePath);
		if (fileInfo == null) {
			String localFileMd5 = file.exists() ? FileMd5Utils.getFileMD5(file) : null;
			if(localFileMd5 == null)localFileMd5 = "";
			fileInfo = FileManagerDao.addFileInfo(group, filePath, localFileMd5, file.isDirectory() ? 0 : file.length(), file.lastModified());
		}
		
		if(StringUtils.equals(fileInfo.getMd5(), md5) && lastModifyTime == fileInfo.getLastModifyTime()
				&& fileLength == fileInfo.getLength()){
			response.writeMessage("Y");
			return;
		}

		// 增加更新推送阻止事件
		OutputStream os = null;
		InputStream is = null;
		FileCreateEvent fileCreateEvent = new FileCreateEvent(group, file);
		FileModifiyEvent fileModifiyEvent = new FileModifiyEvent(group, file);
		FileEventHandler.addInhibitionEvent(fileModifiyEvent);
		FileEventHandler.addInhibitionEvent(fileCreateEvent);
		try {
			if (StringUtils.equals(fileInfo.getMd5(), md5)) {
				// 文件内容相同，只是修改属性
				file.setLastModified(lastModifyTime);
			} else {
				os = new FileOutputStream(file);
				is = request.getRequestBody();
				byte[] buffer = new byte[1024 * 16];
				long copyCount = 0;
				while(copyCount< length){
					copyCount+=IOUtils.copyLarge(is, os, 0, length, buffer);
				}
				IoUtils.close(os);
				file.setLastModified(lastModifyTime);
				// 在创建文件时，已生成文件记录，但是md5码是空，现在需要更新文件信息
				FileManagerDao.updateFileMd5(fileInfo.getId(), md5, fileLength, lastModifyTime);
			}
			FileSyncLog.info("ACTION: %s->%s修改文件%s, 最后更新时间：%tc", fromGroupNo, group.getGroupNo(), filePath, new Date(lastModifyTime));
			response.writeMessage("Y");
		} catch (IOException e) {
			FileSyncLog.error(e, "ACTION: %s->%s修改文件异常: %s", fromGroupNo, group.getGroupNo(), file.getAbsolutePath());
			response.writeMessage("N");
		} finally {
			IoUtils.close(is);
			IoUtils.close(os);
			FileEventHandler.removeInhibitionEvent(fileCreateEvent);
			FileEventHandler.removeInhibitionEvent(fileModifiyEvent);
		}
	}

}