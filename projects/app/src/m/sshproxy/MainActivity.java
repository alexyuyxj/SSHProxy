package m.sshproxy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {
	private IntentFilter filter;
	private BroadcastReceiver receiver;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		findViewById(R.id.btnStart).setOnClickListener(this);
		findViewById(R.id.btnStop).setOnClickListener(this);
		
		filter = new IntentFilter(SSHService.ACTION_STATE);
		receiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				MainActivity.this.onReceive(context, intent);
			}
		};
	}

	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.btnStart: {
				Intent ii = new Intent(this, SSHService.class);
				ii.putExtra("action", SSHService.ACTION_START);
				ii.putExtra("server", "<ip address of ssh server>");
				ii.putExtra("account", "<ssh user name>");
				ii.putExtra("password", "<ssh password>");
				startService(ii);
			} break;
			case R.id.btnStop: {
				Intent ii = new Intent(this, SSHService.class);
				ii.putExtra("action", SSHService.ACTION_STOP);
				ii.putExtra("server", "<ip address of ssh server>");
				startService(ii);
			} break;
		}
	}
	
	protected void onResume() {
		super.onResume();
		registerReceiver(receiver, filter);
	}
	
	protected void onPause() {
		unregisterReceiver(receiver);
		super.onPause();
	}
	
	public void onDestroy() {
		super.onDestroy();
		System.exit(0);
	}
	
	private void onReceive(Context context, Intent intent) {
		int state = intent.getIntExtra("state", 0);
		Toast.makeText(this, "state: " + state, Toast.LENGTH_SHORT).show();
		
		if (state == SSHService.STATE_STARTED || state == SSHService.STATE_STOPED) {
			WebView wvBody = (WebView) findViewById(R.id.wvBody);
			wvBody.setWebViewClient(new WebViewClient() {
				public boolean shouldOverrideUrlLoading(WebView view, String url) {
					view.loadUrl(url);
					return true;
				}
			});
			wvBody.loadUrl("http://www.ip138.com");
		}
	}

}
