package com.pitecan.gyaco;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;

     
//ホームウィジェットを制御するサービス
public class GyacoService extends Service {
    private static final String ACTION_BTNCLICK =
        "com.pitecan.GyacoService.ACTION_BTNCLICK";

    private static MediaPlayer mp;
    private Timer timer;
    
    //サービス開始時に呼ばれる
    @Override
    public void onStart(Intent intent,int startId) {
        super.onStart(intent, startId);
        
	mp = MediaPlayer.create(this, R.raw.cabbage);

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
     
    //バインダーを返す
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
     
    //ボタンクリック時に呼ばれる
    public void btnClicked(RemoteViews view){
	mp.start(); // 音声再生
    }
	private void playSound(){
		FileInputStream fs = null;
		MediaPlayer mp = new MediaPlayer();
		mp.setOnCompletionListener(new OnCompletionListener(){
			@Override
			public void onCompletion(MediaPlayer mp) {
			}});
		try {
			fs = new FileInputStream(Consts.PATH_TO_SOUND_FILE);
			if(fs != null){
				mp.setDataSource(fs.getFD());
				mp.prepare();
				mp.start();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}	
	}
	private void downloadSoundData(){
		//notification();
		download();
		if(checkHash()){
			//
		}
	}
	private void download() {
		if (timer != null) {
			timer.cancel();
		}
		timer = new Timer();
		TimerTask timerTask = new TimerTask() {			
			public void run() {
			    Downloader d = new Downloader();
			    d.setConnectTimeout(Consts.CONNECT_TIMEOUT); 
			    d.setReadTimeout(Consts.READ_TIMEOUT); 
			    byte[] b;
				try {
					Log.v("Download finished", "Download finished");
					b = d.getContent(Consts.DOWNLOAD_URL);
				    FileOutputStream fos = new FileOutputStream(Consts.PATH_TO_SAVE);
				    fos.write(b);
				    fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		timer.schedule(timerTask, Consts.DOWNLOAD_DELAY);
	}
	private boolean checkHash(){
		return true;
	}

}
