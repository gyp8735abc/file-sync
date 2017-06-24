package com.foxjc.service.file.sync.filesystem.task;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.bean.MemberInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.db.H2Utils;
import com.foxjc.service.file.sync.http.HttpServiceUtils;

/**
 * 对比各组文件的任务
 * 
 * @author 郭跃鹏
 * 
 */
public class CompareGroupFileTask {

	private static Map<String, Thread> groupCompareThreads = Collections.synchronizedMap(new HashMap<String, Thread>());

	public static void startCompareGroupAllMember(GroupInfo group) {
		List<MemberInfo> members = FileManagerDao.getGroupMembers(group);
		for (int i = 0; i < members.size(); i++) {
			startCompareGroup(group, members.get(i));
		}
	}

	/**
	 * 开始对比文件服务
	 * 
	 * @param group
	 * @param member
	 */
	public static void startCompareGroup(final GroupInfo group, final MemberInfo member) {
		final String compareThreadKey = String.format("%s(%s<->%s)", group.getGroupName(), group.getGroupNo(), member.getMemberNo());
		if ("Y".equals(member.getOnline())) {
			// 如果在线，则精心同步协调
			Thread t = new Thread(new Runnable() {
				public void run() {
					long localGroupReadyTime = -1;
					long memberReadyTime = -1;
					while (!Thread.interrupted()) {
						localGroupReadyTime = FileManagerDao.getGroupReadyTime(group);
						memberReadyTime = HttpServiceUtils.queryMemberReadyTime(group, member);
						if (localGroupReadyTime == -1 || memberReadyTime == -1) {
							// 有一方为准备好，继续等待
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								return;
							}
							continue;
						}
						break;
					}
					if (localGroupReadyTime < memberReadyTime) {
						FileSyncLog.info("文件对比服务group=%s,member=%s: ReadyTime小於member=%s，对比服务由member进行，%s终止对比", 
								group.getGroupNo(), 
								member.getMemberNo(),
								member.getMemberNo(),
								group.getGroupNo());
						return;
					}
					synchronized (group) {
						FileSyncLog.info("文件对比服务group=%s,member=%s: 文件对比服务开始...", group.getGroupNo(), member.getMemberNo());
						FileManagerDao.lockAllFileInfo(group);
						HttpServiceUtils.noticeMemberEvent(group, member, "compareStart");
						while (!Thread.interrupted()) {
							if (syncTop50File(group, member) > 0) {
								// 有一方为准备好，继续等待
								continue;
							}
							break;
						}
						FileSyncLog.info("文件对比服务group=%s,member=%s: 文件对比服务结束", group.getGroupNo(), member.getMemberNo());
						HttpServiceUtils.noticeMemberEvent(group, member, "compareEnd");
					}
					groupCompareThreads.remove(compareThreadKey);
				}
			});
			groupCompareThreads.put(compareThreadKey, t);
			t.setDaemon(true);
			t.setName("groupCompareThread-" + compareThreadKey);
			t.start();
		}
	}
	
	public static void putGroupCompareThread(GroupInfo group, MemberInfo member, Thread t){
		String compareThreadKey = String.format("%s(%s<->%s)", group.getGroupName(), group.getGroupNo(), member.getMemberNo());
		groupCompareThreads.put(compareThreadKey, t);
	}

	/**
	 * 终止正在进行的对比服务
	 * 
	 * @param group
	 * @param member
	 */
	public static void interruptCompareGroup(GroupInfo group, MemberInfo member) {
		String compareThreadKey = String.format("%s(%s<->%s)", group.getGroupName(), group.getGroupNo(), member.getMemberNo());
		if (groupCompareThreads.containsKey(compareThreadKey)) {
			Thread t = groupCompareThreads.get(compareThreadKey);
			t.interrupt();
			groupCompareThreads.remove(compareThreadKey);
		}
	}

	private static int syncTop50File(GroupInfo group, MemberInfo member) {
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
			FileSyncLog.debug("文件对比服务group=%s,member=%s: 完成%s笔文件对比请求", group.getGroupNo(), member.getMemberNo(), list.size());
			return list.size();
		} catch (Exception e) {
			FileSyncLog.error(e, "文件对比服务group=%s,member=%s: 文件对比任务异常", group.getGroupNo(), member.getMemberNo());
		} finally {
			// IoUtils.close(conn);
		}
		return -1;
	}
}
