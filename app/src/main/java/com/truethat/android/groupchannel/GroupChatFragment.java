package com.truethat.android.groupchannel;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.sendbird.android.AdminMessage;
import com.sendbird.android.BaseChannel;
import com.sendbird.android.BaseMessage;
import com.sendbird.android.FileMessage;
import com.sendbird.android.GroupChannel;
import com.sendbird.android.Member;
import com.sendbird.android.PreviousMessageListQuery;
import com.sendbird.android.SendBird;
import com.sendbird.android.SendBirdException;
import com.sendbird.android.User;
import com.sendbird.android.UserMessage;
import com.truethat.android.R;
import com.truethat.android.affectiva.ReactionDetectionListener;
import com.truethat.android.common.Emotion;
import com.truethat.android.utils.FileUtils;
import com.truethat.android.utils.MediaPlayerActivity;
import com.truethat.android.utils.PhotoViewerActivity;
import com.truethat.android.utils.PreferenceUtils;
import com.truethat.android.utils.TextUtils;
import com.truethat.android.utils.UrlPreviewInfo;
import com.truethat.android.utils.WebUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import org.json.JSONException;

public class GroupChatFragment extends Fragment implements ReactionDetectionListener {

  public static final String REACTION_MESSAGE_TYPE = "truedat_reaction";
  static final String EXTRA_CHANNEL_URL = "EXTRA_CHANNEL_URL";
  private static final String CONNECTION_HANDLER_ID = "CONNECTION_HANDLER_GROUP_CHAT";
  private static final String LOG_TAG = GroupChatFragment.class.getSimpleName();
  private static final int STATE_NORMAL = 0;
  private static final int STATE_EDIT = 1;
  private static final String CHANNEL_HANDLER_ID = "CHANNEL_HANDLER_GROUP_CHANNEL_CHAT";
  private static final String STATE_CHANNEL_URL = "STATE_CHANNEL_URL";
  private static final int INTENT_REQUEST_CHOOSE_MEDIA = 301;
  private static final int INTENT_TAKE_PHOTO = 100;
  private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 13;
  private InputMethodManager mIMM;
  private HashMap<BaseChannel.SendFileMessageWithProgressHandler, FileMessage>
      mFileProgressHandlerMap;
  private RelativeLayout mRootLayout;
  private RecyclerView mRecyclerView;
  private GroupChatAdapter mChatAdapter;
  private LinearLayoutManager mLayoutManager;
  private EditText mMessageEditText;
  private Button mMessageSendButton;
  private ImageButton mUploadFileButton;
  private ImageButton mTakePhotoButton;
  private View mCurrentEventLayout;
  private TextView mCurrentEventText;
  private String mCurrentPhotoPath;
  private MediaPlayer mPlayer;
  private GroupChannel mChannel;
  private String mChannelUrl;
  private PreviousMessageListQuery mPrevMessageListQuery;
  private boolean mIsTyping;
  private int mCurrentState = STATE_NORMAL;
  private BaseMessage mEditingMessage = null;

  /**
   * To create an instance of this fragment, a Channel URL should be required.
   */
  public static GroupChatFragment newInstance(@NonNull String channelUrl) {
    GroupChatFragment fragment = new GroupChatFragment();

    Bundle args = new Bundle();
    args.putString(GroupChannelListFragment.EXTRA_GROUP_CHANNEL_URL, channelUrl);
    fragment.setArguments(args);

    return fragment;
  }

  @Override public void onActivityResult(int requestCode, int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == INTENT_REQUEST_CHOOSE_MEDIA && resultCode == Activity.RESULT_OK) {
      // If user has successfully chosen the image, show a dialog to confirm upload.
      if (data == null) {
        Log.d(LOG_TAG, "data is null!");
        return;
      }

      sendFileWithThumbnail(data.getData());
    }

    if (requestCode == INTENT_TAKE_PHOTO && resultCode == Activity.RESULT_OK) {
      if (mCurrentPhotoPath == null) {
        Log.d(LOG_TAG, "no image saved!");
        return;
      }
      sendFileWithThumbnail(Uri.fromFile(new File(mCurrentPhotoPath)));
    }

