package com.truethat.android.main;

import android.app.Application;
import android.util.Log;
import com.crashlytics.android.Crashlytics;
import com.sendbird.android.SendBird;
import io.fabric.sdk.android.Fabric;

public class BaseApplication extends Application {

  public static final String VERSION = "3.0.38";
  private static final String APP_ID = "5A2C83D8-3C58-47CE-B31A-ED758808A79F"; // US-1 Demo

  @Override public void onCreate() {
    super.onCreate();
    for (int i = 0; i < 10; i++) {
      Log.e("Truedat",
          "************************ !!!!! LAUNCHED !!!!! ************************************************ !!!!! LAUNCHED !!!!! ************************");
    }
    SendBird.init(APP_ID, getApplicationContext());
    Fabric.with(this, new Crashlytics());
  }
}
