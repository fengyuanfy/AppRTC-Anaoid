/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package de.lespace.apprtc;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import java.util.ArrayList;
import java.util.List;


/**
 * Handles the initial setup where the user selects which room to join.
 */
public class ConnectActivity extends RTCConnection  implements AppRTCClient.SignalingEvents {

  private static final String TAG = "ConnectActivity";
  private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
  private static boolean commandLineRun = false;
  private ImageButton connectButton;
  private String keyprefFrom;
  private String keyprefVideoCallEnabled;
  private String keyprefResolution;
  private String keyprefFps;
  private String keyprefCaptureQualitySlider;
  private String keyprefVideoBitrateType;
  private String keyprefVideoBitrateValue;
  private String keyprefVideoCodec;
  private String keyprefAudioBitrateType;
  private String keyprefAudioBitrateValue;
  private String keyprefAudioCodec;
  private String keyprefHwCodecAcceleration;
  private String keyprefCaptureToTexture;
  private String keyprefNoAudioProcessingPipeline;
  private String keyprefAecDump;
  private String keyprefOpenSLES;
  private String keyprefDisplayHud;
  private String keyprefTracing;
  private String keyprefRoomServerUrl;
  private String keyprefRoom;
  private String keyprefRoomList;
  private ListView roomListView;
  private List<String> missingPermissions;
  private Intent intent = null;

  private BroadcastReceiver networkchangeBroadcastReceiver;
  private BroadcastReceiver gcmRegistrationBroadcastReceiver;
  private BroadcastReceiver bringToFrontBroadcastReceiver;

  private boolean isNetworkChangeReceiverRegistered;
  private boolean isGCMReceiverRegistered;
  private boolean isBringToFrontReceiverRegistered;


  // List of mandatory application permissions.
  private static final String[] MANDATORY_PERMISSIONS = {
          "android.permission.MODIFY_AUDIO_SETTINGS",
          "android.permission.RECORD_AUDIO",
          "android.permission.CAMERA",
          "android.permission.INTERNET"
  };
  private boolean callActive;


  @Override
  public void onRequestPermissionsResult(
          int requestCode,
          String permissions[],
          int[] grantResults) {

    if (grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

      Log.e(TAG,  MANDATORY_PERMISSIONS[0] +" Permission Granted!");

      //  Toast.makeText(ConnectActivity.this, MANDATORY_PERMISSIONS[0] + " Permission Granted!", Toast.LENGTH_SHORT).show();
    } else {

      Log.e(TAG, MANDATORY_PERMISSIONS[0] + " Permission Denied!");

      //Toast.makeText(ConnectActivity.this, MANDATORY_PERMISSIONS[0] + " Permission Denied!", Toast.LENGTH_SHORT).show();
    }
    missingPermissions.remove(0); //remove missing permission from array and request next left permission
    requestPermission();
  }


