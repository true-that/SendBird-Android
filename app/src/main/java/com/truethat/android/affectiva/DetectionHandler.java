package com.truethat.android.affectiva;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.Detector;
import com.affectiva.android.affdex.sdk.detector.Face;
import com.affectiva.android.affdex.sdk.detector.FrameDetector;
import com.crashlytics.android.Crashlytics;
import com.truethat.android.BuildConfig;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proudly created by ohad on 31/07/2017 for TrueThat.
 * <p>
 * A handler for the DetectionThread.
 */
public class DetectionHandler extends Handler {
  private static final double DETECTION_THRESHOLD = 200;
  //Incoming message codes
  private static final int START = 0;
  private static final int STOP = 1;
  String LOG_TAG = DetectionHandler.class.getSimpleName();
  private String TAG = this.getClass().getSimpleName();
  private CameraHelper mCameraHelper;
  private FrameDetector mFrameDetector;
  private SurfaceTexture mSurfaceTexture;
  private ReactionDetectionListener mDetectionListener;
  private Map<AffectivaEmotion, Float> emotionToLikelihood;

  DetectionHandler(Context context, HandlerThread detectionThread) {
    // note: getLooper will block until the the thread's looper has been prepared
    super(detectionThread.getLooper());
    resetLikelihoodMap();
    Display display =
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    mCameraHelper = new CameraHelper(context, display, new DetectionHandler.CameraHelperListener());
    mSurfaceTexture = new SurfaceTexture(0); // a dummy texture

    // Set up the FrameDetector.  For the purposes of this sample app, we'll just request
    // listen for face events and request valence scores.
    mFrameDetector = new FrameDetector(context);
    mFrameDetector.setDetectAllEmotions(true);
    mFrameDetector.setFaceListener(new Detector.FaceListener() {
      @Override public void onFaceDetectionStarted() {
        resetLikelihoodMap();
        Log.d(LOG_TAG, "onFaceDetectionStarted.");
      }

      @Override public void onFaceDetectionStopped() {
        resetLikelihoodMap();
        Log.d(LOG_TAG, "onFaceDetectionStopped");
      }
    });
    mFrameDetector.setImageListener(new Detector.ImageListener() {
      @Override public void onImageResults(List<Face> faces, Frame frame, float v) {
        for (final Face face : faces) {
          emotionToLikelihood.put(AffectivaEmotion.JOY,
              emotionToLikelihood.get(AffectivaEmotion.JOY) + face.emotions.getJoy());
          emotionToLikelihood.put(AffectivaEmotion.SURPRISE,
              emotionToLikelihood.get(AffectivaEmotion.SURPRISE) + face.emotions.getSurprise());
          emotionToLikelihood.put(AffectivaEmotion.ANGER,
              emotionToLikelihood.get(AffectivaEmotion.ANGER) + face.emotions.getAnger());
          // Fear is harder to detect, and so it is amplified
          emotionToLikelihood.put(AffectivaEmotion.FEAR,
              emotionToLikelihood.get(AffectivaEmotion.FEAR) + face.emotions.getFear() * 2);
          // Negative emotions are too easy to detect, and so they are decreased
          emotionToLikelihood.put(AffectivaEmotion.SADNESS,
              emotionToLikelihood.get(AffectivaEmotion.SADNESS) + face.emotions.getSadness() / 2);
          emotionToLikelihood.put(AffectivaEmotion.DISGUST,
              emotionToLikelihood.get(AffectivaEmotion.DISGUST) + face.emotions.getDisgust() / 2);
          Map.Entry<AffectivaEmotion, Float> mostLikely =
              Collections.max(emotionToLikelihood.entrySet(),
                  new Comparator<Map.Entry<AffectivaEmotion, Float>>() {
                    @Override
                    public int compare(Map.Entry<AffectivaEmotion, Float> emotionFloatEntry,
                        Map.Entry<AffectivaEmotion, Float> t1) {
                      return emotionFloatEntry.getValue().compareTo(t1.getValue());
                    }
                  });
          if (mDetectionListener != null) {
            for (Map.Entry<AffectivaEmotion, Float> emotionLikelihoodEntry : emotionToLikelihood.entrySet()) {
              if (emotionLikelihoodEntry.getKey() != mostLikely.getKey()
                  && emotionLikelihoodEntry.getValue() > DETECTION_THRESHOLD) {
                mDetectionListener.onReactionDetected(emotionLikelihoodEntry.getKey().getEmotion(),
                    false);
              }
            }
            if (mostLikely.getValue() > DETECTION_THRESHOLD) {
              mDetectionListener.onReactionDetected(mostLikely.getKey().getEmotion(), true);
            }
          }
          if (mostLikely.getValue() > DETECTION_THRESHOLD) {
            Log.d(LOG_TAG, "Detected " + mostLikely.getKey().getEmotion());
            resetLikelihoodMap();
          }
        }
      }
    });
  }

