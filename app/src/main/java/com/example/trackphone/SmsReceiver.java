package com.example.trackphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    public static final String PREFS_NAME = "TrackPhonePrefs";
    public static final String KEY_TRACKING_ENABLED = "tracking_enabled";
    public static final String KEY_TRIGGER_KEYWORD = "trigger_keyword";
    public static final String KEY_TRUSTED_NUMBER = "trusted_number";
    public static final String DEFAULT_TRIGGER_KEYWORD = "#TRACK_NOW";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isTermsAccepted = prefs.getBoolean("terms_accepted", false);
        if (!isTermsAccepted) {
            Log.d(TAG, "Terms of service not accepted yet. Ignoring location request.");
            return;
        }

        boolean isTrackingEnabled = prefs.getBoolean(KEY_TRACKING_ENABLED, true);
        if (!isTrackingEnabled) {
            Log.d(TAG, "SMS tracking service is currently disabled.");
            return;
        }

        String savedKeyword = prefs.getString(KEY_TRIGGER_KEYWORD, DEFAULT_TRIGGER_KEYWORD);
        String triggerKeyword = (savedKeyword != null && !savedKeyword.trim().isEmpty())
                ? savedKeyword.trim()
                : DEFAULT_TRIGGER_KEYWORD;

        String trustedNumber = prefs.getString(KEY_TRUSTED_NUMBER, "");

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        for (SmsMessage sms : messages) {
            if (sms == null) {
                continue;
            }

            String sender = sms.getDisplayOriginatingAddress();
            String messageBody = sms.getMessageBody();

            if (messageBody != null && messageBody.trim().equalsIgnoreCase(triggerKeyword)) {
                if (!isSenderAuthorized(sender, trustedNumber)) {
                    Log.w(TAG, "Trigger keyword received from unauthorized sender: " + sender);
                    continue;
                }

                Log.i(TAG, "Trigger keyword received from authorized sender: " + sender);
                abortBroadcast();
                fetchLocationAndReply(context, sender);
            }
        }
    }

    private boolean isSenderAuthorized(String sender, String trustedNumber) {
        if (trustedNumber == null || trustedNumber.trim().isEmpty()) {
            return true; // No restriction if trusted number is blank
        }
        if (sender == null || sender.trim().isEmpty()) {
            return false;
        }

        String cleanSender = sender.replaceAll("[^0-9]", "");
        String cleanTrusted = trustedNumber.replaceAll("[^0-9]", "");

        if (cleanSender.isEmpty() || cleanTrusted.isEmpty()) {
            return false;
        }

        return cleanSender.endsWith(cleanTrusted) || cleanTrusted.endsWith(cleanSender);
    }

    private void fetchLocationAndReply(Context context, String recipientPhone) {
        if (context == null || recipientPhone == null || recipientPhone.trim().isEmpty()) {
            return;
        }

        LocationHelper.getBestLocation(context, new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationFound(double lat, double lon, boolean isLastKnown, String timeText) {
                String label = isLastKnown ? "TrackPhone (Last Known Location - " + timeText + "):" : "TrackPhone Lost Location:";
                String replyMsg = label + "\nhttps://maps.google.com/?q=" + lat + "," + lon;
                sendSmsMessage(context, recipientPhone, replyMsg);
                NotificationHelper.showLocationSharedNotification(context, recipientPhone);
            }

            @Override
            public void onLocationError(String errorMessage) {
                sendSmsMessage(context, recipientPhone, "TrackPhone: Location currently unavailable on lost device. Ensure GPS is ON.");
            }
        });
    }

    private void sendSmsMessage(Context context, String recipientPhone, String message) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager != null) {
                smsManager.sendTextMessage(recipientPhone, null, message, null, null);
                Log.i(TAG, "Location SMS reply sent to " + recipientPhone);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS message", e);
        }
    }
}
