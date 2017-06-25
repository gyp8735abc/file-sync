package com.foxjc.service.file.sync.http.action;

import java.io.File;
import java.util.Objects;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.FileMd5Utils;
import com.foxjc.service.file.sync.filesystem.IoUtils;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

/**
 * 接受member发送过来的文件信息 如果文件md5和lastModify一致，则更新直接返回，不做修改
 * 
 * @author 郭跃鹏
 * 
 */
public class PullFileAction implements RequestHandler {

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		try {
			String fromGroupNo = request.getParamter("fromGroupNo");
			String toGroupNo = request.getParamter("toGroupNo");
			String filePath = request.getParamter("filePath");

			GroupInfo group = FileManagerDao.getGroupInfoByNo(toGroupNo);
			if (group == null) {
				FileSyncLog.error("%s[ACTION]: 没有找到叫%s的Group，无法同步文件", toGroupNo, toGroupNo);
				response.writeMessage(500, "需要抽取文件的组" + toGroupNo + "不存在");
				return;
			}
			String basePath = group.getPath();
			File file = new File(basePath + filePath);
			if (!file.exists()) {
				FileSyncLog.error("%s[ACTION]: 响应%s，file=%s不存在，无法同步文件", toGroupNo, fromGroupNo, file.getAbsolutePath());
				response.writeMessage(500, String.format("组%s文件%s没有找到", fromGroupNo, filePath));
				return;
			}

			FileInfo fileInfo = FileManagerDao.getFileInfo(group, filePath);
			// 如果md5和lastModify一致，则取消更新
			if (fileInfo == null) {
				String md5 = FileMd5Utils.getFileMD5(file);
				if(md5 == null)md5 = "";
				fileInfo = FileManagerDao.addFileInfo(group, filePath, md5, file.length(), file.lastModified());
			}
			BufferedSink sink = null;
			BufferedSource source = null;
			try {
				response.setHeader("lastModified", Objects.toString(fileInfo.getLastModifyTime()));
				response.setHeader("md5", fileInfo.getMd5());
				
				long fileLength = file.length();
				response.setBodyLength((int)fileLength);
				source = Okio.buffer(Okio.source(file));
				sink = Okio.buffer(Okio.sink(response.getOutputStream()));
				source.readAll(sink);
				sink.flush();
			} catch (Exception e) {
				FileSyncLog.error(e, "%s[ACTION]: 响应%s抽取文件%s异常", toGroupNo, fromGroupNo, file.getAbsolutePath());
				response.writeMessage(500, "抽取文件异常: "+e.getMessage());
			} finally {
				IoUtils.close(source);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}