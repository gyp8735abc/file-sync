package com.foxjc.service.file.sync.filesystem;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DateUtils;

import com.foxjc.service.file.monitor.LocalFileMonitor;
import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.filesystem.task.CheckGroupFileTask;
import com.foxjc.service.file.sync.filesystem.task.CleanInhibitionEventTask;
import com.foxjc.service.file.sync.filesystem.task.ScanMemberOnlineTask;
import com.foxjc.service.file.sync.filesystem.task.SyncGroupFileTask;

public class FileSystemManager {

	private static Map<String, LocalFileMonitor> monitors = new ConcurrentHashMap<String, LocalFileMonitor>();
	private static ScheduledExecutorService service = Executors.newScheduledThreadPool(3);

	/**
	 * 开始监视各个group
	 */
	public static void startMonitorGroups(List<GroupInfo> groups) {
		FileEventHandler.start();
		for (GroupInfo group : groups) {
			File f = new File(group.getPath());
			if(!f.exists()){
				f.mkdirs();
				FileSyncLog.info("group=%s, path=%s不存在，自动创建目录", group.getGroupNo(), group.getPath());
			}
			LocalFileMonitor monitor = new LocalFileMonitor(group.getPath(), new FileChangeNotifyCallback(group));
			monitors.put(group.getId(), monitor);
			monitor.start();
		}
	}

	public static void stopMonitorGroups() {
		Set<String> keys = monitors.keySet();
		for (String key : keys) {
			LocalFileMonitor monitor = monitors.remove(key);
			monitor.stop();
		}
		FileEventHandler.stop();
	}

	public static void startCompareGroupFileTask(List<GroupInfo> groups) {
		// 第二个参数为首次执行的延时时间，第三个参数为定时执行的间隔时间
		service.scheduleAtFixedRate(new ScanMemberOnlineTask(groups), 1, 3, TimeUnit.SECONDS);
		FileSyncLog.info("Member在线扫描任务1秒後启动，每3秒执行一次");
		
		service.scheduleWithFixedDelay(new SyncGroupFileTask(), 3, 3, TimeUnit.SECONDS);
		FileSyncLog.info("延迟文件同步任务3秒後启动，每3秒执行一次");
		
		service.scheduleAtFixedRate(new CleanInhibitionEventTask(), 3, 5, TimeUnit.SECONDS);
		FileSyncLog.info("清除文件系统事件抑制器任务3秒後启动，每5秒执行一次");
		
		Date now = new Date();
		now = DateUtils.truncate(now, Calendar.DAY_OF_MONTH);
		now = DateUtils.addDays(now, 1);
		now = DateUtils.addHours(now, 1);
		long delay = (now.getTime() - System.currentTimeMillis())/1000; 
		service.scheduleAtFixedRate(new CheckGroupFileTask(), delay, 60*60*24, TimeUnit.SECONDS);
		FileSyncLog.info("文件校验任务任务"+delay+"秒後启动，每天01:00执行");
	}

	public static void stopCompareGroupFileTask() {
		service.shutdownNow();
	}
}