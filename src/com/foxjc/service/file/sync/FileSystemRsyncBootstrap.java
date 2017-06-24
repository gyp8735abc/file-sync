package com.foxjc.service.file.sync;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.bean.MemberInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.db.H2Utils;
import com.foxjc.service.file.sync.filesystem.FileSystemManager;
import com.foxjc.service.file.sync.filesystem.GroupFileScanService;
import com.foxjc.service.file.sync.filesystem.IoUtils;
import com.foxjc.service.file.sync.http.HttpClient;
import com.foxjc.service.file.sync.http.HttpServerManager;
import com.foxjc.service.file.sync.http.HttpServiceUtils;
import com.foxjc.service.file.sync.http.server.JsonUtils;

/**
 * 文件同步服务-启动器
 * @author Administrator
 *
 */
public class FileSystemRsyncBootstrap implements ApplicationListener<ApplicationContextEvent> {

	private boolean started = false;
	private String configName;
	private String configPath;
	private String configStr;
	private List<GroupInfo> groups;
	
	public static void main(String[] args) throws Exception {
		FileSystemRsyncBootstrap bootstrap = new FileSystemRsyncBootstrap();
		bootstrap.setConfigName("rsync-config.json");
		bootstrap.start();
		
		System.in.read();
		
		bootstrap.stop();
	}
	

	public void onApplicationEvent(ApplicationContextEvent event) {
		if (event instanceof ContextStartedEvent) {
			if (!started) {
				started = true;
				start();
			}
		} else if (event instanceof ContextStoppedEvent) {
			if (started) {
				started = false;
				stop();
			}
		}
	}

	public void start() {
		// 1加载配置文件config.json
		if(StringUtils.isBlank(configStr) && StringUtils.isNotBlank(configName)){
			URL url = Thread.currentThread().getContextClassLoader().getResource(configName);
			InputStream is = null;
			try {
				is = url.openStream();
				configStr = IOUtils.toString(is, "UTF-8");
				FileSyncLog.info("rsync加载配置文件 %s", configName);
			} catch (IOException e1) {
				throw new RuntimeException(String.format("加载配置文件%s异常", configName));
			} finally {
				IoUtils.close(is);
			}
		}else if(StringUtils.isBlank(configStr) && StringUtils.isNotBlank(configPath)){
			try {
				configStr = FileUtils.readFileToString(new File(configPath), "UTF-8");
				FileSyncLog.info("rsync加载配置文件 %s", configPath);
			} catch (IOException e) {
				throw new RuntimeException(String.format("加载配置文件%s异常", configPath));
			}
		}else if(StringUtils.isBlank(configStr)){
			throw new RuntimeException("无配置文件，请配置configName|configPath|configStr");
		}else{
			FileSyncLog.info("rsync加载配置文件 %s", configStr);
		}
		
		
		JSONObject config = JSON.parseObject(configStr);
		FileSyncConfig.serverName = JsonUtils.getString(config, "serverName", "foxjc-http-server");
		FileSyncConfig.localIp = JsonUtils.getString(config, "localIp", "");
		FileSyncConfig.dbPort = JsonUtils.getInt(config, "dbPort", 9092);
		FileSyncConfig.httpPort = JsonUtils.getInt(config, "httpPort", 0);
		FileSyncConfig.initThreadPoolSize = JsonUtils.getInt(config, "initThreadPoolSize", 6);
		FileSyncConfig.maxThreadPoolSize = JsonUtils.getInt(config, "maxThreadPoolSize", 128);
		FileSyncConfig.defaultCharsetName = JsonUtils.getString(config, "charsetEncodingName", "UTF-8");
		FileSyncConfig.dbFilePath = JsonUtils.getString(config, "dbFilePath", System.getProperty("java.io.tmpdir"));
		boolean debug = JsonUtils.getBoolean(config, "debug", false);
		FileSyncLog.info(String.format("初始化serverName: %s", FileSyncConfig.serverName));
		FileSyncLog.info(String.format("初始化charsetEncodingName: %s", FileSyncConfig.defaultCharsetName));
		FileSyncLog.info(String.format("初始化dbFilePath: %s", FileSyncConfig.dbFilePath));
		if(debug){
			FileSyncLog.setDebug(debug);
		}
		
		// 2初始化db
		H2Utils.init();
		FileManagerDao.initTable();
		groups = new ArrayList<GroupInfo>();
		try {
			JSONArray gs = config.getJSONArray("groups");
			//FileSyncDao.clearGroupAndMembers();
			for (int i = 0; i < gs.size(); i++) {
				JSONObject group = gs.getJSONObject(i);
				String groupId = group.getString("groupId");
				String groupNo = group.getString("group");
				String groupPath = FilenameUtils.normalizeNoEndSeparator(group.getString("path"), true);
				JSONArray members = group.getJSONArray("members");
				
				GroupInfo groupInfo = FileManagerDao.getGroupInfoByNo(groupId);
				if(groupInfo == null){
					groupInfo = FileManagerDao.addGroupInfo(groupId, groupNo, groupPath);
				}
				groups.add(groupInfo);
				FileSyncLog.info("创建Group %s@%s[%s], ID=%s", groupInfo.getGroupName(), groupInfo.getGroupNo(), groupInfo.getPath(), groupInfo.getId());
				for (int j = 0; j < members.size(); j++) {
					String member = members.getString(j);
					String memberIp = StringUtils.substringBefore(member, ":");
					int memberPort = NumberUtils.toInt(StringUtils.substringBetween(member, ":", "@"));
					String memberNo = StringUtils.substringAfter(member, "@");
					
					MemberInfo memberInfo = FileManagerDao.getGroupMember(groupInfo, memberNo);
					if(memberInfo == null){
						memberInfo = FileManagerDao.addGroupMember(groupInfo, memberIp, memberPort, memberNo);
					}else{
						FileManagerDao.updateGroupMemberOfflineStatus(groupNo, memberNo);
					}
					FileSyncLog.info("创建Group %s的盟友%s:%d, ID=%s", groupInfo.getGroupNo(), memberInfo.getIp(), memberInfo.getPort(), memberInfo.getId());
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(String.format("加载配置文件异常:%s", e.getMessage()), e);
		}
		
		// 3初始化httpserver
		HttpServerManager.start();
		// 6监视本地文件
		FileSystemManager.startMonitorGroups(groups);
		
		// 4扫描本地文件
		FileSyncLog.info("开始扫描Group本地文件");
		for (int i = 0; i < groups.size(); i++) {
			GroupInfo group = groups.get(i);
			GroupFileScanService.scanGroupFile(group);
		}
		// 7对比本地文件
		FileSystemManager.startCompareGroupFileTask(groups);
	}

	public void stop() {
		//通知member离綫
		for (int i = 0; i < groups.size(); i++) {
			GroupInfo group = groups.get(i);
			List<MemberInfo> members = FileManagerDao.getGroupMembers(group);
			for (int j = 0; j < members.size(); j++) {
				MemberInfo member = members.get(j);
				HttpServiceUtils.noticeMemberOffline(group, member);
			}
		}
		
		FileSystemManager.stopCompareGroupFileTask();
		FileSystemManager.stopMonitorGroups();
		HttpServerManager.stop();
		HttpClient.close();
		H2Utils.stop();
	}

	public void setConfigName(String configPath) {
		this.configName = configPath;
	}


	public void setConfigPath(String configPath) {
		this.configPath = configPath;
	}

	public void setConfigStr(String configStr) {
		this.configStr = configStr;
	}
}