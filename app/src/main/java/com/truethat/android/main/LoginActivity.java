package com.truethat.android.main;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.appsee.Appsee;
import com.sendbird.android.SendBird;
import com.sendbird.android.SendBirdException;
import com.sendbird.android.User;
import com.truethat.android.BuildConfig;
import com.truethat.android.R;
import com.truethat.android.groupchannel.GroupChannelActivity;
import com.truethat.android.utils.PreferenceUtils;
import com.truethat.android.utils.PushUtils;

public class LoginActivity extends AppCompatActivity {

  private static final int NICKNAME_MIN_LENGTH = 3;
  private CoordinatorLayout mLoginLayout;
  //private TextInputEditText mUserIdConnectEditText;
  private TextInputEditText mUserNicknameEditText;
  private Button mConnectButton;
  private ContentLoadingProgressBar mProgressBar;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_login);

    mLoginLayout = (CoordinatorLayout) findViewById(R.id.layout_login);

    //mUserIdConnectEditText = (TextInputEditText) findViewById(R.id.edittext_login_user_id);
    mUserNicknameEditText = (TextInputEditText) findViewById(R.id.edittext_login_user_nickname);

    //mUserIdConnectEditText.setText(PreferenceUtils.getUserId(this));
    mUserNicknameEditText.setText(PreferenceUtils.getNickname(this));
    mUserNicknameEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (v.getText().length() >= NICKNAME_MIN_LENGTH) {
          mConnectButton.performClick();
          return true;
        }
        return false;
      }
    });
    mConnectButton = (Button) findViewById(R.id.button_login_connect);
    mConnectButton.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        @SuppressLint("HardwareIds") String userId =
            Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        //mUserIdConnectEditText.getText().toString();
        // Remove all spaces from userID
        userId = userId.replaceAll("\\s", "");

        String userNickname = mUserNicknameEditText.getText().toString();

        PreferenceUtils.setUserId(LoginActivity.this, userId);
        PreferenceUtils.setNickname(LoginActivity.this, userNickname);

        if (userNickname.length() >= NICKNAME_MIN_LENGTH) {
          connectToSendBird(userId, userNickname);
        }
      }
    });

    // A loading indicator
    mProgressBar = (ContentLoadingProgressBar) findViewById(R.id.progress_bar_login);

    // Display current SendBird and app versions in a TextView
    String sdkVersion =
        String.format(getResources().getString(R.string.all_app_version), BaseApplication.VERSION,
            SendBird.getSDKVersion());
    ((TextView) findViewById(R.id.text_login_versions)).setText(sdkVersion);

    // Appsee
    Appsee.start(BuildConfig.APPSEE_API_KEY);
  }

  @Override protected void onStart() {
    super.onStart();
    if (PreferenceUtils.getConnected(this)) {
      connectToSendBird(PreferenceUtils.getUserId(this), PreferenceUtils.getNickname(this));
    }
  }

  /**
   * Attempts to connect a user to SendBird.
   *
   * @param userId       The unique ID of the user.
   * @param userNickname The user's nickname, which will be displayed in chats.
   */
  private void connectToSendBird(final String userId, final String userNickname) {
    // Show the loading indicator
    showProgressBar(true);
    mConnectButton.setEnabled(false);

    SendBird.connect(userId, new SendBird.ConnectHandler() {
      @Override public void onConnected(User user, SendBirdException e) {
        // Callback received; hide the progress bar.
        showProgressBar(false);

        if (e != null) {
          // Error!
          Toast.makeText(LoginActivity.this, "" + e.getCode() + ": " + e.getMessage(),
              Toast.LENGTH_SHORT).show();

          // Show login failure snackbar
          showSnackbar("Login to SendBird failed");
          mConnectButton.setEnabled(true);
          PreferenceUtils.setConnected(LoginActivity.this, false);
          return;
        }

        PreferenceUtils.setNickname(LoginActivity.this, user.getNickname());
        PreferenceUtils.setProfileUrl(LoginActivity.this, user.getProfileUrl());
        PreferenceUtils.setConnected(LoginActivity.this, true);

        // Update the user's nickname
        updateCurrentUserInfo(userNickname);
        updateCurrentUserPushToken();

        // Proceed to MainActivity
        Intent intent = new Intent(LoginActivity.this, GroupChannelActivity.class);
        startActivity(intent);
        finish();
      }
    });
  }

  /**
   * Update the user's push token.
   */
  private void updateCurrentUserPushToken() {
    PushUtils.registerPushTokenForCurrentUser(LoginActivity.this, null);
  }

  /**
   * Updates the user's nickname.
   *
   * @param userNickname The new nickname of the user.
   */
  private void updateCurrentUserInfo(final String userNickname) {
    SendBird.updateCurrentUserInfo(userNickname, null, new SendBird.UserInfoUpdateHandler() {
      @Override public void onUpdated(SendBirdException e) {
        if (e != null) {
          // Error!
          Toast.makeText(LoginActivity.this, "" + e.getCode() + ":" + e.getMessage(),
              Toast.LENGTH_SHORT).show();

          // Show update failed snackbar
          showSnackbar("Update user nickname failed");

          return;
        }

        PreferenceUtils.setNickname(LoginActivity.this, userNickname);
      }
    });
  }

  // Displays a Snackbar from the bottom of the screen
  private void showSnackbar(String text) {
    Snackbar snackbar = Snackbar.make(mLoginLayout, text, Snackbar.LENGTH_SHORT);

    snackbar.show();
  }

  // Shows or hides the ProgressBar
  private void showProgressBar(boolean show) {
    if (show) {
      mProgressBar.show();
    } else {
      mProgressBar.hide();
    }
  }
}
