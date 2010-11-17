package com.pitecan.gyaco;
import java.util.*;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.IBinder;
import android.widget.RemoteViews;

import android.media.MediaPlayer; /////

     
//ホームウィジェットを制御するサービス
public class GyacoService extends Service {
    private static final String ACTION_BTNCLICK =
        "com.pitecan.GyacoService.ACTION_BTNCLICK";

    private static MediaPlayer mp;
    
    //サービス開始時に呼ばれる
    @Override
    public void onStart(Intent intent,int startId) {
        super.onStart(intent, startId);
        
	mp = MediaPlayer.create(this, R.raw.cabbage); //////

        //リモートビューの取得
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        RemoteViews view=new RemoteViews(getPackageName(),R.layout.appwidget);
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
	mp.start(); //////
    }
}
