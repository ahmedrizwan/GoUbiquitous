/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.minimize.android.sunshinewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
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
import com.patloew.rxwear.transformers.MessageEventGetDataMap;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
  private static final Typeface NORMAL_TYPEFACE =
      Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

  /**
   * Update rate in milliseconds for interactive mode. We update once a second since seconds are
   * displayed in interactive mode.
   */
  private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1000);

  /**
   * Handler message id for updating the time periodically in interactive mode.
   */
  private static final int MSG_UPDATE_TIME = 0;

  @Override public Engine onCreateEngine() {
    return new Engine();
  }

  private static class EngineHandler extends Handler {
    private final WeakReference<MyWatchFace.Engine> mWeakReference;

    public EngineHandler(MyWatchFace.Engine reference) {
      mWeakReference = new WeakReference<>(reference);
    }

    @Override public void handleMessage(Message msg) {
      MyWatchFace.Engine engine = mWeakReference.get();
      if (engine != null) {
        switch (msg.what) {
          case MSG_UPDATE_TIME:
            engine.handleUpdateTimeMessage();
            break;
        }
      }
    }
  }

  private class Engine extends CanvasWatchFaceService.Engine
      implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener {
    final Handler mUpdateTimeHandler = new EngineHandler(this);
    boolean mRegisteredTimeZoneReceiver = false;
    Paint mBackgroundPaint;
    Paint mTimePaint;
    Paint mDatePaint;
    Paint mHighPaint;
    Paint mLowPaint;

    private GoogleApiClient mGoogleApiClient;

    boolean mAmbient;
    Time mTime;
    final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
      @Override public void onReceive(Context context, Intent intent) {
        mTime.clear(intent.getStringExtra("time-zone"));
        mTime.setToNow();
      }
    };
    int mTapCount;

    float mXOffset;
    float mYOffset;

    /**
     * Whether the display supports fewer bits for each color in ambient mode. When true, we
     * disable anti-aliasing in ambient mode.
     */
    boolean mLowBitAmbient;
    private String dateString = "";
    private String highString = "";
    private String lowString = "";
    private Bitmap mBitmapWeather;

    @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
      super.onSurfaceDestroyed(holder);
      Wearable.DataApi.removeListener(mGoogleApiClient, this);
    }

    @Override public void onConnected(@Nullable Bundle bundle) {
      Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override public void onConnectionSuspended(int i) {
      Wearable.DataApi.removeListener(mGoogleApiClient, this);
    }

    @Override public void onDataChanged(DataEventBuffer dataEvents) {
      for (DataEvent event : dataEvents) {
        if (event.getType() == DataEvent.TYPE_CHANGED) {
          String path = event.getDataItem().getUri().getPath();
          if ("/image".equals(path)) {
            //snackbar.dismiss();
            DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());

            final Asset photoAsset = dataMapItem.getDataMap().getAsset("icon");
            // Loads image on background thread.
            Observable<Bitmap> myObservable =
                Observable.create(new Observable.OnSubscribe<Bitmap>() {
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
                    //mImageView.setImageBitmap(mBitmapWeather);
                    mBitmapWeather = getResizedBitmap(bitmap, 80, 80);
                    invalidate();
                  }
                });
          }
        }
      }
    }

    @Override public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);
      RxWear.init(getApplicationContext());
      mGoogleApiClient =
          new GoogleApiClient.Builder(getApplicationContext()).addConnectionCallbacks(this)
              .addApi(Wearable.API)
              .build();
      mGoogleApiClient.connect();

      setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this).setCardPeekMode(
          WatchFaceStyle.PEEK_MODE_VARIABLE)
          .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
          .setShowSystemUiTime(false)
          .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL)
          .setAcceptsTapEvents(true)
          .build());

      Resources resources = MyWatchFace.this.getResources();
      mYOffset = resources.getDimension(R.dimen.digital_y_offset);

      mBackgroundPaint = new Paint();
      mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.primary));

      mTimePaint = new Paint();
      mTimePaint = createTextPaint(Color.WHITE);

      mDatePaint = new Paint();
      mDatePaint = createTextPaint(Color.WHITE);

      mHighPaint = new Paint();
      mHighPaint = createTextPaint(Color.WHITE);

      mLowPaint = new Paint();
      mLowPaint = createTextPaint(Color.WHITE);

      mTime = new Time();
      Calendar cal = Calendar.getInstance();
      SimpleDateFormat month_date = new SimpleDateFormat("MMM", Locale.US);
      dateString = month_date.format(cal.getTime());
      dateString = "Today, " + dateString + " " + cal.get(Calendar.DAY_OF_MONTH);
      //String suffix = "\u00B0";

      //highString = "15" + suffix;
      //lowString = "4" + suffix;
      mBitmapWeather = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear);

      mBitmapWeather = getResizedBitmap(mBitmapWeather, 1, 1);

      //Launch Mobile Sync from here
      getWeatherDataFromMobile();

      //Listen
      listenForWeather();


      RxWear.Message.listen()
          .compose(MessageEventGetDataMap.filterByPath("/wear_trigger_sync"))
          .subscribe(new Action1<DataMap>() {
            @Override public void call(DataMap dataMap) {
              getWeatherDataFromMobile();
            }
          }, new Action1<Throwable>() {
            @Override public void call(Throwable throwable) {
              Log.e("Wear", "ListenForWeather: " + throwable.getMessage());
            }
          });
    }

    private void listenForWeather() {
      RxWear.Data.listen()
          .compose(DataEventGetDataMap.filterByPathAndType("/weather", DataEvent.TYPE_CHANGED))
          .subscribe(new Action1<DataMap>() {
            @Override public void call(DataMap dataMap) {
              highString = dataMap.getString("temp_high");
              lowString = dataMap.getString("temp_low");
              Log.e("Wear", "High: " + highString + " and Low:" + lowString);
              invalidate();
            }
          }, new Action1<Throwable>() {
            @Override public void call(Throwable throwable) {
              Log.e("Wear", "ListenForWeather: " + throwable.getMessage());
            }
          });
    }

    private static final String START_ACTIVITY_PATH = "/start-activity";

    private void getWeatherDataFromMobile() {
      RxWear.Message.SendDataMap.toAllRemoteNodes(START_ACTIVITY_PATH)
          .putDouble("time", System.currentTimeMillis())
          .putString("message", "send_sync")
          .toObservable()
          .subscribe(new Action1<Integer>() {
            @Override public void call(Integer integer) {
              Log.e("Wear", integer.toString());
            }
          }, new Action1<Throwable>() {
            @Override public void call(Throwable throwable) {
              Log.e("Wear", "getWeatherData: " + throwable.getMessage());
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
              if (highString.equals("")) Log.e("Wear", "Connect with phone!");
            }
          });
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newHeight, int newWidth) {
      int width = bm.getWidth();
      int height = bm.getHeight();
      float scaleWidth = ((float) newWidth) / width;
      float scaleHeight = ((float) newHeight) / height;
      // create a matrix for the manipulation
      Matrix matrix = new Matrix();
      // resize the bit map
      matrix.postScale(scaleWidth, scaleHeight);
      // recreate the new Bitmap
      Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
      return resizedBitmap;
    }

    @Override public void onDestroy() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      super.onDestroy();
    }

    private Paint createTextPaint(int textColor) {
      Paint paint = new Paint();
      paint.setColor(textColor);
      paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
      paint.setAntiAlias(true);
      return paint;
    }

    @Override public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);

      if (visible) {
        registerReceiver();

        // Update time zone in case it changed while we weren't visible.
        mTime.clear(TimeZone.getDefault().getID());
        mTime.setToNow();
      } else {
        unregisterReceiver();
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    private void registerReceiver() {
      if (mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = true;
      IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
      MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
    }

    private void unregisterReceiver() {
      if (!mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = false;
      MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
    }

    @Override public void onApplyWindowInsets(WindowInsets insets) {
      super.onApplyWindowInsets(insets);

      // Load resources that have alternate values for round watches.
      Resources resources = MyWatchFace.this.getResources();
      boolean isRound = insets.isRound();
      mXOffset = resources.getDimension(
          isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
      mYOffset = resources.getDimension(
          isRound ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
      float timeSize = resources.getDimension(
          isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
      float dateSize = resources.getDimension(
          isRound ? R.dimen.digital_date_size_round : R.dimen.digital_date_size);
      mTimePaint.setTextSize(timeSize);
      mDatePaint.setTextSize(dateSize);
      mHighPaint.setTextSize(timeSize);
      mLowPaint.setTextSize(dateSize);
    }

    @Override public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);
      mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
    }

    @Override public void onTimeTick() {
      super.onTimeTick();
      invalidate();
    }

    @Override public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);
      if (mAmbient != inAmbientMode) {
        mAmbient = inAmbientMode;
        if (mLowBitAmbient) {
          mTimePaint.setAntiAlias(!inAmbientMode);
        }
        invalidate();
      }

      // Whether the timer should be running depends on whether we're visible (as well as
      // whether we're in ambient mode), so we may need to start or stop the timer.
      updateTimer();
    }

    @Override public void onDraw(Canvas canvas, Rect bounds) {
      // Draw the background.
      mTime.setToNow();

      String text = String.format("%02d:%02d", mTime.hour, mTime.minute);

      final int middle = canvas.getWidth() / 2;
      final int xPos = (int) (middle - (mTimePaint.measureText(text) / 2));
      final int xDate = (int) (middle - mDatePaint.measureText(dateString) / 2);
      final float timeHeight = mTimePaint.getTextSize();

      if (isInAmbientMode()) {
        canvas.drawColor(Color.BLACK);
      } else {
        canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
        canvas.drawText(dateString, xDate, mYOffset - timeHeight, mDatePaint);
        final float yHigh = mYOffset + timeHeight;

        canvas.drawText(highString, middle - mHighPaint.measureText(highString), yHigh, mHighPaint);
        canvas.drawText(lowString, middle - mLowPaint.measureText(lowString),
            yHigh + mLowPaint.getTextSize(), mLowPaint);

        canvas.drawBitmap(mBitmapWeather, middle, mYOffset, mBackgroundPaint);
      }
      canvas.drawText(text, xPos, mYOffset, mTimePaint);
    }

    /**
     * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
     * or stops it if it shouldn't be running but currently is.
     */
    private void updateTimer() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      if (shouldTimerBeRunning()) {
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
      }
    }

    /**
     * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
     * only run when we're visible and in interactive mode.
     */
    private boolean shouldTimerBeRunning() {
      return isVisible() && !isInAmbientMode();
    }

    /**
     * Handle updating the time periodically in interactive mode.
     */
    private void handleUpdateTimeMessage() {
      invalidate();
      if (shouldTimerBeRunning()) {
        long timeMs = System.currentTimeMillis();
        long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
        mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
      }
    }
  }
}
