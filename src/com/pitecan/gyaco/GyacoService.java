package com.pitecan.gyaco;
import java.io.*;
import java.util.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.*;
import android.content.*;
import android.appwidget.AppWidgetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;
import android.os.Handler;
import android.os.IBinder;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaPlayer.OnCompletionListener;

//ホームウィジェットを制御するサービス
public class GyacoService extends Service {
    private static final String ACTION_PLAY = "com.pitecan.GyacoService.ACTION_PLAY";
    private static final String ACTION_REC = "com.pitecan.GyacoService.ACTION_REC";

    private static MediaPlayer player;
    private static MediaRecorder recorder;
    private static FileInputStream playerStream = null;

    //サービス開始時に呼ばれる
    @Override
	public void onStart(Intent intent,int startId) {
	super.onStart(intent, startId);
	Log.v("Gyaco", "Widget onStart");

	//
	// Widgetは「リモートビュー」を使うことになっているらしい
	//
	AppWidgetManager manager=AppWidgetManager.getInstance(this);
	RemoteViews view=new RemoteViews(getPackageName(),R.layout.gyaco);

	if (ACTION_PLAY.equals(intent.getAction())) {
	    Log.v("Gyaco", "PlayButton Click");
	    playButton(view);
	}
	if (ACTION_REC.equals(intent.getAction())) {
	    Log.v("Gyaco", "RecButton Click");
	    recButton(view);
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

	//
	// button3とボタンクリックイベントの関連付け
	// 普通のアプリケーションからIntentで別アプリケーションを呼び出す方法は使えない。
	// 以下を参照
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
    
    public void playButton(RemoteViews view){
	//
	// ネットが使える環境では新しい音声があればダウンロードする
	//
	if(networkIsAvailable()){
	    downloadIfDataIsObsolete(Consts.DOWNLOAD_MP3,Consts.LOCAL_MP3);
	}
	play(Consts.LOCAL_MP3);
    }

    private Handler handler = new Handler();
    private Runnable stop;

    public void recButton(RemoteViews view){
	Log.v("Gyaco", "Widget onClick");
	try{
	    recorder = new MediaRecorder();
	    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
	    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
	    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
	    //
	    // MediaRecorderの出力ファイルは絶対パスを利用することもできるが、
	    // Activityのメソッドである openFileOutput() を使ってローカルファイルの出力ストリームを取得して
	    // 指定することもできる。
	    //
	    // 絶対パスを使う場合は以下のようにすればよい。
	    // String filePath = "/data/data/" + this.getPackageName() + "/files/" + Consts.LOCAL_3GP
	    // recorder.setOutputFile(filePath);
	    //
	    FileOutputStream os = openFileOutput(Consts.LOCAL_3GP, MODE_PRIVATE);
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
			    try{
				upload_local(Consts.LOCAL_3GP, Consts.UPLOAD_URL);
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

    public void upload_local(String localfile, String uri){
        try{
	    //
	    // android.context.Context の getFileStreamPath() は
	    // アプリケーションが使うローカルファイル名前からFileオブジェクトを返す。
	    // アプリケーションが使うローカルファイルは /data/data/(パッケージ名)/files/ に置かれるので
	    // new File("/data/data/" + getPackageName() + "/files/" + ローカルファイル名) と同じことだと思う。
	    //
	    File upfile = getFileStreamPath(localfile);
	    upload(upfile, uri);
	}
        catch(Exception e){
	    e.printStackTrace();
	}
    }

    public void upload(File file, String uri) throws Exception{
        try{
	    Log.v("Gyaco","upload....");
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost post = new HttpPost(uri);
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            entity.addPart("file", new FileBody(file));
            post.setEntity(entity);
            post.setHeader("User-Agent", "TestAndroidApp/0.1");
            HttpResponse res = httpClient.execute(post);
            Log.v("Gyaco","res = " + res.getEntity().getContent().toString());
        }
        catch(Exception e){
            throw e;
        }
    }

    private void play(String localfile){
	player = new MediaPlayer();
	player.setOnCompletionListener(new OnCompletionListener(){
		@Override
		    public void onCompletion(MediaPlayer player) {
		    try {
			player.release();
			playerStream.close();
		    }
		    catch(Exception e){
		    }
		}
	    });
	try {
	    playerStream = openFileInput(localfile);
	    if(playerStream != null){
		player.setDataSource(playerStream.getFD());
		player.prepare();
		player.start();
	    }
	} catch(Exception e) {
	    e.printStackTrace();
	}	
    }

    private void download(String downloadUri, String localfile) {
	Downloader d = new Downloader();
	d.setConnectTimeout(Consts.CONNECT_TIMEOUT); 
	d.setReadTimeout(Consts.READ_TIMEOUT); 
	byte[] b;
	try {
	    b = d.getContent(downloadUri);
	    Log.v("Gyaco", "Download finished");
	    FileOutputStream fos = openFileOutput(localfile, MODE_PRIVATE);
	    fos.write(b);
	    fos.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    private boolean networkIsAvailable(){
	ConnectivityManager conn_manager = (ConnectivityManager) this.getSystemService(CONNECTIVITY_SERVICE);
	NetworkInfo netinfo = conn_manager.getActiveNetworkInfo();
	if(netinfo != null && netinfo.isConnected()){
	    return true;
	}
	return false;
    }

    private static long lastModifiedDate = 0;

    private void downloadIfDataIsObsolete(String downloadUri, String localfile){
	Downloader d = new Downloader();
	d.setConnectTimeout(Consts.CONNECT_TIMEOUT); 
	d.setReadTimeout(Consts.READ_TIMEOUT);
	long date = 0;
	try {
	    date  = d.getLastModified(downloadUri);
	} catch (IOException e) {
	    e.printStackTrace();
	}
	if(date != lastModifiedDate){
	    download(downloadUri,localfile);
	    lastModifiedDate = date;
	}
    }
}
