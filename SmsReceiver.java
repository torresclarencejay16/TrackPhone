package com.example.trackphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TRACK_CODE = "#TRACK_NOW";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

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

            if (messageBody != null && messageBody.trim().equalsIgnoreCase(TRACK_CODE)) {
                abortBroadcast();
                fetchLocationAndReply(context, sender);
            }
        }
    }

    private void fetchLocationAndReply(Context context, String recipientPhone) {
        if (context == null || recipientPhone == null || recipientPhone.trim().isEmpty()) {
            return;
        }

        FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(context);

        try {
            fusedClient.getLastLocation().addOnSuccessListener(location -> {
                String replyMsg;
                if (location != null) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    replyMsg = "Lost Phone Location: https://maps.google.com/?q=" + lat + "," + lon;
                } else {
                    replyMsg = "Lost Phone: Location currently unavailable.";
                }

                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(recipientPhone, null, replyMsg, null, null);
            });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
}