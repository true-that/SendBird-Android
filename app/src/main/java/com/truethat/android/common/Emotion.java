package com.truethat.android.common;

/**
 * Proudly created by ohad on 21/11/2017 for TrueThat.
 */

public enum Emotion {
  HAPPY("😂"), OMG("😱"), DISGUST("😫");

  private String mEmoji;

  Emotion(String emoji) {
    mEmoji = emoji;
  }

  public String getEmoji() {
    return mEmoji;
  }
}