  public void setDetectionListener(ReactionDetectionListener detectionListener) {
    resetLikelihoodMap();
    mDetectionListener = detectionListener;
  }

  /**
   * Process incoming messages
   *
   * @param msg message to handle
   */
  @Override public void handleMessage(Message msg) {
    switch (msg.what) {
      case START:
        Log.d(TAG, "starting background processing of frames");
        try {
          mFrameDetector.start();
          //noinspection deprecation
          mCameraHelper.acquire(Camera.CameraInfo.CAMERA_FACING_FRONT);
          mCameraHelper.start(mSurfaceTexture); // initiates previewing
        } catch (IllegalStateException e) {
          if (!BuildConfig.DEBUG) {
            Crashlytics.logException(e);
          }
          e.printStackTrace();
          Log.d(TAG, "couldn't open camera: " + e.getMessage());
          // TODO(ohad): Let user know via UI
          return;
        }
        break;
      case STOP:
        Log.d(TAG, "stopping background processing of frames");
        mCameraHelper.stop(); // stops previewing
        mCameraHelper.release();
        mFrameDetector.stop();

        Log.d(TAG, "quitting detection thread");
        ((HandlerThread) getLooper().getThread()).quit();
        break;

      default:
        break;
    }
  }

  /**
   * asynchronously start processing on the background thread
   */
  void sendStartMessage() {
    sendMessage(obtainMessage(START));
  }

  /**
   * asynchronously stop processing on the background thread
   */
  void sendStopMessage() {
    sendMessage(obtainMessage(STOP));
  }

  private void resetLikelihoodMap() {
    emotionToLikelihood = new HashMap<>();
    emotionToLikelihood.put(AffectivaEmotion.JOY, 0F);
    emotionToLikelihood.put(AffectivaEmotion.SURPRISE, 0F);
    emotionToLikelihood.put(AffectivaEmotion.ANGER, 0F);
    emotionToLikelihood.put(AffectivaEmotion.FEAR, 0F);
    emotionToLikelihood.put(AffectivaEmotion.DISGUST, 0F);
    emotionToLikelihood.put(AffectivaEmotion.SADNESS, 0F);
  }

  /**
   * A mListener for CameraHelper callbacks
   */
  private class CameraHelperListener implements CameraHelper.Listener {
    private static final long TIMESTAMP_DELTA_MILLIS = 100;
    private Date lastTimestamp = new Date();
    private float detectionTimestamp = 0;

    @Override
    public void onFrameAvailable(byte[] frame, int width, int height, Frame.ROTATE rotation) {
      Date timeStamp = new Date();
      if (timeStamp.getTime() > lastTimestamp.getTime() + TIMESTAMP_DELTA_MILLIS) {
        lastTimestamp = timeStamp;
        detectionTimestamp += 0.1;
        mFrameDetector.process(createFrameFromData(frame, width, height, rotation),
            detectionTimestamp);
      }
    }

    @Override public void onFrameSizeSelected(int width, int height, Frame.ROTATE rotation) {
    }

    private Frame createFrameFromData(byte[] frameData, int width, int height,
        Frame.ROTATE rotation) {
      Frame.ByteArrayFrame frame =
          new Frame.ByteArrayFrame(frameData, width, height, Frame.COLOR_FORMAT.YUV_NV21);
      frame.setTargetRotation(rotation);
      return frame;
    }
  }
}
