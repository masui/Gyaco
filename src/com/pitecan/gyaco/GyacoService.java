//
// Widgetを使うには様々な技法というかバッドノウハウが必要らしい。
// Widgetの画面は他のプロセス(ホームスクリーンプロセス)が管理しているので
// これを操作するには「RemoteViews」というクラスを使って間接的にやるらしい。
// http://developer.android.com/reference/android/widget/RemoteViews.html
// これに画像を貼ることはできる
//
// 直接書く方法もあるのかも
// http://stackoverflow.com/questions/2181588
// http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=core/java/android/widget/AnalogClock.java;hb=HEAD
// http://developer.android.com/guide/topics/appwidgets/index.html#CreatingLayout
// と思ったらAnalogClockだけ許されているという話も!? 今のところよくわからない。
//

//
// Serviceのライフサイクル
//
//   onCreate()    サービス起動
//   onStart()     サービス開始
//   (実行中)
//   onDestroy()   サービス終了
//

package com.pitecan.gyaco;
import java.io.*;
import java.util.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
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
    private static final String ACTION_DEL = "com.pitecan.GyacoService.ACTION_DEL";

    private static MediaPlayer player;
    private static MediaRecorder recorder;
    private static FileInputStream playerStream = null;
    private static FileOutputStream recorderStream = null;

    private RemoteViews remoteViews;
    AppWidgetManager manager;

    // context.startService(intent); により呼ばれる
    //サービス開始時に呼ばれる
    @Override
	public void onStart(Intent intent,int startId) {
	super.onStart(intent, startId);
	Log.v("Gyaco", "Widget onStart");

	//
	// Widgetは「リモートビュー」を使うことになっているらしい
	//
	manager = AppWidgetManager.getInstance(this);
	remoteViews = new RemoteViews(getPackageName(),R.layout.gyaco);

	String action = intent.getAction();
	if (ACTION_PLAY.equals(action)){
	    Log.v("Gyaco", "PlayButton Click");
	    playButton();
	}
	if (ACTION_REC.equals(action)){
	    Log.v("Gyaco", "RecButton Click");
	    recButton();
	}
	if (ACTION_DEL.equals(action)){
	    Log.v("Gyaco", "DelButton Click");
	    delButton();
	}
	
	// playボタンクリックイベントの関連付け
	Intent playIntent = new Intent();
	playIntent.setAction(ACTION_PLAY);
	PendingIntent playPendingIntent = PendingIntent.getService(this,0,playIntent,0);
	remoteViews.setOnClickPendingIntent(R.id.playbutton,playPendingIntent);
	
	// recボタンクリックイベントの関連付け
	Intent recIntent = new Intent();
	recIntent.setAction(ACTION_REC);
	PendingIntent recPendingIntent = PendingIntent.getService(this,0,recIntent,0);
	remoteViews.setOnClickPendingIntent(R.id.recbutton,recPendingIntent);

	//
	// webButton とボタンクリックイベントの関連付け
	// 普通のアプリケーションからIntentで別アプリケーションを呼び出す方法は使えない。
	// 以下を参照
	// https://groups.google.com/group/android-sdk-japan/browse_thread/thread/fd069d05bcdfd2b3?hl=ja
	// http://www.developer.com/ws/article.php/10927_3837531_1/Handling-User-Interaction-with-Android-App-Widgets.htm
	//
	Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://pitecan.com/"));
	PendingIntent webPendingIntent = PendingIntent.getActivity(this, 0, webIntent, 0);
	remoteViews.setOnClickPendingIntent(R.id.webbutton, webPendingIntent); 
	
	// delボタンクリックイベントの関連付け
	Intent delIntent = new Intent();
	delIntent.setAction(ACTION_DEL);
	PendingIntent delPendingIntent = PendingIntent.getService(this,0,delIntent,0);
	remoteViews.setOnClickPendingIntent(R.id.delbutton,delPendingIntent);

	//ホームウィジェットの更新
	ComponentName widget = new ComponentName(this,Gyaco.class);
	manager.updateAppWidget(widget,remoteViews);
    }

    @Override
    public IBinder onBind(Intent intent) {
	return null;
    }
    
    public void playButton(){
	//
	// ネットが使える環境では新しい音声があればダウンロードする
	//
	if(networkIsAvailable()){
	    downloadIfDataIsObsolete(Consts.DOWNLOAD_MP3,Consts.LOCAL_MP3);
	}
	play(Consts.LOCAL_MP3);
    }

    public void delButton(){
	try {
	    deleteAll();
	}
        catch(Exception e){
	    e.printStackTrace();
	}
    }

    private Handler handler = new Handler();
    private Runnable step;
    private Context context = this;
    private int recCount = 0; // 録音タイマ: 10からはじめて0になると録音終了

    public void recButton(){
	Log.v("Gyaco", "REC button clicked");
	try{
	    if(recCount == 0){
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
		recorderStream = openFileOutput(Consts.LOCAL_3GP, MODE_PRIVATE);
		recorder.setOutputFile(recorderStream.getFD());
		recorder.prepare();
		recorder.start();
		Log.v("Gyaco","record start");

		recCount = 10;
		displayRecIcon();

		step = new Runnable() {
			public void run() {
			    if(recCount > 0){
				recCount--;
			    }
			    displayRecIcon();

			    if(recCount == 0){ // 録音終了
				handler.removeCallbacks(step);
				try {
				    Log.v("Gyaco","record step");
				    recorder.stop();
				    recorder.release();
				    recorder = null;
				    recorderStream.close();
				    try{
					uploadLocal(Consts.LOCAL_3GP, Consts.UPLOAD_URL);
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
			    else { // 録音続行
				handler.postDelayed(step, 200); // 0.2秒後にstep
			    }
			}
		    };
		handler.postDelayed(step, 200); // 0.2秒後にstep
	    }
	    else { // 録音タイマをリセットして録音続行
		recCount = 10;
		displayRecIcon();
	    }
	}
	catch(Exception e){
	    e.printStackTrace();
	    Log.v("Gyaco",e.toString());
	}
    }

    public void displayRecIcon(){
	int[] recIcons = {
	    R.drawable.rec0, R.drawable.rec1, R.drawable.rec2,
	    R.drawable.rec3, R.drawable.rec4, R.drawable.rec5,
	    R.drawable.rec6, R.drawable.rec7, R.drawable.rec8,
	    R.drawable.rec9, R.drawable.rec10
	};
	remoteViews.setImageViewResource(R.id.recbutton,recIcons[recCount]);
	manager.updateAppWidget(new ComponentName(getApplicationContext(), Gyaco.class), remoteViews); 
	// Log.v("Gyaco", "dispRecIcon(" + recCount + ")");
    }

    public void uploadLocal(String localfile, String uri){
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

    public void deleteAll() throws Exception{
        try{
	    Log.v("Gyaco","deleting everything....");
            HttpClient httpClient = new DefaultHttpClient();
            HttpGet get = new HttpGet("http://masui.sfc.keio.ac.jp/gyaco/delete_all");
            get.setHeader("User-Agent", "TestAndroidApp/0.1");
            HttpResponse res = httpClient.execute(get);
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
			e.printStackTrace();
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