    // Set this as true to restore background connection management.
    SendBird.setAutoBackgroundDetection(true);
  }

  @Override public void onAttach(Context context) {
    super.onAttach(context);
    ((GroupChannelActivity) context).setOnBackPressedListener(
        new GroupChannelActivity.onBackPressedListener() {
          @Override public boolean onBack() {
            if (mCurrentState == STATE_EDIT) {
              setState(STATE_NORMAL, null, -1);
              return true;
            }
            return false;
          }
        });
  }

  @Override public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mIMM = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
    mFileProgressHandlerMap = new HashMap<>();

    if (savedInstanceState != null) {
      // Get channel URL from saved state.
      mChannelUrl = savedInstanceState.getString(STATE_CHANNEL_URL);
    } else {
      // Get channel URL from GroupChannelListFragment.
      mChannelUrl = getArguments().getString(GroupChannelListFragment.EXTRA_GROUP_CHANNEL_URL);
    }

    Log.d(LOG_TAG, mChannelUrl);

    mChatAdapter = new GroupChatAdapter(getActivity(), this);
    setUpChatListAdapter();

    // Load messages from cache.
    mChatAdapter.load(mChannelUrl);
  }

  @Nullable @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.fragment_group_chat, container, false);

    setRetainInstance(true);

    mRootLayout = (RelativeLayout) rootView.findViewById(R.id.layout_group_chat_root);
    mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_group_chat);

    mCurrentEventLayout = rootView.findViewById(R.id.layout_group_chat_current_event);
    mCurrentEventText = (TextView) rootView.findViewById(R.id.text_group_chat_current_event);

    mMessageEditText = (EditText) rootView.findViewById(R.id.edittext_group_chat_message);
    mMessageSendButton = (Button) rootView.findViewById(R.id.button_group_chat_send);
    mUploadFileButton = (ImageButton) rootView.findViewById(R.id.button_group_chat_upload);
    mTakePhotoButton = (ImageButton) rootView.findViewById(R.id.button_group_chat_camera);

    mMessageEditText.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
      }

      @Override public void afterTextChanged(Editable s) {
        if (s.length() > 0) {
          mMessageSendButton.setEnabled(true);
        } else {
          mMessageSendButton.setEnabled(false);
        }
      }
    });

    mMessageSendButton.setEnabled(false);
    mMessageSendButton.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        if (mCurrentState == STATE_EDIT) {
          String userInput = mMessageEditText.getText().toString();
          if (userInput.length() > 0) {
            if (mEditingMessage != null) {
              editMessage(mEditingMessage, userInput);
            }
          }
          setState(STATE_NORMAL, null, -1);
        } else {
          String userInput = mMessageEditText.getText().toString();
          if (userInput.length() > 0) {
            sendUserMessage(userInput, null);
            mMessageEditText.setText("");
          }
        }
      }
    });

    mUploadFileButton.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        requestMedia();
      }
    });

    mTakePhotoButton.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        takePhoto();
      }
    });

    mIsTyping = false;
    mMessageEditText.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (!mIsTyping) {
          setTypingStatus(true);
        }

        if (s.length() == 0) {
          setTypingStatus(false);
        }
      }

      @Override public void afterTextChanged(Editable s) {
      }
    });

    setUpRecyclerView();

    setHasOptionsMenu(true);

    return rootView;
  }

  @Override public void onResume() {
    super.onResume();

    mChatAdapter.setContext(
        getActivity()); // Glide bug fix (java.lang.IllegalArgumentException: You cannot start a load for a destroyed activity)

    // Gets channel from URL user requested

    Log.d(LOG_TAG, mChannelUrl);

    SendBird.addChannelHandler(CHANNEL_HANDLER_ID, new SendBird.ChannelHandler() {
      @Override public void onMessageReceived(BaseChannel baseChannel, BaseMessage baseMessage) {
        if (baseChannel.getUrl().equals(mChannelUrl)) {
          mChatAdapter.markAllMessagesAsRead();
          // Add new message to view
          mChatAdapter.addFirst(baseMessage);
          if (!(baseMessage instanceof UserMessage) || !Objects.equals(
              ((UserMessage) baseMessage).getCustomType(),
              GroupChatFragment.REACTION_MESSAGE_TYPE)) {
            ((GroupChannelActivity) getActivity()).getDetectionManager()
                .getDetectionHandler()
                .setDetectionListener(GroupChatFragment.this);
          }
        }
      }

      @Override public void onMessageDeleted(BaseChannel baseChannel, long msgId) {
        super.onMessageDeleted(baseChannel, msgId);
        if (baseChannel.getUrl().equals(mChannelUrl)) {
          mChatAdapter.delete(msgId);
        }
      }

      @Override public void onMessageUpdated(BaseChannel channel, BaseMessage message) {
        super.onMessageUpdated(channel, message);
        if (channel.getUrl().equals(mChannelUrl)) {
          mChatAdapter.update(message);
        }
      }

      @Override public void onReadReceiptUpdated(GroupChannel channel) {
        if (channel.getUrl().equals(mChannelUrl)) {
          mChatAdapter.notifyDataSetChanged();
        }
      }

      @Override public void onTypingStatusUpdated(GroupChannel channel) {
        if (channel.getUrl().equals(mChannelUrl)) {
          List<Member> typingUsers = channel.getTypingMembers();
          displayTyping(typingUsers);
        }
      }
    });

    SendBird.addConnectionHandler(CONNECTION_HANDLER_ID, new SendBird.ConnectionHandler() {
      @Override public void onReconnectStarted() {
      }

      @Override public void onReconnectSucceeded() {
        refresh();
      }

      @Override public void onReconnectFailed() {
      }
    });

    if (SendBird.getConnectionState() == SendBird.ConnectionState.OPEN) {
      refresh();
    } else {
      if (SendBird.reconnect()) {
        // Will call onReconnectSucceeded()
      } else {
        String userId = PreferenceUtils.getUserId(getActivity());
        if (userId == null) {
          Toast.makeText(getActivity(), "Require user ID to connect to SendBird.",
              Toast.LENGTH_LONG).show();
          return;
        }

        SendBird.connect(userId, new SendBird.ConnectHandler() {
          @Override public void onConnected(User user, SendBirdException e) {
            if (e != null) {
              e.printStackTrace();
              return;
            }

            refresh();
          }
        });
      }
    }
  }

  @Override public void onSaveInstanceState(Bundle outState) {
    outState.putString(STATE_CHANNEL_URL, mChannelUrl);

    super.onSaveInstanceState(outState);
  }

  @Override public void onPause() {
    setTypingStatus(false);

    SendBird.removeChannelHandler(CHANNEL_HANDLER_ID);
    SendBird.removeConnectionHandler(CONNECTION_HANDLER_ID);
    super.onPause();

    killReactSoundPlayer();
  }

  @Override public void onDestroy() {
    // Save messages to cache.
    mChatAdapter.save();

    super.onDestroy();
  }

  @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_group_chat, menu);
    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    if (id == R.id.action_group_channel_invite) {
      Intent intent = new Intent(getActivity(), InviteMemberActivity.class);
      intent.putExtra(EXTRA_CHANNEL_URL, mChannelUrl);
      startActivity(intent);
      return true;
    } else if (id == R.id.action_group_channel_view_members) {
      Intent intent = new Intent(getActivity(), MemberListActivity.class);
      intent.putExtra(EXTRA_CHANNEL_URL, mChannelUrl);
      startActivity(intent);
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override public void onReactionDetected(final Emotion reaction, boolean mostLikely) {
    if (!mostLikely) return;
    ((GroupChannelActivity) getActivity()).getDetectionManager()
        .getDetectionHandler()
        .setDetectionListener(null);
    getActivity().runOnUiThread(new Runnable() {
      @Override public void run() {
        sendUserMessage(reaction.getEmoji(), REACTION_MESSAGE_TYPE);
        Vibrator vb = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        vb.vibrate(10);
        killReactSoundPlayer();
        mPlayer = MediaPlayer.create(getContext(), R.raw.react);
        mPlayer.start();
      }
    });
  }

  private void killReactSoundPlayer() {
    if (mPlayer != null) {
      mPlayer.stop();
      mPlayer.release();
      mPlayer = null;
    }
  }

  private File createImageFile() throws IOException {
    // Create an image file name
    @SuppressLint("SimpleDateFormat") String timeStamp =
        new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    String imageFileName = "JPEG_" + timeStamp + "_";
    File storageDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    File imageFile = File.createTempFile(imageFileName,  /* prefix */
        ".jpg",         /* suffix */
        storageDir      /* directory */);

    // Save a file: path for use with ACTION_VIEW intents
    mCurrentPhotoPath = imageFile.getAbsolutePath();
    return imageFile;
  }

  private void refresh() {
    if (mChannel == null) {
      GroupChannel.getChannel(mChannelUrl, new GroupChannel.GroupChannelGetHandler() {
        @Override public void onResult(GroupChannel groupChannel, SendBirdException e) {
          if (e != null) {
            // Error!
            e.printStackTrace();
            return;
          }

          mChannel = groupChannel;
          mChatAdapter.setChannel(mChannel);
          mChatAdapter.loadLatestMessages(30, new BaseChannel.GetMessagesHandler() {
            @Override public void onResult(List<BaseMessage> list, SendBirdException e) {
              mChatAdapter.markAllMessagesAsRead();
            }
          });
          updateActionBarTitle();
        }
      });
    } else {
      mChannel.refresh(new GroupChannel.GroupChannelRefreshHandler() {
        @Override public void onResult(SendBirdException e) {
          if (e != null) {
            // Error!
            e.printStackTrace();
            return;
          }

          mChatAdapter.loadLatestMessages(30, new BaseChannel.GetMessagesHandler() {
            @Override public void onResult(List<BaseMessage> list, SendBirdException e) {
              mChatAdapter.markAllMessagesAsRead();
            }
          });
          updateActionBarTitle();
        }
      });
    }
  }

  private void setUpRecyclerView() {
    mLayoutManager = new LinearLayoutManager(getActivity());
    mLayoutManager.setReverseLayout(true);
    mRecyclerView.setLayoutManager(mLayoutManager);
    mRecyclerView.setAdapter(mChatAdapter);
    mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        if (mLayoutManager.findLastVisibleItemPosition() == mChatAdapter.getItemCount() - 1) {
          mChatAdapter.loadPreviousMessages(30, null);
        }
      }
    });
  }

  private void setUpChatListAdapter() {
    mChatAdapter.setItemClickListener(new GroupChatAdapter.OnItemClickListener() {
      @Override public void onUserMessageItemClick(UserMessage message) {
        // Restore failed message and remove the failed message from list.
        if (mChatAdapter.isFailedMessage(message)) {
          retryFailedMessage(message);
          return;
        }

        // Message is sending. Do nothing on click event.
        if (mChatAdapter.isTempMessage(message)) {
          return;
        }

        if (message.getCustomType().equals(GroupChatAdapter.URL_PREVIEW_CUSTOM_TYPE)) {
          try {
            UrlPreviewInfo info = new UrlPreviewInfo(message.getData());
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.getUrl()));
            startActivity(browserIntent);
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      }

      @Override public void onFileMessageItemClick(FileMessage message) {
        // Load media chooser and remove the failed message from list.
        if (mChatAdapter.isFailedMessage(message)) {
          retryFailedMessage(message);
          return;
        }

        // Message is sending. Do nothing on click event.
        if (mChatAdapter.isTempMessage(message)) {
          return;
        }

        onFileMessageClicked(message);
      }
    });

    mChatAdapter.setItemLongClickListener(new GroupChatAdapter.OnItemLongClickListener() {
      @Override public void onUserMessageItemLongClick(UserMessage message, int position) {
        showMessageOptionsDialog(message, position);
      }

      @Override public void onFileMessageItemLongClick(FileMessage message) {
      }

      @Override public void onAdminMessageItemLongClick(AdminMessage message) {
      }
    });
  }

  private void showMessageOptionsDialog(final BaseMessage message, final int position) {
    String[] options = new String[] { "Edit message", "Delete message" };

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setItems(options, new DialogInterface.OnClickListener() {
      @Override public void onClick(DialogInterface dialog, int which) {
        if (which == 0) {
          setState(STATE_EDIT, message, position);
        } else if (which == 1) {
          deleteMessage(message);
        }
      }
    });
    builder.create().show();
  }

  private void setState(int state, BaseMessage editingMessage, final int position) {
    switch (state) {
      case STATE_NORMAL:
        mCurrentState = STATE_NORMAL;
        mEditingMessage = null;

        mUploadFileButton.setVisibility(View.VISIBLE);
        mTakePhotoButton.setVisibility(View.VISIBLE);
        mMessageSendButton.setText("SEND");
        mMessageEditText.setText("");

        //                mIMM.hideSoftInputFromWindow(mMessageEditText.getWindowToken(), 0);
        break;

      case STATE_EDIT:
        mCurrentState = STATE_EDIT;
        mEditingMessage = editingMessage;

        mUploadFileButton.setVisibility(View.GONE);
        mTakePhotoButton.setVisibility(View.GONE);
        mMessageSendButton.setText("SAVE");
        String messageString = ((UserMessage) editingMessage).getMessage();
        if (messageString == null) {
          messageString = "";
        }
        mMessageEditText.setText(messageString);
        if (messageString.length() > 0) {
          mMessageEditText.setSelection(0, messageString.length());
        }

        mMessageEditText.requestFocus();
        mIMM.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

        mRecyclerView.postDelayed(new Runnable() {
          @Override public void run() {
            mRecyclerView.scrollToPosition(position);
          }
        }, 500);
        break;
    }
  }

  private void retryFailedMessage(final BaseMessage message) {
    new AlertDialog.Builder(getActivity()).setMessage("Retry?")
        .setPositiveButton(R.string.resend_message, new DialogInterface.OnClickListener() {
          @Override public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
              if (message instanceof UserMessage) {
                String userInput = ((UserMessage) message).getMessage();
                sendUserMessage(userInput, null);
              } else if (message instanceof FileMessage) {
                Uri uri = mChatAdapter.getTempFileMessageUri(message);
                sendFileWithThumbnail(uri);
              }
              mChatAdapter.removeFailedMessage(message);
            }
          }
        })
        .setNegativeButton(R.string.delete_message, new DialogInterface.OnClickListener() {
          @Override public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
              mChatAdapter.removeFailedMessage(message);
            }
          }
        })
        .show();
  }

  /**
   * Display which users are typing.
   * If more than two users are currently typing, this will state that "multiple users" are typing.
   *
   * @param typingUsers The list of currently typing users.
   */
  private void displayTyping(List<Member> typingUsers) {

    if (typingUsers.size() > 0) {
      mCurrentEventLayout.setVisibility(View.VISIBLE);
      String string;

      if (typingUsers.size() == 1) {
        string = typingUsers.get(0).getNickname() + " is typing";
      } else if (typingUsers.size() == 2) {
        string = typingUsers.get(0).getNickname()
            + " "
            + typingUsers.get(1).getNickname()
            + " is typing";
      } else {
        string = "Multiple users are typing";
      }
      mCurrentEventText.setText(string);
    } else {
      mCurrentEventLayout.setVisibility(View.GONE);
    }
  }

  private void requestMedia() {
    if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      // If storage permissions are not granted, request permissions at run-time,
      // as per < API 23 guidelines.
      requestStoragePermissions();
    } else {
      Intent intent = new Intent();

      // Pick images or videos
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        intent.setType("*/*");
        String[] mimeTypes = { "image/*" };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
      } else {
        intent.setType("image/*");
      }

      intent.setAction(Intent.ACTION_GET_CONTENT);

      // Always show the chooser (if there are multiple options available)
      startActivityForResult(Intent.createChooser(intent, "Select Media"),
          INTENT_REQUEST_CHOOSE_MEDIA);

      // Set this as false to maintain connection
      // even when an external Activity is started.
      SendBird.setAutoBackgroundDetection(false);
    }
  }

  private void takePhoto() {
    Log.d(LOG_TAG, "takePhoto");
    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    // Ensure that there's a camera activity to handle the intent
    if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
      // Create the File where the photo should go
      File photoFile = null;
      try {
        photoFile = createImageFile();
      } catch (IOException ex) {
        // Error occurred while creating the File
      }
      // Continue only if the File was successfully created
      if (photoFile != null) {
        Uri photoURI = FileProvider.getUriForFile(getContext(), "com.truethat.android.fileprovider",
            photoFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        startActivityForResult(takePictureIntent, INTENT_TAKE_PHOTO);
      }
    }

    // Set this as false to maintain connection
    // even when an external Activity is started.
    SendBird.setAutoBackgroundDetection(false);
  }

  private void requestStoragePermissions() {
    if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      // Provide an additional rationale to the user if the permission was not granted
      // and the user would benefit from additional context for the use of the permission.
      // For example if the user has previously denied the permission.
      Snackbar.make(mRootLayout,
          "Storage access permissions are required to upload/download files.", Snackbar.LENGTH_LONG)
          .setAction("Okay", new View.OnClickListener() {
            @Override public void onClick(View view) {
              requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                  PERMISSION_WRITE_EXTERNAL_STORAGE);
            }
          })
          .show();
    } else {
      // Permission has not been granted yet. Request it directly.
      requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
          PERMISSION_WRITE_EXTERNAL_STORAGE);
    }
  }

  private void onFileMessageClicked(FileMessage message) {
    String type = message.getType().toLowerCase();
    if (type.startsWith("image")) {
      Intent i = new Intent(getActivity(), PhotoViewerActivity.class);
      i.putExtra("url", message.getUrl());
      i.putExtra("type", message.getType());
      startActivity(i);
    } else if (type.startsWith("video")) {
      Intent intent = new Intent(getActivity(), MediaPlayerActivity.class);
      intent.putExtra("url", message.getUrl());
      startActivity(intent);
    } else {
      showDownloadConfirmDialog(message);
    }
  }

  private void showDownloadConfirmDialog(final FileMessage message) {
    if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      // If storage permissions are not granted, request permissions at run-time,
      // as per < API 23 guidelines.
      requestStoragePermissions();
    } else {
      new AlertDialog.Builder(getActivity()).setMessage("Download file?")
          .setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
              if (which == DialogInterface.BUTTON_POSITIVE) {
                FileUtils.downloadFile(getActivity(), message.getUrl(), message.getName());
              }
            }
          })
          .setNegativeButton(R.string.cancel, null)
          .show();
    }
  }

  private void updateActionBarTitle() {
    String title = "";

    if (mChannel != null) {
      title = TextUtils.getGroupChannelTitle(mChannel);
    }

    // Set action bar title to name of channel
    if (getActivity() != null) {
      ((GroupChannelActivity) getActivity()).setActionBarTitle(title);
    }
  }

  @SuppressLint("StaticFieldLeak") private void sendUserMessageWithUrl(final String text, String url) {
    new WebUtils.UrlPreviewAsyncTask() {
      @Override protected void onPostExecute(UrlPreviewInfo info) {
        UserMessage tempUserMessage = null;
        BaseChannel.SendUserMessageHandler handler = new BaseChannel.SendUserMessageHandler() {
          @Override public void onSent(UserMessage userMessage, SendBirdException e) {
            if (e != null) {
              // Error!
              Log.e(LOG_TAG, e.toString());
              Toast.makeText(getActivity(), "Send failed with error " + e.getCode() + ": " + e.getMessage(),
                  Toast.LENGTH_SHORT).show();
              mChatAdapter.markMessageFailed(userMessage.getRequestId());
              return;
            }

            // Update a sent message to RecyclerView
            mChatAdapter.markMessageSent(userMessage);
          }
        };

        try {
          // Sending a message with URL preview information and custom type.
          String jsonString = info.toJsonString();
          tempUserMessage =
              mChannel.sendUserMessage(text, jsonString, GroupChatAdapter.URL_PREVIEW_CUSTOM_TYPE,
                  handler);
        } catch (Exception e) {
          // Sending a message without URL preview information.
          tempUserMessage = mChannel.sendUserMessage(text, handler);
        }

        // Display a user message to RecyclerView
        mChatAdapter.addFirst(tempUserMessage);
      }
    }.execute(url);
  }

  private void sendUserMessage(String text, @Nullable String customType) {
    Log.d(LOG_TAG, "sendUserMessage(" + text + ", " + customType + ")");
    List<String> urls = WebUtils.extractUrls(text);
    if (urls.size() > 0) {
      sendUserMessageWithUrl(text, urls.get(0));
      return;
    }

    UserMessage tempUserMessage = mChannel.sendUserMessage(text, null, customType, null,
        new BaseChannel.SendUserMessageHandler() {
          @Override public void onSent(UserMessage userMessage, SendBirdException e) {
            if (e != null) {
              // Error!
              Log.e(LOG_TAG, e.toString());
              Toast.makeText(getActivity(),
                  "Send failed with error " + e.getCode() + ": " + e.getMessage(),
                  Toast.LENGTH_SHORT).show();
              mChatAdapter.markMessageFailed(userMessage.getRequestId());
              return;
            }

            // Update a sent message to RecyclerView
            mChatAdapter.markMessageSent(userMessage);
          }
        });

    // Display a user message to RecyclerView
    mChatAdapter.addFirst(tempUserMessage);
  }

  /**
   * Notify other users whether the current user is typing.
   *
   * @param typing Whether the user is currently typing.
   */
  private void setTypingStatus(boolean typing) {
    if (mChannel == null) {
      return;
    }

    if (typing) {
      mIsTyping = true;
      mChannel.startTyping();
    } else {
      mIsTyping = false;
      mChannel.endTyping();
    }
  }

  /**
   * Sends a File Message containing an image file.
   * Also requests thumbnails to be generated in specified sizes.
   *
   * @param uri The URI of the image, which in this case is received through an Intent request.
   */
  private void sendFileWithThumbnail(Uri uri) {
    // Specify two dimensions of thumbnails to generate
    List<FileMessage.ThumbnailSize> thumbnailSizes = new ArrayList<>();
    thumbnailSizes.add(new FileMessage.ThumbnailSize(240, 240));
    thumbnailSizes.add(new FileMessage.ThumbnailSize(320, 320));

    Hashtable<String, Object> info = FileUtils.getFileInfo(getActivity(), uri);

    if (info == null) {
      Toast.makeText(getActivity(), "Extracting file information failed.", Toast.LENGTH_LONG)
          .show();
      return;
    }

    final String path = (String) info.get("path");

    // Reducing image size
    Bitmap bitmap = BitmapFactory.decodeFile(path);
    bitmap =
        Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 2, bitmap.getHeight() / 2, false);
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 30, byteArrayOutputStream);
    try (OutputStream outputStream = new FileOutputStream(path)) {
      byteArrayOutputStream.writeTo(outputStream);
    } catch (IOException e) {
      Log.e(LOG_TAG, "Failed to write photo minified photo.");
    }

    final File file = new File(path);
    final String name = file.getName();
    String mime = (String) info.get("mime");
    mime = mime.startsWith("image") ? mime : "image/jpg";
    final int size = (Integer) info.get("size");

    if (path.equals("")) {
      Toast.makeText(getActivity(), "File must be located in local storage.", Toast.LENGTH_LONG)
          .show();
    } else {
      BaseChannel.SendFileMessageWithProgressHandler progressHandler =
          new BaseChannel.SendFileMessageWithProgressHandler() {
            @Override
            public void onProgress(int bytesSent, int totalBytesSent, int totalBytesToSend) {
              FileMessage fileMessage = mFileProgressHandlerMap.get(this);
              if (fileMessage != null && totalBytesToSend > 0) {
                int percent = (totalBytesSent * 100) / totalBytesToSend;
                mChatAdapter.setFileProgressPercent(fileMessage, percent);
              }
            }

            @Override public void onSent(FileMessage fileMessage, SendBirdException e) {
              if (e != null) {
                Toast.makeText(getActivity(), "" + e.getCode() + ":" + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
                mChatAdapter.markMessageFailed(fileMessage.getRequestId());
                return;
              }

              mChatAdapter.markMessageSent(fileMessage);
            }
          };

      // Send image with thumbnails in the specified dimensions
      FileMessage tempFileMessage =
          mChannel.sendFileMessage(file, name, mime, size, "", null, thumbnailSizes,
              progressHandler);

      mFileProgressHandlerMap.put(progressHandler, tempFileMessage);

      mChatAdapter.addTempFileMessageInfo(tempFileMessage, uri);
      mChatAdapter.addFirst(tempFileMessage);
    }
  }

  private void editMessage(final BaseMessage message, String editedMessage) {
    mChannel.updateUserMessage(message.getMessageId(), editedMessage, null, null,
        new BaseChannel.UpdateUserMessageHandler() {
          @Override public void onUpdated(UserMessage userMessage, SendBirdException e) {
            if (e != null) {
              // Error!
              Toast.makeText(getActivity(), "Error " + e.getCode() + ": " + e.getMessage(),
                  Toast.LENGTH_SHORT).show();
              return;
            }

            mChatAdapter.loadLatestMessages(30, new BaseChannel.GetMessagesHandler() {
              @Override public void onResult(List<BaseMessage> list, SendBirdException e) {
                mChatAdapter.markAllMessagesAsRead();
              }
            });
          }
        });
  }

  /**
   * Deletes a message within the channel.
   * Note that users can only delete messages sent by oneself.
   *
   * @param message The message to delete.
   */
  private void deleteMessage(final BaseMessage message) {
    mChannel.deleteMessage(message, new BaseChannel.DeleteMessageHandler() {
      @Override public void onResult(SendBirdException e) {
        if (e != null) {
          // Error!
          Toast.makeText(getActivity(), "Error " + e.getCode() + ": " + e.getMessage(),
              Toast.LENGTH_SHORT).show();
          return;
        }

        mChatAdapter.loadLatestMessages(30, new BaseChannel.GetMessagesHandler() {
          @Override public void onResult(List<BaseMessage> list, SendBirdException e) {
            mChatAdapter.markAllMessagesAsRead();
          }
        });
      }
    });
  }
}