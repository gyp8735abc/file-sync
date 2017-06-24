package com.foxjc.service.file.sync.filesystem;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.zip.CRC32;

/**
 * 文件校验工具类
 * @author 郭跃鹏
 *
 */
public class FileMd5Utils {
	private static char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	public static String getFileMD5(File file) {
		return getFileMessageDigest(file, "MD5");
	}
	public static String getFileSHA256(File file) {
		return getFileMessageDigest(file, "SHA-256");
	}
	public static String getFileMessageDigest(File file, String algorithm) {
		if(file == null || file.isDirectory() || !file.exists() || file.length() == 0)return null;
		// 缓冲区大小（这个可以抽出一个参数）
		int bufferSize = 16 * 1024;
		FileInputStream fis = null;
		BufferedInputStream bis = null;
		DigestInputStream dis = null;
		try {
			// 拿到一个MD5转换器（同样，这里可以换成SHA1）
			MessageDigest MD5 = MessageDigest.getInstance(algorithm);
			// 使用DigestInputStream
			fis = new FileInputStream(file);
			bis = new BufferedInputStream(fis);
			dis = new DigestInputStream(bis, MD5);
			// read的过程中进行MD5处理，直到读完档
			byte[] buffer = new byte[bufferSize];
			while (dis.read(buffer) > 0) {}
			// 获取最终的MessageDigest
			MD5 = dis.getMessageDigest();
			// 拿到结果，也是位元组数组，包含16个元素
			byte[] resultByteArray = MD5.digest();
			// 同样，把位元组数组转换成字串
			return byteToHex(resultByteArray);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			IoUtils.close(dis);
			IoUtils.close(bis);
			IoUtils.close(fis);
		}
	}
	

	/**
	 * 获取档CRC32码
	 * 
	 * @return String
	 * */
	public static String getCRC32(File file) {
		CRC32 crc32 = new CRC32();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			byte[] buffer = new byte[8192];
			int length;
			while ((length = fis.read(buffer)) != -1) {
				crc32.update(buffer, 0, length);
			}
			return crc32.getValue() + "";
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			IoUtils.close(fis);
		}
	}

	public static String getMD5String(String s) {
		return getMD5String(s.getBytes());
	}

	public static String getMD5String(byte[] bytes) {
		try {
			MessageDigest MD5 = MessageDigest.getInstance("MD5");
			MD5.update(bytes);
			return byteToHex(MD5.digest());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @Description 计算二进制数据
	 * @return String
	 * */
	private static String byteToHex(byte bytes[]) {
		return byteToHex(bytes, 0, bytes.length);
	}

	private static String byteToHex(byte bytes[], int m, int n) {
		StringBuffer stringbuffer = new StringBuffer(2 * n);
		int k = m + n;
		for (int l = m; l < k; l++) {
			appendHexPair(bytes[l], stringbuffer);
		}
		return stringbuffer.toString();
	}

	private static void appendHexPair(byte bt, StringBuffer stringbuffer) {
		char c0 = hexDigits[(bt & 0xf0) >> 4];
		char c1 = hexDigits[bt & 0xf];
		stringbuffer.append(c0);
		stringbuffer.append(c1);
	}

	public static boolean checkPassword(String password, String md5PwdStr) {
		if(password == null || password.length() == 0 || md5PwdStr == null)return false;
		String s = getMD5String(password);
		return s.equals(md5PwdStr);
	}
}
