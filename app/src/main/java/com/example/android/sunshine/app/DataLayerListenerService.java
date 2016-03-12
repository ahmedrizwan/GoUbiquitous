package com.example.android.sunshine.app;

import android.content.Intent;
import android.util.Log;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by ahmedrizwan on 12/03/2016.
 */
public class DataLayerListenerService extends WearableListenerService {
  private static final String START_ACTIVITY_PATH = "/start-activity";
  GoogleApiClient mGoogleApiClient;

  @Override
  public void onCreate() {
    super.onCreate();
    mGoogleApiClient = new GoogleApiClient.Builder(this)
        .addApi(Wearable.API)
        .build();
    mGoogleApiClient.connect();
  }

  @Override
  public void onMessageReceived(MessageEvent messageEvent) {
    Log.e("Mobile", "onMessageReceived: " + messageEvent);

    // Check to see if the message is to start an activity
    if (messageEvent.getPath().equals(START_ACTIVITY_PATH)) {
      Intent startIntent = new Intent(this, TestActivity.class);
      startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(startIntent);
    }
  }


}
