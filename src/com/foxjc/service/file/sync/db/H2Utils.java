package com.foxjc.service.file.sync.db;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.h2.tools.Server;

import com.foxjc.service.file.sync.FileSyncConfig;
import com.foxjc.service.file.sync.FileSyncLog;
import com.foxjc.service.file.sync.filesystem.IoUtils;

/**
 * 数据库工具类
 * 
 * @author 郭跃鹏 2016/8/26-下午3:21:58
 */
public class H2Utils {

	static {
		try {
			Class.forName("org.h2.Driver");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private static QueryRunner run = new QueryRunner(true);
	private static Connection conn;
	private static Server server;
	private static String url;

	public static void init() {
		if (StringUtils.isBlank(FileSyncConfig.dbFilePath)) {
			FileSyncConfig.dbFilePath = System.getProperty("java.io.tmpdir");
		}
		File dir = new File(FileSyncConfig.dbFilePath);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		url = String.format("jdbc:h2:tcp://%s:%s/%s/foxjcRsync;AUTO_RECONNECT=TRUE",//;DB_CLOSE_ON_EXIT=FALSE
				FileSyncConfig.localIp,
				FileSyncConfig.dbPort,
				FilenameUtils.normalizeNoEndSeparator(dir.getAbsolutePath(), true));
		FileSyncLog.info("H2DB-URL=%s", url);
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url, "sa", "");
		} catch (Exception e1) {
		}
		try {
			if (conn == null) {
				FileSyncLog.info("本机未发现H2服务，准备建立H2数据库服务");
				server = Server.createTcpServer("-tcpPort", Integer.toString(FileSyncConfig.dbPort), "-tcpAllowOthers").start();
				conn = DriverManager.getConnection(url, "sa", "");
				conn.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		FileSyncLog.info("创建h2数据库连接");
	}

	public static Connection getConnection() {
		if(conn != null)return conn;
		try {
			conn = DriverManager.getConnection(url, "sa", "");
		} catch (Exception e) {
			throw new RuntimeException("获取数据库连接异常", e);
		}
		return conn;
	}

	public static <T> T queryBean(String sql, Class<T> clz, Object... params) {
		Connection conn = null;
		try {
			conn = getConnection();
			return run.query(conn, sql, new BeanHandler<T>(clz), handlerSqlParam(params));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			//IoUtils.close(conn);
		}
	}

	private static Object[] handlerSqlParam(Object[] params) {
		return params;
	}

	public static <T> List<T> queryBeanList(String sql, Class<T> clz, Object... params) {
		Connection conn = null;
		try {
			conn = getConnection();
			return run.query(conn, sql, new BeanListHandler<T>(clz), handlerSqlParam(params));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			//IoUtils.close(conn);
		}
	}

	public static List<String> queryStringList(String sql, Object... params) {
		Connection conn = null;
		try {
			conn = getConnection();
			return run.query(conn, sql, new ColumnListHandler<String>(1), handlerSqlParam(params));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			//IoUtils.close(conn);
		}
	}

	public static int queryScalarInt(String sql, Object... params) {
		Object obj = queryScalarObject(sql, params);
		if (obj == null) {
			return 0;
		} else if (obj instanceof Number) {
			Number num = (Number) obj;
			return num.intValue();
		} else if (obj instanceof CharSequence) {
			String s = obj.toString();
			try {
				return Integer.valueOf(s);
			} catch (Exception e) {
				throw new RuntimeException("转换数字异常", e);
			}
		} else {
			throw new RuntimeException("未知的结果类型：" + obj);
		}
	}

	public static String queryScalarString(String sql, Object... params) {
		Object obj = queryScalarObject(sql, params);
		if (obj == null)
			return null;
		if (obj instanceof Number) {
			return ((Number) obj).toString();
		} else if (obj instanceof Clob) {
			StringBuffer sb = new StringBuffer();
			try {
				Clob c = (Clob) obj;
				Reader reader = c.getCharacterStream();
				char[] bs = new char[1024 * 4];
				int num;
				while ((num = reader.read(bs)) != -1) {
					sb.append(bs, 0, num);
				}
				IoUtils.close(reader);
				return sb.toString();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (obj instanceof Blob) {
			InputStream is = null;
			try {
				Blob c = (Blob) obj;
				is = c.getBinaryStream();
				return IOUtils.toString(is, Charset.defaultCharset());
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				IoUtils.close(is);
			}
		} else if (obj instanceof Date) {
			return DateFormatUtils.format((Date) obj, "yyyy/MM/dd HH:mm:ss");
		} else {
			return obj.toString();
		}
		return null;
	}

	public static Object queryScalarObject(String sql, Object... params) {
		Connection conn = null;
		try {
			conn = getConnection();
			return run.query(conn, sql, new ScalarHandler<Object>(), handlerSqlParam(params));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			//IoUtils.close(conn);
		}
	}

	public static int update(String sql, Object... params) {
		Connection conn = null;
		try {
			conn = getConnection();
			return run.update(conn, sql, handlerSqlParam(params));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			//IoUtils.close(conn);
		}
	}

	public static int[] batch(String sql, Object[][] params) {
		Connection conn = null;
		try {
			conn = getConnection();
			for (int i = 0; i < params.length; i++) {
				params[i] = handlerSqlParam(params[i]);
			}
			return run.batch(conn, sql, params);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			//IoUtils.close(conn);
		}
	}

	/**
	 * 指定需要返回的主键
	 * 
	 * @param generatedKey
	 *            指定insert返回的主键
	 * @return
	 */
	public synchronized static String insert(String sql, Object... params) {
		PreparedStatement pst = null;
		Connection conn = null;
		try {
			conn = getConnection();
			pst = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
			run.fillStatement(pst, handlerSqlParam(params));
			int i = pst.executeUpdate();
			String pk = null;
			if (i == 0) {
				return pk;
			}
			ResultSet rs = pst.getGeneratedKeys();
			if (rs.next()) {
				Object id = rs.getObject(1);
				pk = id.toString();
				rs.close();
			} else {
				throw new RuntimeException("获取自动主键错误");
			}
			return pk;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			IoUtils.close(pst);
			//IoUtils.close(conn);
		}
	}

	public static void stop() {
		try {
			server.stop();
			FileSyncLog.info("数据库连接断开");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}