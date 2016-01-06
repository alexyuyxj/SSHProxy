package m.sshproxy;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.DynamicPortForwarder;

public class SSHService extends Service implements Callback {
	public static final int ACTION_STOP = 0;
	public static final int ACTION_START = 1;
	
	public static final String ACTION_STATE = "m.sshproxy.broadcast.ACTION_STATE";
	public static final int STATE_STARTING = 1;
	public static final int STATE_STARTED = 2;
	public static final int STATE_STOPING = 3;
	public static final int STATE_STOPED = 0;
	
	private int[] uids;
	private boolean started;
	private Handler handler;
	private Connection connection;
	private DynamicPortForwarder forwarder;
	
	public void onCreate() {
		super.onCreate();
		handler = new Handler(this);
		try {
			PackageManager pm = getPackageManager();
			List<PackageInfo> pis = pm.getInstalledPackages(0);
			uids = new int[pis.size()];
			for (int i = 0; i < uids.length; i++) {
				uids[i] = pis.get(i).applicationInfo.uid;
			}
		} catch (Throwable t) {
			t.printStackTrace();
			uids = null;
		}
	}
	
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	public void onDestroy() {
		super.onDestroy();
		System.exit(0);
	}
	
	public int onStartCommand(Intent intent, int flags, int startId) {
		int action = intent == null ? ACTION_STOP : intent.getIntExtra("action", ACTION_STOP);
		switch (action) {
			case ACTION_STOP: stopSSHService(intent); break;
			case ACTION_START: startSSHService(intent); break;
		}
		return START_STICKY;
	}

	public boolean handleMessage(Message msg) {
		Intent ii = new Intent(ACTION_STATE);
		ii.putExtra("state", msg.what);
		sendBroadcast(ii);
		return false;
	}
	
	private void startSSHService(final Intent intent) {
		if (started) {
			return;
		}
		
		started = true;
		new Thread() {
			public void run() {
				handler.sendEmptyMessage(STATE_STARTING);
				String server = intent.getStringExtra("server");
				String account = intent.getStringExtra("account");
				String password = intent.getStringExtra("password");
				startSSH(server, account, password);
				handler.sendEmptyMessage(STATE_STARTED);
			}
		}.start();
	}
	
	private void stopSSHService(final Intent intent) {
		if (!started) {
			return;
		}
		
		started = false;
		new Thread() {
			public void run() {
				handler.sendEmptyMessage(STATE_STOPING);
				String server = intent.getStringExtra("server");
				stopSSH(server);
				handler.sendEmptyMessage(STATE_STOPED);
				stopSelf();
			}
		}.start();
	}
	
	private void startSSH(String server, String account, String password) {
		try {
			connection = new Connection(server);
			connection.connect();
			connection.authenticateWithPassword(account, password);
			forwarder = connection.createDynamicPortForwarder(7890);
			if (connection.isAuthenticationComplete()) {
				String base = copyFiles();
				String command = base + "/proxy_socks.sh " + "start 7890 " + base;
				runCommand(command);
				
				command = "iptables -t nat -N MOBSSH\n"
						+ "iptables -t nat -F MOBSSH\n"
						+ "iptables -t nat -A MOBSSH -p tcp -j REDIRECT --to 6789\n"
						+ "iptables -t nat -A OUTPUT -p tcp -d " + server + " -j RETURN\n";
				for (int i = 0, len = (uids == null ? 0 : uids.length); i < len; i++) {
					command += "iptables -t nat -m owner --uid-owner " + uids[i] + " -A OUTPUT -p tcp -j MOBSSH\n";
				}
				command.substring(0, command.length() - 1);
				runCommand(command);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
	private void stopSSH(String server) {
		try {
			String command = "iptables -t nat -F MOBSSH\n"
					+ "iptables -t nat -X MOBSSH\n"
					+ "iptables -t nat -D OUTPUT -p tcp -d " + server + " -j RETURN\n";
			for (int i = 0, len = (uids == null ? 0 : uids.length); i < len; i++) {
				command += "iptables -t nat -m owner --uid-owner " + uids[i] + " -D OUTPUT -p tcp -j MOBSSH\n";
			}
			command.substring(0, command.length() - 1);
			runCommand(command);
			
			command = new File(getFilesDir(), "exe/proxy_socks.sh stop").getAbsolutePath();
			runCommand(command);
			
			forwarder.close();
			connection.close();	
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
	private String copyFiles() throws Throwable {
		File dir = new File(getFilesDir(), "exe");
		String[] names = new String[] {
				"proxy_socks.sh",
				"redsocks"
		};
		byte[] buf = new byte[1024];
		for (String name : names) {
			InputStream  is = getAssets().open(name);
			File file = new File(dir, name);
			if (file.exists()) {
				file.delete();
			}
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			FileOutputStream fos = new FileOutputStream(file);
			int len = is.read(buf);
			while (len > 0) {
				fos.write(buf, 0, len);
				len = is.read(buf);
			}
			fos.flush();
			fos.close();
			is.close();
			file.setExecutable(true);
		}
		return dir.getAbsolutePath();
	}
	
	private void runCommand(String command) {
		Process process = null;
		DataOutputStream os = null;
		try {
			process = Runtime.getRuntime().exec("su");
			os = new DataOutputStream(process.getOutputStream());
			os.writeBytes(command + "\n");
			os.writeBytes("exit\n");
			os.flush();
			process.waitFor();
			System.out.println("============ " + command);
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			try {
				if (os != null) {
					os.close();
				}
				process.destroy();
			} catch (Exception e) {}
		}
	}
	
}
