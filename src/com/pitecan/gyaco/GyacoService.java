package com.pitecan.gyaco;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;

import android.os.Environment;


//ホームウィジェットを制御するサービス
public class GyacoService extends Service {
	private static final String ACTION_BTNCLICK =
		"com.pitecan.GyacoService.ACTION_BTNCLICK";

	private static MediaPlayer mp;
	private long lastmodified = 0;
	private Toast toast;

	//サービス開始時に呼ばれる
	@Override
	public void onStart(Intent intent,int startId) {
		super.onStart(intent, startId);
		Log.v("Gyaco", "Widget onStart");
		//リモートビューの取得
		AppWidgetManager manager=AppWidgetManager.getInstance(this);
		RemoteViews view=new RemoteViews(getPackageName(),R.layout.gyaco);
		if (ACTION_BTNCLICK.equals(intent.getAction())) {
			btnClicked(view);
		}

		//button1とボタンクリックイベントの関連付け
		Intent newintent=new Intent();
		newintent.setAction(ACTION_BTNCLICK);
		PendingIntent pending=PendingIntent.getService(this,0,newintent,0);
		view.setOnClickPendingIntent(R.id.button1,pending);

		//ホームウィジェットの更新
		ComponentName widget=new ComponentName(this,Gyaco.class);
		manager.updateAppWidget(widget,view);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public void btnClicked(RemoteViews view){
		Log.v("Gyaco", "Widget onClick");
		downloadAndPlay();
	}

	private void playSound(){
		FileInputStream fs = null;
		MediaPlayer mp = new MediaPlayer();
		mp.setOnCompletionListener(new OnCompletionListener(){
			@Override
			public void onCompletion(MediaPlayer mp) {
				toast.cancel();
			}});
		try {
			fs = openFileInput(Consts.FILENAME);
			if(fs != null){
				mp.setDataSource(fs.getFD());
				mp.prepare();
				mp.start();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}	
	}

	private void downloadAndPlay(){
		toast = Toast.makeText(this, "Playing...", Toast.LENGTH_LONG);
		toast.show();
		if(checkConnectionStatus()){
			if(isDataObsolete()){
				download();
			}
		}
		playSound();
	}
	
	private void download() {
		Downloader d = new Downloader();
		d.setConnectTimeout(Consts.CONNECT_TIMEOUT); 
		d.setReadTimeout(Consts.READ_TIMEOUT); 
		byte[] b;
		try {
			b = d.getContent(Consts.DOWNLOAD_URL);
			Log.v("Gyaco", "Download finished");
			FileOutputStream fos = openFileOutput(Consts.FILENAME, MODE_PRIVATE);
			fos.write(b);
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean checkConnectionStatus(){
		ConnectivityManager conn_manager = (ConnectivityManager) this.getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo netinfo = conn_manager.getActiveNetworkInfo();
		if(netinfo != null && netinfo.isConnected()){
			return true;
		}
		return false;
	}

	private boolean isDataObsolete() {
		Downloader d = new Downloader();
		d.setConnectTimeout(Consts.CONNECT_TIMEOUT); 
		d.setReadTimeout(Consts.READ_TIMEOUT);
		long date = 0;
		try {
			date  = d.getLastModified(Consts.DOWNLOAD_URL);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(date != this.lastmodified){
			return true;
		}
		return false;
	}
}