  //http://stackoverflow.com/questions/35484767/activitycompat-requestpermissions-not-showing-dialog-box
  //https://developer.android.com/training/permissions/requesting.html
  private void requestPermission() {
    if (missingPermissions.size() > 0)
      ActivityCompat.requestPermissions(this, new String[]{missingPermissions.get(0)}, missingPermissions.size());
  }



  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_connect);

    // Get setting keys.
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    keyprefFrom = getString(R.string.pref_from_key);
    keyprefVideoCallEnabled = getString(R.string.pref_videocall_key);
    keyprefResolution = getString(R.string.pref_resolution_key);
    keyprefFps = getString(R.string.pref_fps_key);
    keyprefCaptureQualitySlider = getString(R.string.pref_capturequalityslider_key);
    keyprefVideoBitrateType = getString(R.string.pref_startvideobitrate_key);
    keyprefVideoBitrateValue = getString(R.string.pref_startvideobitratevalue_key);
    keyprefVideoCodec = getString(R.string.pref_videocodec_key);
    keyprefHwCodecAcceleration = getString(R.string.pref_hwcodec_key);
    keyprefCaptureToTexture = getString(R.string.pref_capturetotexture_key);
    keyprefAudioBitrateType = getString(R.string.pref_startaudiobitrate_key);
    keyprefAudioBitrateValue = getString(R.string.pref_startaudiobitratevalue_key);
    keyprefAudioCodec = getString(R.string.pref_audiocodec_key);
    keyprefNoAudioProcessingPipeline = getString(R.string.pref_noaudioprocessing_key);
    keyprefAecDump = getString(R.string.pref_aecdump_key);
    keyprefOpenSLES = getString(R.string.pref_opensles_key);
    keyprefDisplayHud = getString(R.string.pref_displayhud_key);
    keyprefTracing = getString(R.string.pref_tracing_key);
    keyprefRoomServerUrl = getString(R.string.pref_room_server_url_key);
    keyprefRoom = getString(R.string.pref_room_key);
    keyprefRoomList = getString(R.string.pref_room_list_key);



    // Video call enabled flag.
    boolean videoCallEnabled = sharedPref.getBoolean(keyprefVideoCallEnabled, Boolean.valueOf(getString(R.string.pref_videocall_default)));

    startService(new Intent(this,SignalingService.class));
    String wssUrl = sharedPref.getString(keyprefRoomServerUrl, getString(R.string.pref_room_server_url_default));
    from = sharedPref.getString(keyprefFrom, getString(R.string.pref_from_default));
    // Get default codecs.
    String videoCodec = sharedPref.getString(keyprefVideoCodec, getString(R.string.pref_videocodec_default));
    String audioCodec = sharedPref.getString(keyprefAudioCodec, getString(R.string.pref_audiocodec_default));

    // Check HW codec flag.
    boolean hwCodec = sharedPref.getBoolean(keyprefHwCodecAcceleration, Boolean.valueOf(getString(R.string.pref_hwcodec_default)));

    // Check Capture to texture.
    boolean captureToTexture = sharedPref.getBoolean(keyprefCaptureToTexture, Boolean.valueOf(getString(R.string.pref_capturetotexture_default)));

    // Check Disable Audio Processing flag.
    boolean noAudioProcessing = sharedPref.getBoolean(keyprefNoAudioProcessingPipeline, Boolean.valueOf(getString(R.string.pref_noaudioprocessing_default)));

    // Check Disable Audio Processing flag.
    boolean aecDump = sharedPref.getBoolean(keyprefAecDump, Boolean.valueOf(getString(R.string.pref_aecdump_default)));

    // Check OpenSL ES enabled flag.
    boolean useOpenSLES = sharedPref.getBoolean(
            keyprefOpenSLES,
            Boolean.valueOf(getString(R.string.pref_opensles_default)));

    // Check for mandatory permissions.
    missingPermissions = new ArrayList();

    for (String permission : MANDATORY_PERMISSIONS) {
      if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        missingPermissions.add(permission);
      }
    }
    requestPermission();

    networkchangeBroadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {

        if(intent.getFlags()==0){
          SignalingService.appRTCClient.resetWebsocket();

        }
        if(intent.getFlags()==1){
          SignalingService.appRTCClient.reconnect();
          Toast.makeText(context, "network is online:"+intent.getFlags(), Toast.LENGTH_LONG).show();
        }

      }
    };

    final AppRTCClient.SignalingEvents signalingEvents = this;
    // mRegistrationProgressBar = (ProgressBar) findViewById(R.id.registrationProgressBar);
    gcmRegistrationBroadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        // mRegistrationProgressBar.setVisibility(ProgressBar.GONE);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean sentToken = sharedPreferences.getBoolean(QuickstartPreferences.SENT_TOKEN_TO_SERVER, false);
        if (sentToken) {
          logAndToast(getString(R.string.gcm_send_message));
          // mInformationTextView.setText(getString(R.string.gcm_send_message));
        } else {
          logAndToast(getString(R.string.gcm_send_message));
          // mInformationTextView.setText(getString(R.string.token_error_message));
        }
        SignalingService.appRTCClient.setSignalingEvents(signalingEvents);
      }
    };

    //Bring Call2Front when
    bringToFrontBroadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {

        Intent intentStart = new Intent(getApplicationContext(), ConnectActivity.class);
            // intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        startActivity(intentStart);
      }
    };

    // Registering BroadcastReceiver
    registerNetworkChangeReceiver();
    registerGCMReceiver();
    registerBringToFrontReceiver();

   // if (checkPlayServices()) {
      // Start IntentService to register this application with GCM.
     // Intent intent = new Intent(this, RegistrationIntentService.class);
      //startService(intent);
   // }
    // Get video resolution from settings.
    int videoWidth = 0;
    int videoHeight = 0;
    String resolution = sharedPref.getString(keyprefResolution,
            getString(R.string.pref_resolution_default));
    String[] dimensions = resolution.split("[ x]+");
    if (dimensions.length == 2) {
      try {
        videoWidth = Integer.parseInt(dimensions[0]);
        videoHeight = Integer.parseInt(dimensions[1]);
      } catch (NumberFormatException e) {
        videoWidth = 0;
        videoHeight = 0;
        Log.e(TAG, "Wrong video resolution setting: " + resolution);
      }
    }

    // Get camera fps from settings.
    int cameraFps = 0;
    String fps = sharedPref.getString(keyprefFps,getString(R.string.pref_fps_default));
    String[] fpsValues = fps.split("[ x]+");
    if (fpsValues.length == 2) {
      try {
        cameraFps = Integer.parseInt(fpsValues[0]);
      } catch (NumberFormatException e) {
        Log.e(TAG, "Wrong camera fps setting: " + fps);
      }
    }

    // Check capture quality slider flag.
    boolean captureQualitySlider = sharedPref.getBoolean(keyprefCaptureQualitySlider,
            Boolean.valueOf(getString(R.string.pref_capturequalityslider_default)));

    // Get video and audio start bitrate.
    int videoStartBitrate = 0;
    String bitrateTypeDefault = getString(
            R.string.pref_startvideobitrate_default);
    String bitrateType = sharedPref.getString(
            keyprefVideoBitrateType, bitrateTypeDefault);
    if (!bitrateType.equals(bitrateTypeDefault)) {
      String bitrateValue = sharedPref.getString(keyprefVideoBitrateValue, getString(R.string.pref_startvideobitratevalue_default));
      videoStartBitrate = Integer.parseInt(bitrateValue);
    }
    int audioStartBitrate = 0;
    bitrateTypeDefault = getString(R.string.pref_startaudiobitrate_default);
    bitrateType = sharedPref.getString(
            keyprefAudioBitrateType, bitrateTypeDefault);
    if (!bitrateType.equals(bitrateTypeDefault)) {
      String bitrateValue = sharedPref.getString(keyprefAudioBitrateValue,
              getString(R.string.pref_startaudiobitratevalue_default));
      audioStartBitrate = Integer.parseInt(bitrateValue);
    }

    // Check statistics display option.
    boolean displayHud = sharedPref.getBoolean(keyprefDisplayHud, Boolean.valueOf(getString(R.string.pref_displayhud_default)));

    boolean tracing = sharedPref.getBoolean(keyprefTracing, Boolean.valueOf(getString(R.string.pref_tracing_default)));

    Log.d(TAG, "Connecting from " + from + " at URL " + wssUrl);

    if (validateUrl(wssUrl)) {
      Uri uri = Uri.parse(wssUrl);
      intent = new Intent(this, ConnectActivity.class);
      intent.setData(uri);
      intent.putExtra(CallActivity.EXTRA_VIDEO_CALL, videoCallEnabled);
      intent.putExtra(CallActivity.EXTRA_VIDEO_WIDTH, videoWidth);
      intent.putExtra(CallActivity.EXTRA_VIDEO_HEIGHT, videoHeight);
      intent.putExtra(CallActivity.EXTRA_VIDEO_FPS, cameraFps);
      intent.putExtra(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, captureQualitySlider);
      intent.putExtra(CallActivity.EXTRA_VIDEO_BITRATE, videoStartBitrate);
      intent.putExtra(CallActivity.EXTRA_VIDEOCODEC, videoCodec);
      intent.putExtra(CallActivity.EXTRA_HWCODEC_ENABLED, hwCodec);
      intent.putExtra(CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, captureToTexture);
      intent.putExtra(CallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, noAudioProcessing);
      intent.putExtra(CallActivity.EXTRA_AECDUMP_ENABLED, aecDump);
      intent.putExtra(CallActivity.EXTRA_OPENSLES_ENABLED, useOpenSLES);
      intent.putExtra(CallActivity.EXTRA_AUDIO_BITRATE, audioStartBitrate);
      intent.putExtra(CallActivity.EXTRA_AUDIOCODEC, audioCodec);
      intent.putExtra(CallActivity.EXTRA_DISPLAY_HUD, displayHud);
      intent.putExtra(CallActivity.EXTRA_TRACING, tracing);
      intent.putExtra(CallActivity.EXTRA_CMDLINE, commandLineRun);
      intent.putExtra(CallActivity.EXTRA_RUNTIME, runTimeMs);
    }

    roomListView = (ListView) findViewById(R.id.room_listview);
    roomListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

    connectButton = (ImageButton) findViewById(R.id.connect_button);
    connectButton.setOnClickListener(connectListener);

    // If an implicit VIEW intent is launching the app, go directly to that URL.
    //final Intent intent = getIntent();
    Uri wsurl = Uri.parse(wssUrl);
    //intent.getData();
    Log.d(TAG, "connecting to:" + wsurl.toString());
    if (wsurl == null) {
      logAndToast(getString(R.string.missing_wsurl));
      Log.e(TAG, "Didn't get any URL in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    if (from == null || from.length() == 0) {
      logAndToast(getString(R.string.missing_from));
      Log.e(TAG, "Incorrect from in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    peerConnectionParameters = new PeerConnectionClient.PeerConnectionParameters(
            videoCallEnabled,
            tracing,
            videoWidth, videoHeight, cameraFps, videoStartBitrate, videoCodec, hwCodec,
            captureToTexture, audioStartBitrate, audioCodec, noAudioProcessing,
            aecDump, useOpenSLES);


    Log.i(TAG, "creating appRtcClient with roomUri:" + wsurl.toString() + " from:" + from);


  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.connect_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle presses on the action bar items.
    if (item.getItemId() == R.id.action_settings) {
      Intent intent = new Intent(this, SettingsActivity.class);
      startActivity(intent);
      return true;
    } else {
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onPause() {

    LocalBroadcastManager.getInstance(this).unregisterReceiver(gcmRegistrationBroadcastReceiver);
    isGCMReceiverRegistered = false;

    //String room = roomEditText.getText().toString();
    String roomListJson = new JSONArray(userList).toString();
    SharedPreferences.Editor editor = sharedPref.edit();
    //editor.putString(keyprefRoom, room);
    editor.putString(keyprefRoomList, roomListJson);
    editor.commit();

    super.onPause();
  }

  @Override
  public void onResume() {

    registerGCMReceiver();

    String room = sharedPref.getString(keyprefRoom, "");
    //roomEditText.setText(room);
    userList = new ArrayList<String>();
    String roomListJson = sharedPref.getString(keyprefRoomList, null);
    if (roomListJson != null) {
      try {
        JSONArray jsonArray = new JSONArray(roomListJson);
        for (int i = 0; i < jsonArray.length(); i++) {
          userList.add(jsonArray.get(i).toString());
        }
      } catch (JSONException e) {
        Log.e(TAG, "Failed to load room list: " + e.toString());
      }
    }
    adapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_1, userList);
    roomListView.setAdapter(adapter);
    if (adapter.getCount() > 0) {
      roomListView.requestFocus();
      roomListView.setItemChecked(0, true);
    }

    super.onResume();
  }


  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    //callbackManager.onActivityResult(requestCode, resultCode, data);

    if (requestCode == CONNECTION_REQUEST && commandLineRun) {
      Log.d(TAG, "Return: " + resultCode);
      setResult(resultCode);
      commandLineRun = false;
      finish();
    }
    if(requestCode==RESULT_OK){
      //Now start callActivityAgain!
      if(callActive){
        //Intent intent = new Intent(this, CallActivity.class);
        //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        //  r.stop();
        //startActivityForResult(intent, CONNECTION_REQUEST);
        connectToUser();
        callActive=false;
      }
    }
  }

  private final OnClickListener connectListener = new OnClickListener() {
    @Override
    public void onClick(View view) {
      commandLineRun = false;
      String to = getSelectedItem();
      roomConnectionParameters.initiator = true;
      roomConnectionParameters.to = to;

      connectToUser();
    }
  };
  private void connectToUser() {

    Intent newIntent = new Intent(this, CallActivity.class);
    newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    newIntent.putExtra("keep", true);
    newIntent.putExtras(intent);
    startActivityForResult(newIntent, CONNECTION_REQUEST);

  }


  private String getSelectedItem() {
    int position = AdapterView.INVALID_POSITION;
    if (roomListView.getCheckedItemCount() > 0 && adapter.getCount() > 0) {
      position = roomListView.getCheckedItemPosition();
      if (position >= adapter.getCount()) {
        position = AdapterView.INVALID_POSITION;
      }
    }
    if (position != AdapterView.INVALID_POSITION) {
      return (String) adapter.getItem(position);
    } else {
      return null;
    }
  }


  @Override
  public void onUserListUpdate(final String response) {

    runOnUiThread(new Runnable() {


      @Override
      public void run() {
        try {
          JSONArray mJSONArray = new JSONArray(response);
          userList = new ArrayList();
          adapter.clear();
          adapter.notifyDataSetChanged();


          for(int i = 0; i < mJSONArray.length();i++){
            String username = mJSONArray.getString(i);
            if (username.length() > 0
                    && !userList.contains(username)
                    && !username.equals(roomConnectionParameters.from)) {
              userList.add(username);
              adapter.add(username);
            }
          }
          adapter.notifyDataSetChanged();
        }catch (JSONException e) {
          e.printStackTrace();
        }
      }
    });
  }

  @Override
  public void onIncomingCall(final String from, final boolean screensharing) {

    roomConnectionParameters.to = from;
    roomConnectionParameters.initiator = false;


    if(screensharing){ //if its screensharing jsut re-connect without asking user!
       this.callActive=true;
       connectToUser();
    }
    else{
      DialogFragment newFragment = new RTCConnection.CallDialogFragment();
      Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

      if(alert == null){
        // alert is null, using backup
        alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // I can't see this ever being null (as always have a default notification)
        // but just incase
        if(alert == null) {
          // alert backup is null, using 2nd backup
          alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALL);
        }
      }
      r = RingtoneManager.getRingtone(getApplicationContext(), alert);
      r.play();

      FragmentTransaction transaction = getFragmentManager().beginTransaction();
      transaction.add(newFragment, "loading");
      transaction.commitAllowingStateLoss();
    }

  }

  @Override
  public void onIncomingScreenCall(JSONObject from) {
    // super.onIncomingScreenCall()
    logAndToast("Creating OFFER for Screensharing Caller");
    //do nothing here - just in CallActivity

    peerConnectionClient2 = PeerConnectionClient.getInstance(true);
    peerConnectionClient2.createPeerConnectionFactoryScreen(this);
    peerConnectionClient2.createPeerConnectionScreen(peerConnectionClient.getRenderEGLContext(),peerConnectionClient.getScreenRender());
    // Create offer. Offer SDP will be sent to answering client in
    // PeerConnectionEvents.onLocalDescription event.
    peerConnectionClient2.createOffer();

  }


  @Override
  public void onStartCommunication(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
          return;
        }
        logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
        peerConnectionClient.setRemoteDescription(sdp);
      }
    });
  }


  @Override
  public void onStartScreenCommunication(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient2 == null) {
          Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
          return;
        }
        logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
        peerConnectionClient2.setRemoteDescription(sdp);
      }
    });
  }

  @Override
  public void onRemoteDescription(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
          return;
        }
        logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
        peerConnectionClient.setRemoteDescription(sdp);
      }
    });
  }

  @Override
  public void onRemoteIceCandidate(final IceCandidate candidate) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG,
                  "Received ICE candidate for non-initilized peer connection.");
          return;
        }
        peerConnectionClient.addRemoteIceCandidate(candidate);
      }
    });
  }

  @Override
  public void onRemoteScreenDescription(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient2 == null) {
          Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
          return;
        }
        logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
        peerConnectionClient2.setRemoteDescription(sdp);
      }
    });
  }

  @Override
  public void onRemoteScreenIceCandidate(final IceCandidate candidate) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient2 == null) {
          Log.e(TAG,
                  "Received ICE candidate for non-initilized peer connection.");
          return;
        }
        peerConnectionClient2.addRemoteIceCandidate(candidate);
      }
    });
  }

  @Override
  public void onPing() { //A stop was received by the peer - now answering please send new call (e.g. with screensharing)
    SignalingService.appRTCClient.sendPong();
  }

  @Override
  public void onCallback() { //A stop was received by the peer - now answering please send new call (e.g. with screensharing)
    SignalingService.appRTCClient.sendCallback();
  }

  @Override
  public void onChannelClose() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("stopCommunication from remotereceived; finishing CallActivity");
        disconnect(false);
      }
    });
  }

  @Override
  public void onChannelScreenClose() {
    Intent intent = new Intent("finish_screensharing");
    sendBroadcast(intent);
  }

  @Override
  public void onChannelError(String description) {
    logAndToast(description);
  }

  private void registerNetworkChangeReceiver() {
    if (!isNetworkChangeReceiverRegistered) {
      LocalBroadcastManager.getInstance(this).registerReceiver(networkchangeBroadcastReceiver,
              new IntentFilter(QuickstartPreferences.NETWORK_ONLINE));

      isNetworkChangeReceiverRegistered = true;
    }
  }
  private void registerGCMReceiver() {
    if (!isGCMReceiverRegistered) {
      LocalBroadcastManager.getInstance(this).registerReceiver(gcmRegistrationBroadcastReceiver,
              new IntentFilter(QuickstartPreferences.REGISTRATION_COMPLETE));
      isGCMReceiverRegistered = true;
    }
  }

  private void registerBringToFrontReceiver() {
    if (!isBringToFrontReceiverRegistered) {
      LocalBroadcastManager.getInstance(this).registerReceiver(bringToFrontBroadcastReceiver,
              new IntentFilter(QuickstartPreferences.INCOMING_CALL));
      isBringToFrontReceiverRegistered = true;
    }
  }


  @Override
  public void onStart() {
    super.onStart();
  }

  @Override
  public void onStop() {
    super.onStop();

  }

  public static class CallDialogFragment extends DialogFragment {

    public CallDialogFragment() {

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      // Build the dialog and set up the button click handlers
      // 1. Instantiate an AlertDialog.Builder with its constructor
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

// 2. Chain together various setter methods to set the dialog characteristics
      builder.setMessage(R.string.calldialog_question).setTitle(R.string.calldialog_title);
      // Add the buttons
      builder.setPositiveButton(R.string.calldialog_answer, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {

          Intent intent = new Intent(getActivity(), CallActivity.class);
          intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
          r.stop();
          startActivityForResult(intent, CONNECTION_REQUEST);

        //  startActivity(intent);
        }
      });
      builder.setNegativeButton(R.string.calldialog_hangung, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          // User cancelled the dialog send stop message to peer.
          r.stop();
          SignalingService.appRTCClient.sendStopToPeer();
          ;
        }
      });

// 3. Get the AlertDialog from create()

      return builder.create();
    }
  }
}