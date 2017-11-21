package com.truethat.android.affectiva;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import com.crashlytics.android.Crashlytics;
import com.truethat.android.BuildConfig;
import java.io.IOException;

/**
 * A wrapper class to enforce thread-safe access to the camera and its properties.
 */
@SuppressWarnings({ "deprecation", "SameParameterValue" }) class SafeCamera {
  private Camera camera;
  private volatile int cameraId = -1;
  @SuppressWarnings("unused") private boolean taken;

  /**
   * Attempts to open the specified camera.
   *
   * @param cameraToOpen one of {@link Camera.CameraInfo#CAMERA_FACING_BACK}
   *                     or {@link Camera.CameraInfo#CAMERA_FACING_FRONT}
   *
   * @throws IllegalStateException if the device does not have a camera of the requested type or
   *                               the camera is already locked by another process
   */
  SafeCamera(int cameraToOpen) throws IllegalStateException {

    int cnum = Camera.getNumberOfCameras();
    Camera.CameraInfo caminfo = new Camera.CameraInfo();

    for (int i = 0; i < cnum; i++) {
      Camera.getCameraInfo(i, caminfo);
      if (caminfo.facing == cameraToOpen) {
        cameraId = i;
        break;
      }
    }
    if (cameraId == -1) {
      throw new IllegalStateException("This device does not have a camera of the requested type");
    }
    try {
      camera = Camera.open(cameraId); // attempt to get a Camera instance.
    } catch (RuntimeException e) {
      if (!BuildConfig.DEBUG) {
        Crashlytics.logException(e);
      }
      // Camera is not available (in use or does not exist). Translate to a more appropriate exception type.
      String msg =
          "Camera is unavailable. Please close the app that is using the camera and then try again.\n"
              + "Error:  "
              + e.getMessage();
      throw new IllegalStateException(msg, e);
    }
  }

  synchronized Camera.Parameters getParameters() {
    checkTaken();
    return camera.getParameters();
  }

  synchronized void setParameters(Camera.Parameters parameters) {
    checkTaken();
    camera.setParameters(parameters);
  }

  synchronized void addCallbackBuffer(byte[] buffer) {
    checkTaken();
    camera.addCallbackBuffer(buffer);
  }

  synchronized void setPreviewCallbackWithBuffer(Camera.PreviewCallback callback) {
    checkTaken();
    camera.setPreviewCallbackWithBuffer(callback);
  }

  synchronized void setPreviewCallback(Camera.PreviewCallback callback) {
    checkTaken();
    camera.setPreviewCallback(callback);
  }

  synchronized void setPreviewTexture(SurfaceTexture texture) throws IOException {
    checkTaken();
    camera.setPreviewTexture(texture);
  }

  synchronized void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
    checkTaken();
    camera.setOneShotPreviewCallback(callback);
  }

  synchronized void startPreview() {
    checkTaken();
    camera.startPreview();
  }

  synchronized void stopPreview() {
    checkTaken();
    camera.stopPreview();
  }

  synchronized void setDisplayOrientation(int degrees) {
    checkTaken();
    camera.setDisplayOrientation(degrees);
  }

  synchronized Camera.CameraInfo getCameraInfo() {
    checkTaken();
    Camera.CameraInfo result = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, result);
    return result;
  }

  synchronized void release() {
    checkTaken();

    try {
      camera.release();
      camera = null;
    } catch (Exception e) {
      //do nothing, exception thrown because camera was already closed or camera was null (if it failed to open at all)
    }
  }

  private void checkTaken() throws IllegalStateException {
    if (taken) {
      throw new IllegalStateException(
          "cannot take or interact with camera while it has been taken");
    }
  }
}