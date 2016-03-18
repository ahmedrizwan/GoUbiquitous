package com.example.android.sunshine.app;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.widget.Toast;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.patloew.rxwear.RxWear;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import rx.functions.Action1;

/**
 * Created by ahmedrizwan on 12/03/2016.
 */
public class DataLayerListenerService extends WearableListenerService
    implements Loader.OnLoadCompleteListener<Cursor> {
  private static final String START_ACTIVITY_PATH = "/start-activity";
  GoogleApiClient mGoogleApiClient;
  private CursorLoader mCursorLoader;

  @Override public void onCreate() {
    super.onCreate();
    RxWear.init(this);
    mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(Wearable.API).build();
    mGoogleApiClient.connect();
  }

  @Override public void onMessageReceived(MessageEvent messageEvent) {
    // Check to see if the message is to start an activity
    if (messageEvent.getPath().equals(START_ACTIVITY_PATH)) {
      //Intent startIntent = new Intent(this, TestActivity.class);
      //startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      //startActivity(startIntent);
      //Trigger Sync
      Toast.makeText(this, "Hello", Toast.LENGTH_SHORT).show();
      // Sort order:  Ascending, by date.
      String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";

      String locationSetting = Utility.getPreferredLocation(this);
      Uri weatherForLocationUri =
          WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(locationSetting,
              System.currentTimeMillis());
      mCursorLoader =
          new CursorLoader(this, weatherForLocationUri, ForecastFragment.FORECAST_COLUMNS, null,
              null, sortOrder);
      mCursorLoader.registerListener(0, this);
      mCursorLoader.startLoading();
    }
  }

  @Override public void onDestroy() {
    // Stop the cursor loader
    if (mCursorLoader != null) {
      mCursorLoader.unregisterListener(this);
      mCursorLoader.cancelLoad();
      mCursorLoader.stopLoading();
    }
  }

  @Override public void onLoadComplete(Loader<Cursor> loader, Cursor data) {
    // Read high temperature from cursor
    try {
      data.moveToFirst();
      sendWeatherData(data);
      sendPhoto(data);
    }catch (Exception e){
      RxWear.Data.PutDataMap.to("/error")
          .putString("message", "Please connect with Phone!")
          .toObservable()
          .subscribe(new Action1<DataItem>() {
            @Override public void call(DataItem dataItem) {
              Log.e("Mobile", "Sent to Wear");
            }
          });
    }
  }

  private void sendPhoto(Cursor data) {
    PutDataMapRequest dataMap = PutDataMapRequest.create("/image");
    dataMap.getDataMap().putAsset("icon", getWeatherAsset(data));
    dataMap.getDataMap().putLong("time", new Date().getTime());
    PutDataRequest request = dataMap.asPutDataRequest();
    request.setUrgent();

    Wearable.DataApi.putDataItem(mGoogleApiClient, request)
        .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
          @Override public void onResult(DataApi.DataItemResult dataItemResult) {
            Log.e("App", "Sending image was successful: " + dataItemResult.getStatus().isSuccess());
          }
        });
  }

  private void sendWeatherData(Cursor data) {
    double high = data.getDouble(ForecastFragment.COL_WEATHER_MAX_TEMP);
    String highString = Utility.formatTemperature(this, high);
    double low = data.getDouble(ForecastFragment.COL_WEATHER_MIN_TEMP);
    String lowString = Utility.formatTemperature(this, low);
    // Read date from cursor
    long dateInMillis = data.getLong(ForecastFragment.COL_WEATHER_DATE);

    // Find TextView and set formatted date on it
    String dayString = Utility.getFriendlyDayString(this, dateInMillis, true);

    RxWear.Data.PutDataMap.to("/weather")
        .putString("temp_high", highString)
        .putString("temp_low",lowString)
        .putDouble("time", new Date().getTime())
        .putString("date", dayString)
        .toObservable()
        .subscribe(new Action1<DataItem>() {
          @Override public void call(DataItem dataItem) {
            Log.e("Mobile", "Sent to Wear");
          }
        });
  }

  private Asset getWeatherAsset(Cursor data) {
    int weatherId = data.getInt(ForecastFragment.COL_WEATHER_CONDITION_ID);
    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear);
    ;
    if (weatherId >= 200 && weatherId <= 232) {
      //send storm image
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_storm);
    } else if (weatherId >= 300 && weatherId <= 321) {
      //send light rain
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_light_rain);
    } else if (weatherId >= 500 && weatherId <= 504) {
      //send rain
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_rain);
    } else if (weatherId == 511) {
      //send snow
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_snow);
    } else if (weatherId >= 520 && weatherId <= 531) {
      //send rain
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_rain);
    } else if (weatherId >= 600 && weatherId <= 622) {
      //send snow
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_snow);
    } else if (weatherId >= 701 && weatherId <= 761) {
      //send fog
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_fog);
    } else if (weatherId == 761 || weatherId == 781) {
      //send storm
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_storm);
    } else if (weatherId == 800) {
      //send clear
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear);
    } else if (weatherId == 801) {
      //send light clouds
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_light_clouds);
    } else if (weatherId >= 802 && weatherId <= 804) {
      //send clouds
      bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_clouds);
    }
    return createAssetFromBitmap(bitmap);
  }

  private static Asset createAssetFromBitmap(Bitmap bitmap) {
    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 50, byteStream);
    return Asset.createFromBytes(byteStream.toByteArray());
  }
}
