package com.example.trackphone;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class InternetMessagingManager {
    private static final String TAG = "InternetMessaging";
    private static final String BASE_URL = "https://ntfy.sh/";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient client = new OkHttpClient();

    public interface LocationResponseCallback {
        void onSuccess(double lat, double lon, String mapsUrl, String fromDevice);
        void onError(String errorMessage);
    }

    public interface SendRequestCallback {
        void onRequestSent();
        void onError(String errorMessage);
    }

    // Clean target ID to create a safe channel name
    private static String getChannelName(String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) return "default_device";
        String clean = rawId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.US);
        return "tp_" + (clean.isEmpty() ? "default_device" : clean);
    }

    public static String getMyDeviceIdentifier(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SmsReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        String myId = prefs.getString("this_device_number", "");
        if (myId == null || myId.trim().isEmpty()) {
            myId = prefs.getString(SmsReceiver.KEY_TRUSTED_NUMBER, "");
        }
        if (myId == null || myId.trim().isEmpty()) {
            myId = "default_device";
        }
        return myId.trim();
    }

    // Push live location update to cloud feed automatically when internet is on
    public static void pushLiveLocationUpdate(Context context) {
        LocationHelper.getBestLocation(context, new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationFound(double lat, double lon, boolean isLastKnown, String timeText) {
                String myId = getMyDeviceIdentifier(context);
                String channel = getChannelName(myId) + "_live";

                try {
                    String mapsUrl = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);
                    JSONObject json = new JSONObject();
                    json.put("action", "LIVE_LOCATION");
                    json.put("lat", lat);
                    json.put("lon", lon);
                    json.put("mapsUrl", mapsUrl);
                    json.put("isLastKnown", isLastKnown);
                    json.put("timeText", timeText);
                    json.put("timestamp", System.currentTimeMillis());

                    Request request = new Request.Builder()
                            .url(BASE_URL + channel)
                            .post(RequestBody.create(json.toString(), JSON_MEDIA_TYPE))
                            .build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Log.d(TAG, "Failed to push live location update: " + e.getLocalizedMessage());
                        }

                        @Override
                        public void onResponse(Call call, Response response) {
                            response.close();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error constructing live location payload", e);
                }
            }

            @Override
            public void onLocationError(String errorMessage) {
                Log.d(TAG, "Live location push skipped: " + errorMessage);
            }
        });
    }

    // Fetch live location feed for target phone
    public static void fetchLiveLocationFeed(Context context, String targetPhoneId, LocationResponseCallback callback) {
        String channel = getChannelName(targetPhoneId) + "_live";

        Request request = new Request.Builder()
                .url(BASE_URL + channel + "/json?poll=1&limit=1")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) callback.onError(e.getLocalizedMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    try {
                        String[] lines = body.split("\n");
                        boolean found = false;
                        for (String line : lines) {
                            if (line == null || line.trim().isEmpty()) continue;
                            JSONObject event = new JSONObject(line.trim());
                            if (event.has("message")) {
                                String msg = event.optString("message");
                                if (msg.startsWith("{")) {
                                    JSONObject payload = new JSONObject(msg);
                                    if ("LIVE_LOCATION".equals(payload.optString("action"))) {
                                        double lat = payload.optDouble("lat");
                                        double lon = payload.optDouble("lon");
                                        String mapsUrl = payload.optString("mapsUrl");
                                        found = true;
                                        if (callback != null) {
                                            callback.onSuccess(lat, lon, mapsUrl, targetPhoneId);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        if (!found && callback != null) {
                            callback.onError("No live cloud location found for " + targetPhoneId);
                        }
                    } catch (Exception e) {
                        if (callback != null) callback.onError("Parsing error: " + e.getLocalizedMessage());
                    }
                } else {
                    if (callback != null) callback.onError("Target phone live feed not found or offline.");
                }
                response.close();
            }
        });
    }

    // Send a location request via internet
    public static void sendInternetLocationRequest(Context context, String targetPhoneId, String triggerKeyword, SendRequestCallback callback) {
        String myId = getMyDeviceIdentifier(context);
        String channel = getChannelName(targetPhoneId);
        String replyChannel = getChannelName(myId) + "_reply";

        try {
            JSONObject json = new JSONObject();
            json.put("action", "LOCATION_REQUEST");
            json.put("from", myId);
            json.put("keyword", triggerKeyword);
            json.put("reply_channel", replyChannel);

            Request request = new Request.Builder()
                    .url(BASE_URL + channel)
                    .post(RequestBody.create(json.toString(), JSON_MEDIA_TYPE))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send internet location request", e);
                    if (callback != null) {
                        callback.onError(e.getLocalizedMessage());
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (response.isSuccessful()) {
                        Log.i(TAG, "Internet location request sent to channel: " + channel);
                        if (callback != null) {
                            callback.onRequestSent();
                        }
                    } else {
                        if (callback != null) {
                            callback.onError("Server returned code " + response.code());
                        }
                    }
                    response.close();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error constructing JSON request", e);
            if (callback != null) callback.onError(e.getLocalizedMessage());
        }
    }

    // Poll/check for incoming internet location requests
    public static void checkIncomingInternetRequests(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SmsReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        boolean isTermsAccepted = prefs.getBoolean("terms_accepted", false);
        boolean isTrackingEnabled = prefs.getBoolean(SmsReceiver.KEY_TRACKING_ENABLED, true);

        if (!isTermsAccepted || !isTrackingEnabled) {
            return;
        }

        String myPhoneId = getMyDeviceIdentifier(context);
        String channel = getChannelName(myPhoneId);
        String savedKeyword = prefs.getString(SmsReceiver.KEY_TRIGGER_KEYWORD, SmsReceiver.DEFAULT_TRIGGER_KEYWORD);
        String expectedKeyword = (savedKeyword != null && !savedKeyword.trim().isEmpty()) ? savedKeyword.trim() : SmsReceiver.DEFAULT_TRIGGER_KEYWORD;
        String trustedNumber = prefs.getString(SmsReceiver.KEY_TRUSTED_NUMBER, "");

        Request request = new Request.Builder()
                .url(BASE_URL + channel + "/json?poll=1&since=10m")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "Internet request polling failed: " + e.getLocalizedMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    processIncomingJsonPayloads(context, body, expectedKeyword, trustedNumber);
                }
                response.close();
            }
        });
    }

    private static void processIncomingJsonPayloads(Context context, String responseBody, String expectedKeyword, String trustedNumber) {
        try {
            String[] lines = responseBody.split("\n");
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) continue;

                JSONObject event = new JSONObject(line.trim());
                if (event.has("message")) {
                    String messageStr = event.optString("message");
                    if (messageStr.startsWith("{")) {
                        JSONObject payload = new JSONObject(messageStr);
                        if ("LOCATION_REQUEST".equals(payload.optString("action"))) {
                            String fromSender = payload.optString("from");
                            String reqKeyword = payload.optString("keyword");
                            String replyChannel = payload.optString("reply_channel");

                            if (reqKeyword.equalsIgnoreCase(expectedKeyword)) {
                                if (isSenderAuthorized(fromSender, trustedNumber)) {
                                    handleLocationRequestAndRespond(context, fromSender, replyChannel);
                                } else {
                                    Log.w(TAG, "Internet location request rejected from unauthorized sender: " + fromSender);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing incoming JSON payloads", e);
        }
    }

    private static boolean isSenderAuthorized(String sender, String trustedNumber) {
        if (trustedNumber == null || trustedNumber.trim().isEmpty()) {
            return true;
        }
        if (sender == null || sender.trim().isEmpty()) {
            return false;
        }
        String cleanSender = sender.replaceAll("[^0-9]", "");
        String cleanTrusted = trustedNumber.replaceAll("[^0-9]", "");
        if (cleanSender.isEmpty() || cleanTrusted.isEmpty()) {
            return true;
        }
        return cleanSender.endsWith(cleanTrusted) || cleanTrusted.endsWith(cleanSender);
    }

    private static void handleLocationRequestAndRespond(Context context, String requesterPhone, String replyChannel) {
        LocationHelper.getBestLocation(context, new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationFound(double lat, double lon, boolean isLastKnown, String timeText) {
                sendInternetReply(context, requesterPhone, replyChannel, lat, lon, isLastKnown, timeText);
            }

            @Override
            public void onLocationError(String errorMessage) {
                Log.e(TAG, "Could not fetch location for internet reply: " + errorMessage);
            }
        });
    }

    private static void sendInternetReply(Context context, String requesterPhone, String replyChannel, double lat, double lon, boolean isLastKnown, String timeText) {
        try {
            String mapsUrl = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);
            JSONObject json = new JSONObject();
            json.put("action", "LOCATION_RESPONSE");
            json.put("lat", lat);
            json.put("lon", lon);
            json.put("mapsUrl", mapsUrl);
            json.put("isLastKnown", isLastKnown);
            json.put("timeText", timeText);
            json.put("from", requesterPhone);

            Request request = new Request.Builder()
                    .url(BASE_URL + replyChannel)
                    .post(RequestBody.create(json.toString(), JSON_MEDIA_TYPE))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send internet location reply", e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    Log.i(TAG, "Internet location reply sent successfully to " + replyChannel);
                    response.close();
                }
            });

            // Trigger system notification
            NotificationHelper.showLocationSharedNotification(context, requesterPhone);

        } catch (Exception e) {
            Log.e(TAG, "Error sending internet reply", e);
        }
    }

    // Listen for response on reply channel
    public static void listenForInternetResponse(Context context, LocationResponseCallback callback) {
        String myId = getMyDeviceIdentifier(context);
        String replyChannel = getChannelName(myId) + "_reply";

        Request request = new Request.Builder()
                .url(BASE_URL + replyChannel + "/json?poll=1&since=10m")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    callback.onError(e.getLocalizedMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    try {
                        String[] lines = body.split("\n");
                        boolean found = false;
                        for (String line : lines) {
                            if (line == null || line.trim().isEmpty()) continue;
                            JSONObject event = new JSONObject(line.trim());
                            if (event.has("message")) {
                                String msg = event.optString("message");
                                if (msg.startsWith("{")) {
                                    JSONObject payload = new JSONObject(msg);
                                    if ("LOCATION_RESPONSE".equals(payload.optString("action"))) {
                                        double lat = payload.optDouble("lat");
                                        double lon = payload.optDouble("lon");
                                        String mapsUrl = payload.optString("mapsUrl");
                                        String from = payload.optString("from");

                                        found = true;
                                        if (callback != null) {
                                            callback.onSuccess(lat, lon, mapsUrl, from);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        if (!found && callback != null) {
                            callback.onError("Waiting for response from lost phone...");
                        }
                    } catch (Exception e) {
                        if (callback != null) callback.onError("Parsing error: " + e.getLocalizedMessage());
                    }
                } else {
                    if (callback != null) callback.onError("No internet response yet.");
                }
                response.close();
            }
        });
    }
}
