package com.minimize.android.wearapp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import java.io.InputStream;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {

  private TextView mTextView;
  private ImageView mImageView;

  private GoogleApiClient mGoogleApiClient;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
    stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
      @Override public void onLayoutInflated(WatchViewStub stub) {
        mTextView = (TextView) stub.findViewById(R.id.text);
        mImageView = (ImageView) stub.findViewById(R.id.weather_status_image);
      }
    });

    mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
  }

  @Override protected void onResume() {
    super.onResume();
    mGoogleApiClient.connect();
  }

  @Override protected void onPause() {
    super.onPause();
    Wearable.DataApi.removeListener(mGoogleApiClient, this);
    mGoogleApiClient.disconnect();
  }

  @Override public void onConnected(@Nullable Bundle bundle) {
    Wearable.DataApi.addListener(mGoogleApiClient, this);
  }

  @Override public void onConnectionSuspended(int cause) {
    Log.e("Wear", "onConnectionSuspended(): Connection to Google API client was suspended");
  }

  @Override public void onConnectionFailed(ConnectionResult result) {
    Log.e("Wear", "onConnectionFailed(): Failed to connect, with result: " + result);
  }

  @Override public void onDataChanged(DataEventBuffer dataEvents) {
    Log.e("Wear", "DataChange!!");
    for (DataEvent event : dataEvents) {
      if (event.getType() == DataEvent.TYPE_CHANGED) {
        String path = event.getDataItem().getUri().getPath();
        if ("/image".equals(path)) {
          DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
          final Asset photoAsset = dataMapItem.getDataMap().getAsset("weather");
          // Loads image on background thread.
          Observable<Bitmap> myObservable = Observable.create(new Observable.OnSubscribe<Bitmap>() {
            @Override public void call(Subscriber<? super Bitmap> subscriber) {
              InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, photoAsset).await().getInputStream();

              if (assetInputStream == null) {
                subscriber.onError(new Exception("Unknown Asset"));
              }
              subscriber.onNext(BitmapFactory.decodeStream(assetInputStream));
              subscriber.onCompleted();
            }
          });

          myObservable.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<Bitmap>() {
            @Override public void onCompleted() {

            }

            @Override public void onError(Throwable e) {
              Log.e("Error", e.toString());
            }

            @Override public void onNext(Bitmap bitmap) {
              mImageView.setImageBitmap(bitmap);
            }
          });
        }
      }
    }
  }
}
