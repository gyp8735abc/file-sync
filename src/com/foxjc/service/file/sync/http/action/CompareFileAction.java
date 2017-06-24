package com.foxjc.service.file.sync.http.action;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.bean.MemberInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.FileMd5Utils;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

/**
 * 对比文件
 * 
 * @author 郭跃鹏 2016/8/25-上午9:04:47
 */
public class CompareFileAction implements RequestHandler {

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		String fromGroupNo = request.getParamter("fromGroupNo");
		String toGroupNo = request.getParamter("toGroupNo");
		String filePath = request.getParamter("filePath");
		String unlockFile = request.getParamter("unlockFile");
		String md5 = request.getParamter("md5");
		long lastModifyTime = NumberUtils.toLong(request.getParamter("lastModifyTime"));
		long fileLength = NumberUtils.toLong(request.getParamter("fileLength"));
		
		try {
			final GroupInfo group = FileManagerDao.getGroupInfoByNo(toGroupNo);
			if (group == null) {
				FileSyncLog.error("没有找到叫%s的Group，无法同步文件", toGroupNo);
				response.writeMessage("ERROR");
				return;
			}
			final MemberInfo fromMember = FileManagerDao.getGroupMember(group, fromGroupNo);
			if (fromMember == null) {
				FileSyncLog.error("group=%s,没有找到叫%s的member，无法同步文件", group.getGroupNo(), fromGroupNo);
				response.writeMessage("ERROR");
				return;
			}
			FileInfo fileInfo = FileManagerDao.getFileInfo(group, filePath);
			File file = new File(group.getPath()+filePath);
			if(fileInfo == null){
				String localFileMd5 = FileMd5Utils.getFileMD5(file);
				if(localFileMd5 == null)localFileMd5 = "";
				fileInfo = FileManagerDao.addFileInfo(group, filePath, localFileMd5, file.length(), file.exists()? file.lastModified(): 0);
			}
			
			FileManagerDao.removeNotSyncRecordByPath(fromMember, filePath);
			String result = "NONE";
			if(StringUtils.equals(fileInfo.getMd5(), md5)
				&& lastModifyTime == fileInfo.getLastModifyTime()
				&& fileLength == fileInfo.getLength()){
				result = "NONE";//无需更新
			}
			if(lastModifyTime > fileInfo.getLastModifyTime()){
				result = "PUSH";
			}else if(lastModifyTime < fileInfo.getLastModifyTime()){
				result = "PULL";
			}
			if(!file.exists() || file.length() == 0){
				result = "PUSH";
			}else if(fileLength == 0){
				result = "PULL";
			}
			response.writeMessage(result);
			if("Y".equals(unlockFile)){
				FileManagerDao.unlockFileInfo(fileInfo);
			}
		} catch (Exception e) {
			response.writeMessage("ERROR");
			FileSyncLog.error(e, "ACTION: %s->%s对比文件异常: %s", fromGroupNo, toGroupNo, filePath);
		}
	}

}