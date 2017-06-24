import com.foxjc.service.file.sync.FileSystemRsyncBootstrap;

public class HttpServerTest {

	public static void main(String[] args) throws Exception {
		FileSystemRsyncBootstrap bootstrap = new FileSystemRsyncBootstrap();
		bootstrap.setConfigPath("bin/rsync-config.json");
		bootstrap.start();
		
		System.in.read();
		
		bootstrap.stop();
	}
}
