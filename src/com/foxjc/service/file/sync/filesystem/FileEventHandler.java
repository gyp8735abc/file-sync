package com.foxjc.service.file.sync.filesystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.event.FileEvent;

/**
 * 文件事件异步处理器
 * 事件先加入一个队列
 * 一个线程异步处理相关事件
 * @author 郭跃鹏
 * 2016/9/17-上午8:05:08
 */
public class FileEventHandler {
	
	private static LinkedBlockingQueue<FileEvent> queue = new LinkedBlockingQueue<FileEvent>();
	private static Map<FileEvent, ScheduledFuture<?>> eventCache = new ConcurrentHashMap<FileEvent, ScheduledFuture<?>>();
	private static ScheduledExecutorService service = Executors.newScheduledThreadPool(4);
	private static List<FileEvent> inhibitionEvent = Collections.synchronizedList(new ArrayList<FileEvent>());
	private static Thread handlerThread;

	public static void push(FileEvent event) {
		if(hasInhibitionEvent(event)){
			if(FileSyncLog.isDebug()){
				String fileGroupPath = FileInfoUtils.differencePath(event.getGroup().getPath(), FilenameUtils.normalize(event.getFile().getAbsolutePath(), true));
				FileSyncLog.debug("阻止事件group=%s,file=%s,event=%s", event.getGroup().getGroupNo(), fileGroupPath, event.getEventType());
			}
			return;
		}
		try {
			queue.put(event);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * 延迟添加事件
	 * 相同的事件会导致取消前一个事件，而当前事件立即执行
	 * 如果相同事件已经在执行，则随後添加一个文件对比事件
	 * 有相同事件已经执行完成，或者没有执行被取消的事件，则添加当前执行事件
	 * @param event
	 * @param delay 毫秒数
	 */
	public static void push(final FileEvent event, int delay) {
		if(hasInhibitionEvent(event)){
			if(FileSyncLog.isDebug()){
				String fileGroupPath = FileInfoUtils.differencePath(event.getGroup().getPath(), FilenameUtils.normalize(event.getFile().getAbsolutePath(), true));
				FileSyncLog.debug("阻止事件group=%s,file=%s,event=%s", event.getGroup().getGroupNo(), fileGroupPath, event.getEventType());
			}
			return;
		}
		Set<FileEvent> keys = eventCache.keySet();
		FileEvent oldKey = null;
		for (FileEvent key : keys) {
			if(key.equals(event)){
				oldKey = key;
				break;
			}
		}
		if(oldKey != null){
			try {
				ScheduledFuture<?> sf = eventCache.remove(oldKey);
				sf.cancel(true);
				delay = 800;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		ScheduledFuture<?> sf = service.schedule(new DelayEventTask(event), delay, TimeUnit.MILLISECONDS);
		eventCache.put(event, sf);
	}
	private static class DelayEventTask implements Runnable{
		
		private FileEvent event;
		public DelayEventTask(FileEvent event){
			this.event = event;
		}
		@Override
		public void run() {
			ScheduledFuture<?> sf = eventCache.remove(event);
			if(sf == null)return;
			if(sf.isCancelled())return;
			push(event);
		}
	}

	public static void start() {
		if (handlerThread != null) {
			stop();
		}
		handlerThread = new Thread(new Runnable() {
			public void run() {
				while (!Thread.currentThread().isInterrupted()) {
					try {
						FileEvent event = queue.take();
						if(event == null)continue;
						if(hasInhibitionEvent(event)){
							if(FileSyncLog.isDebug()){
								String fileGroupPath = FileInfoUtils.differencePath(event.getGroup().getPath(), FilenameUtils.normalize(event.getFile().getAbsolutePath(), true));
								FileSyncLog.debug("阻止事件group=%s,file=%s,event=%s", event.getGroup().getGroupNo(), fileGroupPath, event.getEventType());
							}
							continue;
						}
						//否则的话，执行相关事件
						event.execute();
					} catch (InterruptedException e) {
						break;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				//如果线程退出
				//则把队列中的事件写入到数据库中
				for (FileEvent event = queue.poll(); event != null; event = queue.poll()) {
					if(hasInhibitionEvent(event)){
						if(FileSyncLog.isDebug()){
							String fileGroupPath = FileInfoUtils.differencePath(event.getGroup().getPath(), FilenameUtils.normalize(event.getFile().getAbsolutePath(), true));
							FileSyncLog.debug("阻止事件group=%s,file=%s,event=%s", event.getGroup().getGroupNo(), fileGroupPath, event.getEventType());
						}
						continue;
					}
					String filePath = FilenameUtils.normalize(event.getFile().getAbsolutePath(), true);
					String fileGroupPath = FileInfoUtils.differencePath(event.getGroup().getPath(), filePath);
					FileManagerDao.addFileNotSyncLogForAllMember(event.getGroup(), fileGroupPath, event.getEventType());
					FileSyncLog.info("保存group=%s, file=%s, event=%s到数据库", event.getGroup().getGroupNo(), fileGroupPath, event.getEventType());
				}
			}
		});
		handlerThread.setName("file-event-handler-thread");
		handlerThread.start();
	}

	public static void stop() {
		if (handlerThread != null) {
			handlerThread.interrupt();
			handlerThread = null;
		}
		service.shutdownNow();
	}
	/**
	 * 新增抑制事件
	 * @param event
	 */
	public static void addInhibitionEvent(FileEvent event){
		if(FileSyncLog.isDebug()){
			String fileGroupPath = FileInfoUtils.differencePath(event.getGroup().getPath(), FilenameUtils.normalize(event.getFile().getAbsolutePath(), true));
			FileSyncLog.debug("新增抑制事件group=%s,file=%s,event=%s", event.getGroup().getGroupNo(), fileGroupPath, event.getEventType());
		}
		inhibitionEvent.add(event);
	}
	/**
	 * 清除抑制事件
	 * @param event
	 */
	public static void removeInhibitionEvent(FileEvent event){
		service.schedule(new DelayRemoveInhibitionEventTask(event), 1000, TimeUnit.MILLISECONDS);
	}
	private static class DelayRemoveInhibitionEventTask implements Runnable{
		
		private FileEvent event;
		public DelayRemoveInhibitionEventTask(FileEvent event){
			this.event = event;
		}
		@Override
		public void run() {
			inhibitionEvent.remove(event);
			if(FileSyncLog.isDebug()){
				String fileGroupPath = FileInfoUtils.differencePath(event.getGroup().getPath(), FilenameUtils.normalize(event.getFile().getAbsolutePath(), true));
				FileSyncLog.debug("移除抑制事件group=%s,file=%s,event=%s", event.getGroup().getGroupNo(), fileGroupPath, event.getEventType());
			}
		}
	}
	/**
	 * 压缩抑制事件
	 * 移除过期的抑制事件
	 */
	public static void cleanInhibitionEvent(){
		for (int i = 0; i < inhibitionEvent.size(); i++) {
			FileEvent e = inhibitionEvent.get(i);
			//所有抑制事件，如果超过1分钟，则清除抑制事件
			if(System.currentTimeMillis() - e.getRecordTime().getTime() > 1000*60){
				inhibitionEvent.remove(i--);
				FileSyncLog.debug("清除过期抑制事件group=%s,event=%s,time=%s,file=%s", e.getGroup().getGroupNo(), e.getEventType(), DateFormatUtils.format(e.getRecordTime(), "yyyy-MM-dd HH:mm:ss"), e.getFile().getAbsolutePath());
				continue;
			}
		}
	}
	
	public static boolean hasInhibitionEvent(FileEvent event){
		int idx = inhibitionEvent.indexOf(event);
		if(idx == -1)return false;
		FileEvent e = inhibitionEvent.get(idx);
		e.setState(e.getState()+1);
		return true;
	}
}