package com.foxjc.service.file.sync.http.action;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.bean.MemberInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.db.H2Utils;
import com.foxjc.service.file.sync.filesystem.task.CompareGroupFileTask;
import com.foxjc.service.file.sync.http.HttpServiceUtils;
import com.foxjc.service.file.sync.http.server.HttpRequest;
import com.foxjc.service.file.sync.http.server.HttpResponse;
import com.foxjc.service.file.sync.http.server.RequestHandler;

/**
 * 文件组事件变更通知
 * @author 郭跃鹏
 *
 */
public class GroupEventChangeAction implements RequestHandler {

	@Override
	public void handle(HttpRequest request, HttpResponse response) {
		String fromGroupNo = request.getParamter("fromGroupNo");
		String toGroupNo = request.getParamter("toGroupNo");
		String eventType = request.getParamter("eventType");
		
		GroupInfo group = FileManagerDao.getGroupInfoByNo(toGroupNo);
		MemberInfo member = FileManagerDao.getGroupMember(group, fromGroupNo);
		try {
			if(StringUtils.equals(eventType, "compareStart")){
				//对比开始时，锁定本地所有文件
				FileManagerDao.lockAllFileInfo(group);
			}else if(StringUtils.equals(eventType, "compareEnd")){
				startCompareLocalUnlockFile(group, member);
			}
			response.writeMessage("Y");
		} catch (Exception e) {
			response.writeMessage("ERROR");
			FileSyncLog.error(e, "%s[ACTION]: 响应%s文件组事件变更通知异常: %s", toGroupNo, fromGroupNo, e.getMessage());
		}
	}
	
	public void startCompareLocalUnlockFile(final GroupInfo group, final MemberInfo member){
		Thread t = new Thread(new Runnable() {
			public void run() {
				FileSyncLog.info("%s: 和%s文件对比服务开始...", group.getGroupNo(), member.getMemberNo());
				while (!Thread.interrupted()) {
					if (syncTop50File(group, member) > 0) {
						// 有一方为准备好，继续等待
						continue;
					}
					break;
				}
				FileSyncLog.info("%s: 和%s文件对比服务结束", group.getGroupNo(), member.getMemberNo());
			}
		});
		t.setDaemon(true);
		String compareThreadKey = String.format("%s(%s<->%s)", group.getGroupName(), group.getGroupNo(), member.getMemberNo());
		t.setName("groupCompareThread-" + compareThreadKey);
		CompareGroupFileTask.putGroupCompareThread(group, member, t);
		t.start();
	}
	private int syncTop50File(GroupInfo group, MemberInfo member) {
		String sql = "SELECT top 50 * FROM GROUP_FILES t WHERE t.LOCK = '1' AND t.del = '0' and t.group_id = ?";
		try {
			// 循环获取文件同步信息，每次50条，直到没有需要同步的信息
			List<FileInfo> list = H2Utils.queryBeanList(sql, FileInfo.class, group.getId());
			if (list == null || list.size() == 0)
				return 0;

			for (int i = 0; i < list.size(); i++) {
				FileInfo fileInfo = list.get(i);
				if (HttpServiceUtils.compareFile(group, member, fileInfo, true)) {
					FileManagerDao.unlockFileInfo(fileInfo);
				}
			}
			FileSyncLog.debug("%s: 和%s完成%s笔文件对比请求", group.getGroupNo(), member.getMemberNo(), list.size());
			return list.size();
		} catch (Exception e) {
			FileSyncLog.error(e, "%s: 和%s文件对比任务异常", group.getGroupNo(), member.getMemberNo());
		} finally {
			// IoUtils.close(conn);
		}
		return -1;
	}
}