package org.apache.cordova.plugin;


/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.Manifest;
import android.app.IntentService;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.Gson;

import com.eggersimone.mobirec.MainActivity;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.intentfilter.androidpermissions.PermissionManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;


/**
 * While subscribed in the background, this service shows a persistent notification with the
 * current set of messages from nearby beacons. Nearby launches this service when a message is
 * found or lost, and this service updates the notification, then stops itself.
 */
public class BackgroundSubscribeIntentService extends IntentService {
    private static final int MESSAGES_NOTIFICATION_ID = 1;
    private static final int NUM_MESSAGES_IN_NOTIFICATION = 5;
    private LocationManager locationManager;
    private Context mContext;
    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    public BackgroundSubscribeIntentService() {
        super("BackgroundSubscribeIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        updateNotification("First message");
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            Nearby.Messages.handleIntent(intent, new MessageListener() {
                @Override
                public void onFound(Message message) {
                    String msg = new String(message.getContent()).trim();
                    Log.i("Nearby",msg);
                    updateNotification(msg);
                    saveNearbyDevice(msg);
                }

                @Override
                public void onLost(Message message) {
                    updateNotification(new String(message.getContent()).trim());
                }
            });
        }
    }

    public boolean checkLocationPermission(){
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }




    public void startTracking() {
        if (checkLocationPermission()) {
            locationManager = (LocationManager) mContext.getSystemService(mContext.LOCATION_SERVICE);
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
    }



    private void saveNearbyDevice(String message) {
        String jsonLocation = null;
        if (checkLocationPermission()) {
            locationManager = (LocationManager) mContext.getSystemService(mContext.LOCATION_SERVICE);
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Gson gson = new Gson();
            jsonLocation = gson.toJson(lastKnownLocation);
        }
        SQLiteDatabase db = openOrCreateDatabase("db.mobirec",MODE_PRIVATE,null);
        long time = System.currentTimeMillis();
        db.execSQL("CREATE TABLE IF NOT EXISTS NearbyDevices(Name VARCHAR,Timestamp int, Location VARCHAR);");

        ContentValues insertValues = new ContentValues();
        insertValues.put("Name", message);
        insertValues.put("Timestamp", time);
        insertValues.put("Location", jsonLocation);
        db.insert("NearbyDevices", null, insertValues);

        db.close();
    }

    private void updateNotification(String message) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent launchIntent = new Intent(getApplicationContext(), MainActivity.class);
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String contentTitle = "Nearby peer found";
        String contentText = message;


        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setOngoing(true)
                .setContentIntent(pi);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder.setChannelId("my_channel_01");
            /* Create or update. */
            NotificationChannel channel = new NotificationChannel("my_channel_01",
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(MESSAGES_NOTIFICATION_ID, notificationBuilder.build());
    }
}

