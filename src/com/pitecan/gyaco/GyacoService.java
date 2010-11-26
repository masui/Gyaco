package com.pitecan.gyaco;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import android.os.Handler;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaPlayer.OnCompletionListener;

import android.os.Environment;


//ホームウィジェットを制御するサービス
public class GyacoService extends Service {
    private static final String ACTION_PLAY = "com.pitecan.GyacoService.ACTION_PLAY";
    private static final String ACTION_REC = "com.pitecan.GyacoService.ACTION_REC";

    private static MediaPlayer player;
    private static MediaRecorder recorder;
    private long lastmodified = 0;

    //サービス開始時に呼ばれる
    @Override
	public void onStart(Intent intent,int startId) {
	super.onStart(intent, startId);
	Log.v("Gyaco", "Widget onStart");

	//リモートビューの取得
	// リモートビューというのはWidgetのビューのこと
	AppWidgetManager manager=AppWidgetManager.getInstance(this);
	RemoteViews view=new RemoteViews(getPackageName(),R.layout.gyaco);

	if (ACTION_PLAY.equals(intent.getAction())) {
	    play(view);
	}
	if (ACTION_REC.equals(intent.getAction())) {
	    rec(view);
	}
	
	//button1とボタンクリックイベントの関連付け
	Intent newintent1=new Intent();
	newintent1.setAction(ACTION_PLAY);
	PendingIntent pending1=PendingIntent.getService(this,0,newintent1,0);
	view.setOnClickPendingIntent(R.id.button1,pending1);
	
	//button2とボタンクリックイベントの関連付け
	Intent newintent2=new Intent();
	newintent2.setAction(ACTION_REC);
	PendingIntent pending2=PendingIntent.getService(this,0,newintent2,0);
	view.setOnClickPendingIntent(R.id.button2,pending2);
	
	//button3とボタンクリックイベントの関連付け
	// ブラウザを呼び出す
	// https://groups.google.com/group/android-sdk-japan/browse_thread/thread/fd069d05bcdfd2b3?hl=ja
	// http://www.developer.com/ws/article.php/10927_3837531_1/Handling-User-Interaction-with-Android-App-Widgets.htm
	//
	Intent newintent3 = new Intent(Intent.ACTION_VIEW, Uri.parse("http://pitecan.com/"));
	PendingIntent pending3 = PendingIntent.getActivity(this, 0, newintent3, 0);
	view.setOnClickPendingIntent(R.id.button3, pending3); 
	
	//ホームウィジェットの更新
	ComponentName widget=new ComponentName(this,Gyaco.class);
	manager.updateAppWidget(widget,view);
    }

    @Override
    public IBinder onBind(Intent intent) {
	return null;
    }
    
    public void play(RemoteViews view){
	Log.v("Gyaco", "Widget onClick");
	downloadAndPlay();
    }

    private Handler handler = new Handler();
    private Runnable stop;

    public void rec(RemoteViews view){
	Log.v("Gyaco", "Widget onClick");
	try{
	    recorder = new MediaRecorder();
	    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
	    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
	    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

	    //String dataDir = "/data/data/" + this.getPackageName() + "/files";
	    //String fileName = "gyaco.3gp";
                    
	    //this.recorder.setOutputFile(new File(dataDir, fileName).getAbsolutePath());

	    /*
	    String filePath = "/data/data/" + this.getPackageName() + "/files/gyaco.3gp";
	    recorder.setOutputFile(filePath);
	    */
	    FileOutputStream os = openFileOutput("gyaco.3gp", MODE_PRIVATE);
	    Log.v("Gyaco","os = "  + os);
	    recorder.setOutputFile(os.getFD());

	    recorder.prepare();
	    recorder.start();
	    Log.v("Gyaco","record start");

	    stop = new Runnable() {
		    public void run() {
			handler.removeCallbacks(stop);
			try {
			    Log.v("Gyaco","record stop");
			    recorder.stop();
			    recorder.release();
			    recorder = null;

			    File f = new File("/data/data/" + getPackageName() + "/files/gyaco.3gp");
			    Log.v("Gyaco", "length="+f.length());
			    try{
				upload(new File("/data/data/" + getPackageName() + "/files/gyaco.3gp"), "http://masui.sfc.keio.ac.jp/gyaco/upload");
			    }
			    catch(Exception e){
				e.printStackTrace();
				Log.v("Gyaco",e.toString());
			    }
			}
			catch(Exception e){
			    e.printStackTrace();
			    Log.v("Gyaco",e.toString());
			}
		    }
		};
	    handler.postDelayed(stop, 2000);

	}
	catch(Exception e){
	    e.printStackTrace();
	    Log.v("Gyaco",e.toString());
	}
    }

    public void upload(File file, String uri) throws Exception{
        try{
	    Log.v("Gyaco","upload....");
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost post = new HttpPost(uri);
                         
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            //entity.addPart("file", new FileBody(file));
            //entity.addPart("file", new FileBody(new File("gyaco.3gp")));
            entity.addPart("file", new FileBody(getFileStreamPath("gyaco.3gp")));
	    

            post.setEntity(entity);
            post.setHeader("User-Agent", "TestAndroidApp/0.1");
            HttpResponse res = httpClient.execute(post);
            Log.v("Gyaco","res = " + res.getEntity().getContent().toString());
        }
        catch(Exception e){
            throw e;
        }
    }

    FileInputStream fs = null;

    private void play(){
	/* MediaPlayer */ player = new MediaPlayer();
	player.setOnCompletionListener(new OnCompletionListener(){
		@Override
		    public void onCompletion(MediaPlayer player) {
		    try {
			player.release();
			fs.close();
		    }
		    catch(Exception e){
		    }
		}
	    });
	try {
	    fs = openFileInput(Consts.FILENAME);
	    if(fs != null){
		player.setDataSource(fs.getFD());
		player.prepare();
		player.start();
	    }
	} catch(Exception e) {
	    e.printStackTrace();
	}	
    }

    private void downloadAndPlay(){
	if(checkConnectionStatus()){
	    if(isDataObsolete()){
		download();
	    }
	}
	play();
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
