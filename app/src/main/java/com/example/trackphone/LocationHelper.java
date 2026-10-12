package com.example.trackphone;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LocationHelper {
    private static final String TAG = "LocationHelper";
    private static final String PREF_LAST_LAT = "last_known_lat";
    private static final String PREF_LAST_LON = "last_known_lon";
    private static final String PREF_LAST_TIME = "last_known_time";

    public interface LocationResultCallback {
        void onLocationFound(double lat, double lon, boolean isLastKnown, String timeText);
        void onLocationError(String errorMessage);
    }

    public static void saveLastKnownLocation(Context context, double lat, double lon) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(SmsReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(PREF_LAST_LAT, String.valueOf(lat))
                .putString(PREF_LAST_LON, String.valueOf(lon))
                .putLong(PREF_LAST_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void getBestLocation(Context context, LocationResultCallback callback) {
        if (context == null || callback == null) return;

        FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(context);

        try {
            CancellationTokenSource cts = new CancellationTokenSource();
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            saveLastKnownLocation(context, location.getLatitude(), location.getLongitude());
                            callback.onLocationFound(location.getLatitude(), location.getLongitude(), false, "Fresh GPS Fix");
                        } else {
                            fallbackToLastKnownLocation(context, fusedClient, callback);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "getCurrentLocation failed, falling back", e);
                        fallbackToLastKnownLocation(context, fusedClient, callback);
                    });
        } catch (SecurityException e) {
            callback.onLocationError("Location permission missing: " + e.getLocalizedMessage());
        }
    }

    private static void fallbackToLastKnownLocation(Context context, FusedLocationProviderClient fusedClient, LocationResultCallback callback) {
        try {
            fusedClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    saveLastKnownLocation(context, location.getLatitude(), location.getLongitude());
                    String timeStr = formatTimestamp(location.getTime());
                    callback.onLocationFound(location.getLatitude(), location.getLongitude(), true, timeStr);
                } else {
                    fallbackToSystemLocationManager(context, callback);
                }
            }).addOnFailureListener(e -> fallbackToSystemLocationManager(context, callback));
        } catch (SecurityException e) {
            fallbackToSystemLocationManager(context, callback);
        }
    }

    private static void fallbackToSystemLocationManager(Context context, LocationResultCallback callback) {
        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location gpsLocation = null;
            Location netLocation = null;

            if (locationManager != null) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    netLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }

            Location bestLocation = gpsLocation;
            if (bestLocation == null || (netLocation != null && netLocation.getTime() > bestLocation.getTime())) {
                bestLocation = netLocation;
            }

            if (bestLocation != null) {
                saveLastKnownLocation(context, bestLocation.getLatitude(), bestLocation.getLongitude());
                String timeStr = formatTimestamp(bestLocation.getTime());
                callback.onLocationFound(bestLocation.getLatitude(), bestLocation.getLongitude(), true, timeStr);
            } else {
                fallbackToSavedSharedPreferences(context, callback);
            }
        } catch (SecurityException e) {
            fallbackToSavedSharedPreferences(context, callback);
        }
    }

    private static void fallbackToSavedSharedPreferences(Context context, LocationResultCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(SmsReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        String latStr = prefs.getString(PREF_LAST_LAT, null);
        String lonStr = prefs.getString(PREF_LAST_LON, null);
        long lastTime = prefs.getLong(PREF_LAST_TIME, 0);

        if (latStr != null && lonStr != null) {
            try {
                double lat = Double.parseDouble(latStr);
                double lon = Double.parseDouble(lonStr);
                String timeStr = formatTimestamp(lastTime > 0 ? lastTime : System.currentTimeMillis());
                callback.onLocationFound(lat, lon, true, timeStr + " (Stored Cache)");
                return;
            } catch (NumberFormatException ignored) {}
        }

        callback.onLocationError("No location available. Ensure GPS is turned ON and permissions granted.");
    }

    private static String formatTimestamp(long timeMs) {
        if (timeMs <= 0) return "Recent";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(timeMs));
    }
}
