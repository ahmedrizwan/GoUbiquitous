package com.example.android.sunshine.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import java.io.ByteArrayOutputStream;

public class TestActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

  private GoogleApiClient mGoogleApiClient;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_test);
    mGoogleApiClient = new GoogleApiClient.Builder(this)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi(Wearable.API)
        .build();
    Log.e("OnCreate", "Before");
    mGoogleApiClient.connect();

    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_clear);
    Asset asset = createAssetFromBitmap(bitmap);
    PutDataRequest request = PutDataRequest.create("/image");
    request.putAsset("weather", asset);
    Wearable.DataApi.putDataItem(mGoogleApiClient, request);
    Log.e("OnCreate", "After");
  }

  private static Asset createAssetFromBitmap(Bitmap bitmap) {
    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
    return Asset.createFromBytes(byteStream.toByteArray());
  }

  @Override public void onConnected(@Nullable Bundle bundle) {
    Log.e("Wear", "Connected");
  }

  @Override public void onConnectionSuspended(int i) {
    Log.e("Wear", "Suspended");
  }

  @Override public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    Log.e("Wear", "Failed "+connectionResult.getErrorMessage());
  }
}
