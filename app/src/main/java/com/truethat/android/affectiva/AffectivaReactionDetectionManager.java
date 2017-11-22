package com.truethat.android.affectiva;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.HandlerThread;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

/**
 * Proudly created by ohad on 08/06/2017 for TrueThat.
 * <p>
 * A wrapper for Affectiva emotion detection engine.
 */
public class AffectivaReactionDetectionManager {
  public static final int PERMISSION_CAMERA = 255;
  String LOG_TAG = AffectivaReactionDetectionManager.class.getSimpleName();
  private HandlerThread detectionThread;
  private DetectionHandler detectionHandler;

  public DetectionHandler getDetectionHandler() {
    return detectionHandler;
  }

  public void start(AppCompatActivity activity) {
    Log.d(LOG_TAG, "start");
    if (requestCameraPermissions(activity)) {
      if (detectionThread == null) {
        // fire up the background thread
        detectionThread = new DetectionThread();
        detectionThread.start();
        detectionHandler = new DetectionHandler(activity, detectionThread);
        detectionHandler.sendStartMessage();
      }
    }
  }

  public void stop() {
    Log.d(LOG_TAG, "stop");
    if (detectionHandler != null) {
      detectionHandler.sendStopMessage();
      try {
        detectionThread.join();
        detectionThread = null;
        detectionHandler = null;
      } catch (InterruptedException ignored) {
      }
    }
  }

  private boolean requestCameraPermissions(final AppCompatActivity activity) {
    Log.d(LOG_TAG, "requestCameraPermissions");
    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
      Log.d(LOG_TAG, "permission already granted");
      return true;
    }
    if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
      // Provide an additional rationale to the user if the permission was not granted
      // and the user would benefit from additional context for the use of the permission.
      // For example if the user has previously denied the permission.
      Snackbar.make(activity.findViewById(android.R.id.content),
          "Camera access required to detect facial emojis.", Snackbar.LENGTH_LONG)
          .setAction("Okay", new View.OnClickListener() {
            @Override public void onClick(View view) {
              ActivityCompat.requestPermissions(activity,
                  new String[] { Manifest.permission.CAMERA }, PERMISSION_CAMERA);
            }
          })
          .show();
    } else {
      Log.d(LOG_TAG, "requesting...");
      // Permission has not been granted yet. Request it directly.
      ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.CAMERA },
          PERMISSION_CAMERA);
    }
    return false;
  }

  private static class DetectionThread extends HandlerThread {
    private DetectionThread() {
      super("ReactionDetectionThread");
    }
  }
}
