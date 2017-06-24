import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.time.FastDateFormat;

import com.foxjc.service.file.sync.filesystem.FileLockUtils;
import com.foxjc.service.file.sync.http.HttpClient;


public class Test {

	public static void main(String[] args) throws Exception {
		HttpClient.getAsync("http://10.65.3.2/jcsc/main.action", null, new Callback() {
			@Override
			public void onResponse(Call call, Response response) throws IOException {
				System.out.println(response);
			}
			
			@Override
			public void onFailure(Call call, IOException e) {
				e.printStackTrace();
			}
		});
		HttpClient.close();
	}
}
