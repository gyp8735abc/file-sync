package com.foxjc.service.file.sync.filesystem.task;

import java.util.List;

import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.GroupFileScanService;

/**
 * 每天一次的扫描文件，核对数据库中的文件信息是否准确
 * @author 郭跃鹏
 *
 */
public class CheckGroupFileTask implements Runnable{

	@Override
	public void run() {
		List<GroupInfo> groups = FileManagerDao.getAllGroupInfo();
		if(groups == null)return;
		for (int i = 0; i < groups.size(); i++) {
			GroupInfo group = groups.get(i);
			GroupFileScanService.scanGroupFile(group);
		}
		
	}

}
