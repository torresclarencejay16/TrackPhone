package com.example.trackphone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

public class InternetBackgroundService extends Service {
    public static final String SERVICE_CHANNEL_ID = "internet_tracking_service_channel";
    private static final int SERVICE_NOTIFICATION_ID = 2001;
    private static final long POLL_INTERVAL_MS = 15000; // 15 seconds

    private int pollCounter = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            Context context = getApplicationContext();
            InternetMessagingManager.checkIncomingInternetRequests(context);

            pollCounter++;
            // Push live location update every 2 minutes (every 8 polls of 15s)
            if (pollCounter >= 8) {
                pollCounter = 0;
                InternetMessagingManager.pushLiveLocationUpdate(context);
            }

            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification());
        handler.post(pollRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "Internet Track Phone Listener",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Syncs live location updates and listens for remote location requests over internet.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setContentTitle("TrackPhone Internet Service Active")
                .setContentText("Syncing live location & listening for requests...")
                .setSmallIcon(R.drawable.ic_location_pin)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
