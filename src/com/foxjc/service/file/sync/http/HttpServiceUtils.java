package com.foxjc.service.file.sync.http;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.foxjc.service.file.sync.FileSyncConfig;
import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.bean.FileChangeType;
import com.foxjc.service.file.sync.bean.FileInfo;
import com.foxjc.service.file.sync.bean.GroupInfo;
import com.foxjc.service.file.sync.bean.MemberInfo;
import com.foxjc.service.file.sync.db.FileManagerDao;
import com.foxjc.service.file.sync.filesystem.FileEventHandler;
import com.foxjc.service.file.sync.filesystem.FileInfoUtils;
import com.foxjc.service.file.sync.filesystem.event.FileCreateEvent;
import com.foxjc.service.file.sync.filesystem.event.FileModifiyEvent;

public class HttpServiceUtils {
	// 查询memberReadyTime
	public static long queryMemberReadyTime(GroupInfo group, MemberInfo member) {
		Map<String, String> params = new HashMap<String, String>();
		String url = String.format("http://%s:%d/rsync/queryMemberReadyTime", member.getIp(), member.getPort());
		params.put("ip", FileSyncConfig.localIp);
		params.put("port", Integer.toString(FileSyncConfig.httpPort));
		params.put("fromGroupNo", group.getGroupNo());
		params.put("toGroupNo", member.getMemberNo());

		try {
			Response response = HttpClient.post(url, params);
			if (response.isSuccessful()) {
				String result = response.body().string();
				return StringUtils.isNumeric(result) ? NumberUtils.toLong(result, -1) : -1;
			}
		} catch (Exception e) {
			FileSyncLog.info("获取MemberReadyTime异常: %s", e.getMessage());
		}
		return -1;
	}

	// 通知member，我上线了
	public static boolean noticeMemberOnline(GroupInfo group, MemberInfo member) {
		Map<String, String> params = new HashMap<String, String>();
		String url = String.format("http://%s:%d/rsync/memberOnline", member.getIp(), member.getPort());
		params.put("ip", FileSyncConfig.localIp);
		params.put("port", Integer.toString(FileSyncConfig.httpPort));
		params.put("fromGroupNo", group.getGroupNo());
		params.put("toGroupNo", member.getMemberNo());
		try {
			String result = HttpClient.post(url, params).body().string();
			if (StringUtils.equals(result, "Y")) {
				return true;
			}
		} catch (Exception e) {
			FileSyncLog.info("member上线通知异常: %s", e.getMessage());
		}
		return false;
	}

	public static void noticeMemberOffline(GroupInfo group, MemberInfo member) {
		Map<String, String> params = new HashMap<String, String>();
		String url = String.format("http://%s:%d/rsync/memberOffline", member.getIp(), member.getPort());
		params.put("ip", FileSyncConfig.localIp);
		params.put("port", Integer.toString(FileSyncConfig.httpPort));
		params.put("fromGroupNo", group.getGroupNo());
		params.put("toGroupNo", member.getMemberNo());
		HttpClient.post(url, params);

	}

