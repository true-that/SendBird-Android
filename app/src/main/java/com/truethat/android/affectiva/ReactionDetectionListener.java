package com.truethat.android.affectiva;

import com.truethat.android.common.Emotion;

/**
 * Proudly created by ohad on 21/11/2017 for TrueThat.
 */
public interface ReactionDetectionListener {
  /**
   * @param reaction   detected on our user's pretty face.
   * @param mostLikely the emotion most likely to represent the user's mood. Used for cases when
   *                   we
   *                   want to detect a specific emotion, regardless of whether it is the
   *                   prevalent
   *                   one.
   */
  void onReactionDetected(Emotion reaction, boolean mostLikely);
}
