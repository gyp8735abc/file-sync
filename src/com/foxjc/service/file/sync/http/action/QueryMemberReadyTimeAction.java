package com.foxjc.service.file.sync.http.action;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

/**
 * 对比文件
 * 
 * @author 郭跃鹏 2016/8/25-上午9:04:47
 */
public class QueryMemberReadyTimeAction implements RequestHandler {

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		String fromGroupNo = request.getParamter("fromGroupNo");
		String toGroupNo = request.getParamter("toGroupNo");
		
		try {
			final GroupInfo group = FileManagerDao.getGroupInfoByNo(toGroupNo);
			if (group == null) {
				FileSyncLog.error("没有找到叫%s的Group，无法获取MemberReadyTime", toGroupNo);
				response.writeMessage("ERROR");
				return;
			}
			long time = FileManagerDao.getGroupReadyTime(group);
			response.writeMessage(Long.toString(time));
		} catch (Exception e) {
			response.writeMessage("ERROR");
			FileSyncLog.error(e, "ACTION: %s->%s获取MemberReadyTime异常: %s", fromGroupNo, toGroupNo);
		}
	}

}