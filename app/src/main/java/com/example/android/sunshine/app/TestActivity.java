package com.example.android.sunshine.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

public class TestActivity extends Activity
    implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {

  private GoogleApiClient mGoogleApiClient;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_test);
    mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(Wearable.API).build();
    mGoogleApiClient.connect();

    findViewById(R.id.clickButton).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear);
        Asset asset = createAssetFromBitmap(bitmap);
        sendPhoto(asset);
      }
    });
  }

  @Override protected void onPause() {
    super.onPause();
  }

  private void sendPhoto(Asset asset) {
    PutDataMapRequest dataMap = PutDataMapRequest.create("/image");
    dataMap.getDataMap().putAsset("weather", asset);
    dataMap.getDataMap().putLong("time", new Date().getTime());
    PutDataRequest request = dataMap.asPutDataRequest();
    request.setUrgent();

    Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
      @Override public void onResult(DataApi.DataItemResult dataItemResult) {
        Log.e("App", "Sending image was successful: " + dataItemResult.getStatus().isSuccess());
      }
    });
  }

  private static Asset createAssetFromBitmap(Bitmap bitmap) {
    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
    return Asset.createFromBytes(byteStream.toByteArray());
  }

  private static Asset toAsset(Bitmap bitmap) {
    ByteArrayOutputStream byteStream = null;
    try {
      byteStream = new ByteArrayOutputStream();
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
      return Asset.createFromBytes(byteStream.toByteArray());
    } finally {
      if (null != byteStream) {
        try {
          byteStream.close();
        } catch (IOException e) {
          // ignore
        }
      }
    }
  }

  @Override public void onConnected(@Nullable Bundle bundle) {
    Log.e("App", "Connected");
    Wearable.MessageApi.addListener(mGoogleApiClient, this);
  }

  @Override protected void onStop() {
    super.onStop();
    Wearable.MessageApi.removeListener(mGoogleApiClient, this);
  }

  @Override public void onConnectionSuspended(int i) {
    Log.e("App", "Suspended");
  }

  @Override public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    Log.e("App", "Failed " + connectionResult.getErrorMessage());
    Wearable.MessageApi.removeListener(mGoogleApiClient, this);
  }

  public static final String TRIGGER_SYNC_PATH = "/trigger_sync_sunshine";

  @Override public void onMessageReceived(MessageEvent messageEvent) {
    Toast.makeText(TestActivity.this, "onMessageReceived", Toast.LENGTH_SHORT).show();
    if (messageEvent.getPath().equals(TRIGGER_SYNC_PATH)) {
      Toast.makeText(TestActivity.this, "Trigger Sync", Toast.LENGTH_SHORT).show();
    }
  }
}
