package com.truethat.android.main;

import android.app.Application;
import com.sendbird.android.SendBird;

public class BaseApplication extends Application {

  public static final String VERSION = "3.0.38";
  private static final String APP_ID = "5A2C83D8-3C58-47CE-B31A-ED758808A79F"; // US-1 Demo

  @Override public void onCreate() {
    super.onCreate();
    SendBird.init(APP_ID, getApplicationContext());
  }
}