	public static boolean compareFile(final GroupInfo group, final MemberInfo member, final FileInfo fileInfo, boolean unlockFile) {

		long lastModifyTime = fileInfo.getLastModifyTime();
		long fileLength = fileInfo.getLength();
		final String filePath = fileInfo.getPath();

		if (!FileManagerDao.isMemberOnline(member)) {
			FileSyncLog.debug("对比文件: group=%s,member=%s,file=%s member不在线，跳出此次对比", group.getGroupNo(), member.getMemberNo(), filePath);
			return false;
		}

		Map<String, String> params = new HashMap<String, String>();
		String url = String.format("http://%s:%d/rsync/compareFile", member.getIp(), member.getPort());
		params.put("ip", FileSyncConfig.localIp);
		params.put("port", Integer.toString(FileSyncConfig.httpPort));
		params.put("fromGroupNo", group.getGroupNo());
		params.put("toGroupNo", member.getMemberNo());
		params.put("filePath", filePath);
		params.put("lastModifyTime", Long.toString(lastModifyTime));
		params.put("unlockFile", unlockFile ? "Y" : "N");
		params.put("isFile", "Y");
		params.put("fileLength", Long.toString(fileLength));
		params.put("md5", fileInfo.getMd5());

		HttpClient.postAsync(url, params, new Callback() {
			@Override
			public void onResponse(Call call, Response response) throws IOException {
				String result = response.body().string();
				if (StringUtils.equals(result, "NONE")) {// 相同
					FileSyncLog.debug("对比文件: group=%s,member=%s,file=%s 无需同步", group.getGroupNo(), member.getMemberNo(), filePath);
				} else if (StringUtils.equals(result, "PULL")) {// 远端较新，远端会同步文件到本地，此处无需处理
					FileSyncLog.debug("对比文件: group=%s,member=%s,file=%s PULL文件", group.getGroupNo(), member.getMemberNo(), filePath);
					pullFile(group, member, fileInfo);
				} else if (StringUtils.equals(result, "PUSH")) {// 本地较新，需同步到服务器上
					FileSyncLog.debug("对比文件: group=%s,member=%s,file=%s PUSH文件", group.getGroupNo(), member.getMemberNo(), filePath);
					pushFile(group, member, fileInfo);
				} else if (StringUtils.equals(result, "ERROR")) {// 本地较新，需同步到服务器上
					FileSyncLog.info("对比文件: group=%s,member=%s,file=%s 返回异常，稍後同步", group.getGroupNo(), member.getMemberNo(), filePath);
					// 加入後续对比
					FileManagerDao.addFileNotSyncLogForAllMember(group, filePath, FileChangeType.MODIFY);
				} else {
					FileSyncLog.debug("对比文件: group=%s,member=%s,file=%s 未知返回%s", group.getGroupNo(), member.getMemberNo(), filePath, result);
				}
			}

			@Override
			public void onFailure(Call call, IOException e) {
				FileSyncLog.error(e, "对比文件异常：%s->%s，file=%s", group.getGroupNo(), member.getMemberNo(), filePath);
			}
		});
		return true;
	}

	/**
	 * 同步文件到member中
	 * 
	 * @param group
	 * @param member
	 * @param fileInfo
	 */
	public static void pushFile(final GroupInfo group, final MemberInfo member, final FileInfo fileInfo) {
		File file = FileInfoUtils.getFile(group.getPath(), fileInfo.getPath());
		long lastModifyTime = fileInfo.getLastModifyTime();
		long fileLength = fileInfo.getLength();

		final String filePath = fileInfo.getPath();

		Map<String, String> params = new HashMap<String, String>();
		String url = String.format("http://%s:%d/rsync/pushFile", member.getIp(), member.getPort());
		params.put("ip", FileSyncConfig.localIp);
		params.put("port", Integer.toString(FileSyncConfig.httpPort));
		params.put("fromGroupNo", group.getGroupNo());
		params.put("toGroupNo", member.getMemberNo());
		params.put("filePath", filePath);
		params.put("lastModifyTime", Long.toString(lastModifyTime));
		params.put("fileLength", Long.toString(fileLength));
		params.put("md5", fileInfo.getMd5());

		try {
			HttpClient.file(url, params, file, new Callback() {
				@Override
				public void onResponse(Call call, Response response) throws IOException {
					String result = response.body().string();
					if ("Y".equals(result)) {
						FileSyncLog.info("上传文件完成：%s->%s，file=%s", group.getGroupNo(), member.getMemberNo(), filePath);
					} else {
						FileManagerDao.addFileNotSyncLog(group, member, filePath, FileChangeType.MODIFY);
						FileSyncLog.info("上传文件失败：%s->%s，file=%s，添加到未同步文件池", group.getGroupNo(), member.getMemberNo(), filePath);
					}
				}

				@Override
				public void onFailure(Call call, IOException e) {
					FileManagerDao.addFileNotSyncLog(group, member, filePath, FileChangeType.MODIFY);
					FileSyncLog.error(e, "上传文件异常：%s->%s，file=%s，添加到未同步文件池", group.getGroupNo(), member.getMemberNo(), filePath);
				}
			});
		} catch (Exception e) {
			FileManagerDao.addFileNotSyncLog(group, member, filePath, FileChangeType.MODIFY);
			FileSyncLog.error(e, "上传文件异常：%s->%s，file=%s，添加到未同步文件池", group.getGroupNo(), member.getMemberNo(), filePath);
		}
	}

