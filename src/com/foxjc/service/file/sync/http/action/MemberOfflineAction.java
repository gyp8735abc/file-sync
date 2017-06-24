package com.foxjc.service.file.sync.http.action;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

public class MemberOfflineAction implements RequestHandler{

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		String fromGroupNo = request.getParamter("fromGroupNo");
		String toGroupNo = request.getParamter("toGroupNo");
		//更新member的状态
		FileManagerDao.updateGroupMemberOfflineStatus(toGroupNo, fromGroupNo);
		FileSyncLog.info("发现%s离綫", fromGroupNo);
		response.writeMessage("Y");
	}
}
