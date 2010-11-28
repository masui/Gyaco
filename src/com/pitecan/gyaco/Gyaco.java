package com.pitecan.gyaco;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

//
// AppWidgetのライフサイクル
//
//   onEnabled()    最初の起動
//   onUpdate()     appWidgetが起動される
//   (実行中)
//   onDeleted()    起動したappWidgetが終了
//   onDisabled()   すべてのappWiddgetが終了
//

//ホームウィジェット
public class Gyaco extends AppWidgetProvider {
    //更新時に呼ばれる
    @Override
    public void onUpdate(Context context,
        AppWidgetManager appWidgetManager,int[] appWidgetIds) {
        //ホームウィジェットを処理するサービスの実行
        Intent intent=new Intent(context,GyacoService.class);
        context.startService(intent);
    }
}
