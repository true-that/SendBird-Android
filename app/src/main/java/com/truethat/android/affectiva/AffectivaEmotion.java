package com.truethat.android.affectiva;

import com.truethat.android.common.Emotion;

/**
 * Proudly created by ohad on 21/11/2017 for TrueThat.
 */

public enum AffectivaEmotion {
  JOY(Emotion.HAPPY), FEAR(Emotion.OMG), DISGUST(Emotion.DISGUST), SURPRISE(Emotion.OMG), ANGER(
      Emotion.DISGUST), SADNESS(Emotion.DISGUST);

  private Emotion mEmotion;

  AffectivaEmotion(Emotion emotion) {
    mEmotion = emotion;
  }

  public Emotion getEmotion() {
    return mEmotion;
  }
}
