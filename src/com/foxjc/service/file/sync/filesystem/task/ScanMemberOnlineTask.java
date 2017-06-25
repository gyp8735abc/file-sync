package com.foxjc.service.file.sync.filesystem.task;

import java.util.List;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.bean.MemberInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.http.HttpServiceUtils;

/**
 * member在綫扫描，每一分钟执行一次
 * @author 郭跃鹏
 * 2016/9/16-下午3:32:44
 */
public class ScanMemberOnlineTask implements Runnable{

	private List<GroupInfo> groups;
	public ScanMemberOnlineTask(List<GroupInfo> groups){
		this.groups = groups;
	}
	@Override
	public void run() {
		for (int i = 0; i < groups.size(); i++) {
			GroupInfo group = groups.get(i);
			List<MemberInfo> members = FileManagerDao.getGroupMembers(group);
			for (int j = 0; j < members.size(); j++) {
				MemberInfo member = members.get(j);
				if(HttpServiceUtils.noticeMemberOnline(group, member)){
					if("N".equals(member.getOnline())){
						member.setOnline("Y");
						FileManagerDao.updateGroupMemberOnlineStatus(group.getGroupNo(), member.getMemberNo());
						CompareGroupFileTask.startCompareGroup(group, member);
						FileSyncLog.info("%s: member=%s上线", group.getGroupNo(), member.getMemberNo());
					}
				}else if("Y".equals(member.getOnline())){
					member.setOnline("N");
					FileManagerDao.updateGroupMemberOfflineStatus(group.getGroupNo(), member.getMemberNo());
					FileSyncLog.info("%s: member=%s离綫", group.getGroupNo(), member.getMemberNo());
					CompareGroupFileTask.interruptCompareGroup(group, member);
				}
			}
		}
	}
}
