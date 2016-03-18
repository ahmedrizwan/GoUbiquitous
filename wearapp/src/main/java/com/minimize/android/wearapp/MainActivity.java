package com.minimize.android.wearapp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.patloew.rxwear.RxWear;
import com.patloew.rxwear.transformers.DataEventGetDataMap;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class MainActivity extends Activity
    implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener {

  private static final String START_ACTIVITY_PATH = "/start-activity";
  private ImageView mImageView;
  private TextView textViewTime;
  private TextView textViewDate;

  private TextView textViewTempHigh;
  private TextView textViewTempLow;

  private CompositeSubscription subscription = new CompositeSubscription();
  private GoogleApiClient mGoogleApiClient;

  private Snackbar snackbar;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    RxWear.init(this);
    mGoogleApiClient =
        new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addApi(Wearable.API).build();

    final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

    stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
      @Override public void onLayoutInflated(WatchViewStub stub) {
        mImageView = (ImageView) stub.findViewById(R.id.weather_status_image);
        textViewTime = (TextView) stub.findViewById(R.id.text_view_time);
        textViewTempHigh = (TextView) stub.findViewById(R.id.text_view_temp_high);
        textViewTempLow = (TextView) stub.findViewById(R.id.text_view_temp_low);
        textViewDate = (TextView) stub.findViewById(R.id.text_view_date);
        snackbar = Snackbar.make(stub.getRootView(), "", Snackbar.LENGTH_INDEFINITE)
            .setAction("Retry", new View.OnClickListener() {
              @Override public void onClick(View v) {
                RxWear.Data.listen()
                    .compose(
                        DataEventGetDataMap.filterByPathAndType("/error", DataEvent.TYPE_CHANGED))
                    .subscribe(new Action1<DataMap>() {
                      @Override public void call(DataMap dataMap) {
                        String message = dataMap.getString("message");
                        textViewDate.setText(message);
                      }
                    });

                //Launch Mobile Sync from here
                getWeatherDataFromMobile();

                //Listen
                listenForWeather();
              }
            });
        setTime(textViewTime);
      }
    });

    RxWear.Data.listen()
        .compose(DataEventGetDataMap.filterByPathAndType("/error", DataEvent.TYPE_CHANGED))
        .subscribe(new Action1<DataMap>() {
          @Override public void call(DataMap dataMap) {
            String message = dataMap.getString("message");
            textViewDate.setText(message);
            snackbar.show();
          }
        });

    //Launch Mobile Sync from here
    getWeatherDataFromMobile();

    //Listen
    listenForWeather();
  }

  private void listenForWeather() {
    RxWear.Data.listen()
        .compose(DataEventGetDataMap.filterByPathAndType("/weather", DataEvent.TYPE_CHANGED))
        .subscribe(new Action1<DataMap>() {
          @Override public void call(DataMap dataMap) {
            String high = dataMap.getString("temp_high");
            String low = dataMap.getString("temp_low");
            textViewTempHigh.setText(high);
            textViewTempLow.setText(low);
            String date = dataMap.getString("date");
            textViewDate.setText(date);
            snackbar.dismiss();
          }
        });
  }

  private void getWeatherDataFromMobile() {
    RxWear.Message.SendDataMap.toAllRemoteNodes(START_ACTIVITY_PATH)
        .putDouble("time", System.currentTimeMillis())
        .putString("message", "send_sync")
        .toObservable()
        .subscribe(new Action1<Integer>() {
          @Override public void call(Integer integer) {
            Log.e("Wear", integer.toString());
          }
        });

    //Timer for checking if I got any message back
    Observable.create(new Observable.OnSubscribe<Object>() {
      @Override public void call(Subscriber<? super Object> subscriber) {
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        subscriber.onNext(null);
        subscriber.onCompleted();
      }
    })
        .subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Object>() {
          @Override public void call(Object o) {
            String text = "Please connect with Phone!";
            if (textViewDate.getText().equals("")) {
              textViewDate.setText(text);
              snackbar.show();
            }
          }
        });
  }

  private void setTime(final TextView textViewTime) {
    Observable.create(new Observable.OnSubscribe<String>() {
      @Override public void call(Subscriber<? super String> subscriber) {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        final String strDate = sdf.format(c.getTime());
        subscriber.onNext(strDate);
        subscriber.onCompleted();
      }
    }).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<String>() {
      @Override public void call(String s) {
        textViewTime.setText(s);
      }
    });
    Observable.just("").delay(1, TimeUnit.MINUTES).subscribe(new Action1<Object>() {
      @Override public void call(Object o) {
        setTime(textViewTime);
      }
    });
  }

  @Override protected void onDestroy() {
    super.onDestroy();

    if (subscription != null && !subscription.isUnsubscribed()) {
      subscription.unsubscribe();
    }
  }

  @Override public void onDataChanged(DataEventBuffer dataEvents) {
    for (DataEvent event : dataEvents) {
      if (event.getType() == DataEvent.TYPE_CHANGED) {
        String path = event.getDataItem().getUri().getPath();
        if ("/image".equals(path)) {
          snackbar.dismiss();
          DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());

          final Asset photoAsset = dataMapItem.getDataMap().getAsset("icon");
          // Loads image on background thread.
          Observable<Bitmap> myObservable = Observable.create(new Observable.OnSubscribe<Bitmap>() {
            @Override public void call(Subscriber<? super Bitmap> subscriber) {
              InputStream assetInputStream =
                  Wearable.DataApi.getFdForAsset(mGoogleApiClient, photoAsset)
                      .await()
                      .getInputStream();

              if (assetInputStream == null) {
                subscriber.onError(new Exception("Unknown Asset"));
              }
              subscriber.onNext(BitmapFactory.decodeStream(assetInputStream));
              subscriber.onCompleted();
            }
          });

          myObservable.subscribeOn(Schedulers.newThread())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe(new Subscriber<Bitmap>() {
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

  @Override public void onConnected(@Nullable Bundle bundle) {
    Wearable.DataApi.addListener(mGoogleApiClient, this);
  }

  @Override public void onConnectionSuspended(int i) {

  }

  @Override protected void onPause() {
    super.onPause();
    Wearable.DataApi.removeListener(mGoogleApiClient, this);
  }

  @Override protected void onResume() {
    super.onResume();
    mGoogleApiClient.connect();
  }
}
