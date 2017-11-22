package com.truethat.android.groupchannel;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import com.truethat.android.R;
import com.truethat.android.affectiva.AffectivaReactionDetectionManager;
import com.truethat.android.main.SettingsActivity;
import java.util.Objects;

import static com.truethat.android.affectiva.AffectivaReactionDetectionManager.PERMISSION_CAMERA;

public class GroupChannelActivity extends AppCompatActivity {
  private static final String LOG_TAG = GroupChannelActivity.class.getSimpleName();

  private onBackPressedListener mOnBackPressedListener;
  private AffectivaReactionDetectionManager mDetectionManager;

  public AffectivaReactionDetectionManager getDetectionManager() {
    return mDetectionManager;
  }

  public void setOnBackPressedListener(onBackPressedListener listener) {
    mOnBackPressedListener = listener;
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_main:
        Intent intent = new Intent(GroupChannelActivity.this, SettingsActivity.class);
        startActivity(intent);
        return true;
      case android.R.id.home:
        onBackPressed();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override public void onBackPressed() {
    if (mOnBackPressedListener != null && mOnBackPressedListener.onBack()) {
      return;
    }
    super.onBackPressed();
  }

  @Override protected void onPause() {
    super.onPause();
    stopDetector();
  }

  @Override protected void onResume() {
    super.onResume();

    startDetector();
  }

  @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    Log.d(LOG_TAG, "onRequestPermissionsResult");
    if (requestCode == PERMISSION_CAMERA && permissions.length > 0 && Objects.equals(permissions[0],
        Manifest.permission.CAMERA) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      startDetector();
    }
  }

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_group_channel);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_group_channel);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_left_white_24_dp);
    }

    if (savedInstanceState == null) {
      // If started from launcher, load list of Open Channels
      Fragment fragment = GroupChannelListFragment.newInstance();

      FragmentManager manager = getSupportFragmentManager();
      manager.popBackStack();

      manager.beginTransaction().replace(R.id.container_group_channel, fragment).commit();
    }

    String channelUrl = getIntent().getStringExtra("groupChannelUrl");
    if (channelUrl != null) {
      // If started from notification
      Fragment fragment = GroupChatFragment.newInstance(channelUrl);
      FragmentManager manager = getSupportFragmentManager();
      manager.beginTransaction()
          .replace(R.id.container_group_channel, fragment)
          .addToBackStack(null)
          .commit();
    }
  }

  void startDetector() {
    if (mDetectionManager == null) {
      mDetectionManager = new AffectivaReactionDetectionManager();
    }
    mDetectionManager.start(this);
  }

  void stopDetector() {
    if (mDetectionManager != null) {
      mDetectionManager.stop();
    }
  }

  void setActionBarTitle(String title) {
    if (getSupportActionBar() != null) {
      getSupportActionBar().setTitle(title);
    }
  }

  interface onBackPressedListener {
    boolean onBack();
  }
}