	public static void pullFile(final GroupInfo group, final MemberInfo member, final FileInfo fileInfo) {
		final File file = FileInfoUtils.getFile(group.getPath(), fileInfo.getPath());
		long lastModifyTime = fileInfo.getLastModifyTime();
		long fileLength = fileInfo.getLength();

		final String filePath = fileInfo.getPath();

		Map<String, String> params = new HashMap<String, String>();
		String url = String.format("http://%s:%d/rsync/pullFile", member.getIp(), member.getPort());
		params.put("ip", FileSyncConfig.localIp);
		params.put("port", Integer.toString(FileSyncConfig.httpPort));
		params.put("fromGroupNo", group.getGroupNo());
		params.put("toGroupNo", member.getMemberNo());
		params.put("filePath", filePath);
		params.put("lastModifyTime", Long.toString(lastModifyTime));
		params.put("fileLength", Long.toString(fileLength));
		params.put("md5", fileInfo.getMd5());

		HttpClient.postAsync(url, params, new Callback() {
			@Override
			public void onResponse(Call call, Response response) throws IOException {
				FileCreateEvent fileCreateEvent = new FileCreateEvent(group, file);
				FileModifiyEvent fileModifiyEvent = new FileModifiyEvent(group, file);
				String md5 = response.header("md5");
				long lastModified = NumberUtils.toLong(response.header("lastModified"), System.currentTimeMillis());
				BufferedSource source = null;
				BufferedSink sink = null;
				try {
					FileEventHandler.addInhibitionEvent(fileModifiyEvent);
					FileEventHandler.addInhibitionEvent(fileCreateEvent);

					source = response.body().source();
					sink = Okio.buffer(Okio.sink(file));
					source.readAll(sink);

					// 文件同步完成，需要更新文件信息
					FileSyncLog.info("抽取文件完成：group=%s,file=%s", group.getGroupNo(), member.getMemberNo(), filePath);
				} catch (Exception e) {
					FileSyncLog.error(e, "抽取文件异常：group=%s,file=%s", group.getGroupNo(), member.getMemberNo(), filePath);
				} finally {
					IOUtils.closeQuietly(source);
					IOUtils.closeQuietly(sink);
					FileEventHandler.removeInhibitionEvent(fileCreateEvent);
					FileEventHandler.removeInhibitionEvent(fileModifiyEvent);
				}
				file.setLastModified(lastModified);
				FileManagerDao.updateFileMd5(fileInfo.getId(), md5, file.length(), lastModified);
			}

			@Override
			public void onFailure(Call call, IOException e) {
				FileManagerDao.addFileNotSyncLog(group, member, filePath, FileChangeType.MODIFY);
				FileSyncLog.error(e, "抽取文件异常：%s->%s，file=%s，添加到未同步文件池", group.getGroupNo(), member.getMemberNo(), filePath);
			}
		});
	}

	public static void noticeMemberEvent(final GroupInfo group, final MemberInfo member, final String eventType) {
		Map<String, String> params = new HashMap<String, String>();
		String url = String.format("http://%s:%d/rsync/groupEventChange", member.getIp(), member.getPort());
		params.put("ip", FileSyncConfig.localIp);
		params.put("port", Integer.toString(FileSyncConfig.httpPort));
		params.put("fromGroupNo", group.getGroupNo());
		params.put("toGroupNo", member.getMemberNo());
		params.put("eventType", eventType);
		
		HttpClient.postAsync(url, params, new Callback() {
			@Override
			public void onResponse(Call call, Response response) throws IOException {
				String result = response.body().string();
				FileSyncLog.debug("%s->%s通知memberEvent=%s完成: %s", group.getGroupNo(), member.getMemberNo(), eventType, result);
			}
			
			@Override
			public void onFailure(Call call, IOException e) {
				FileSyncLog.info("获取MemberReadyTime异常: %s", e.getMessage());
			}
		});
	}
}