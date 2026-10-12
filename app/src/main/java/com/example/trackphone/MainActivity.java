package com.example.trackphone;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 200;

    private TextView tvHeaderStatusBadge;
    private MaterialSwitch switchTrackingEnabled;

    // Feature 1: Remote Lost Phone Finder
    private TextInputEditText etLostPhoneNumber;
    private TextInputEditText etRemoteTriggerKeyword;
    private MaterialButton btnFetchLiveCloudLocation;
    private MaterialButton btnSendInternetRequest;
    private MaterialButton btnCheckInternetReply;
    private MaterialButton btnSendTrackSms;
    private TextView tvRemoteSmsStatus;
    private MaterialButton btnOpenRemoteMaps;
    private MaterialButton btnOpenSmsApp;

    // Feature 2: Settings (This Device Number, Trigger Keyword, Trusted Phone Number)
    private TextInputEditText etThisDeviceNumber;
    private TextInputEditText etTriggerKeyword;
    private TextInputEditText etTrustedNumber;
    private MaterialButton btnSaveSettings;

    // Permissions
    private TextView tvSmsStatus;
    private TextView tvLocationStatus;
    private MaterialButton btnRequestPermissions;

    // Live Location Test & Footer
    private MaterialButton btnTestLocation;
    private TextView tvLocationResult;
    private MaterialButton btnOpenMaps;
    private MaterialButton btnViewTos;

    private SharedPreferences prefs;
    private String currentMapsUrl = null;
    private String receivedRemoteMapsUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(SmsReceiver.PREFS_NAME, Context.MODE_PRIVATE);

        initViews();
        loadSettings();
        setupListeners();
        updatePermissionUI();

        checkTermsOfServiceAndStartService();
    }

    private void checkTermsOfServiceAndStartService() {
        boolean termsAccepted = prefs.getBoolean("terms_accepted", false);
        if (!termsAccepted) {
            showTermsOfServiceDialog(true);
        } else {
            startInternetServiceIfEnabled();
        }
    }

    private void showTermsOfServiceDialog(boolean forced) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.tos_title)
                .setMessage(R.string.tos_content)
                .setCancelable(!forced)
                .setPositiveButton(R.string.tos_agree, (dialog, which) -> {
                    prefs.edit().putBoolean("terms_accepted", true).apply();
                    Toast.makeText(MainActivity.this, "Terms of Service Accepted", Toast.LENGTH_SHORT).show();
                    startInternetServiceIfEnabled();
                    updatePermissionUI();
                });

        if (forced) {
            builder.setNegativeButton(R.string.tos_decline, (dialog, which) -> {
                Toast.makeText(MainActivity.this, "You must accept the Terms of Service to use TrackPhone.", Toast.LENGTH_LONG).show();
                finish();
            });
        } else {
            builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        }

        builder.show();
    }

    private void startInternetServiceIfEnabled() {
        if (prefs.getBoolean(SmsReceiver.KEY_TRACKING_ENABLED, true)) {
            Intent serviceIntent = new Intent(this, InternetBackgroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    private void initViews() {
        tvHeaderStatusBadge = findViewById(R.id.tvHeaderStatusBadge);
        switchTrackingEnabled = findViewById(R.id.switchTrackingEnabled);

        // Remote Lost Phone Finder
        etLostPhoneNumber = findViewById(R.id.etLostPhoneNumber);
        etRemoteTriggerKeyword = findViewById(R.id.etRemoteTriggerKeyword);
        btnFetchLiveCloudLocation = findViewById(R.id.btnFetchLiveCloudLocation);
        btnSendInternetRequest = findViewById(R.id.btnSendInternetRequest);
        btnCheckInternetReply = findViewById(R.id.btnCheckInternetReply);
        btnSendTrackSms = findViewById(R.id.btnSendTrackSms);
        tvRemoteSmsStatus = findViewById(R.id.tvRemoteSmsStatus);
        btnOpenRemoteMaps = findViewById(R.id.btnOpenRemoteMaps);
        btnOpenSmsApp = findViewById(R.id.btnOpenSmsApp);

        tvRemoteSmsStatus.setMovementMethod(LinkMovementMethod.getInstance());

        // Security & Identity Settings
        etThisDeviceNumber = findViewById(R.id.etThisDeviceNumber);
        etTriggerKeyword = findViewById(R.id.etTriggerKeyword);
        etTrustedNumber = findViewById(R.id.etTrustedNumber);
        btnSaveSettings = findViewById(R.id.btnSaveSettings);

        // Permissions
        tvSmsStatus = findViewById(R.id.tvSmsStatus);
        tvLocationStatus = findViewById(R.id.tvLocationStatus);
        btnRequestPermissions = findViewById(R.id.btnRequestPermissions);

        // Live Location Test & Footer
        btnTestLocation = findViewById(R.id.btnTestLocation);
        tvLocationResult = findViewById(R.id.tvLocationResult);
        btnOpenMaps = findViewById(R.id.btnOpenMaps);
        btnViewTos = findViewById(R.id.btnViewTos);
    }

    private void loadSettings() {
        boolean isTrackingEnabled = prefs.getBoolean(SmsReceiver.KEY_TRACKING_ENABLED, true);
        switchTrackingEnabled.setChecked(isTrackingEnabled);

        String thisDeviceNumber = prefs.getString("this_device_number", "");
        etThisDeviceNumber.setText(thisDeviceNumber);

        String triggerKeyword = prefs.getString(SmsReceiver.KEY_TRIGGER_KEYWORD, SmsReceiver.DEFAULT_TRIGGER_KEYWORD);
        etTriggerKeyword.setText(triggerKeyword);
        etRemoteTriggerKeyword.setText(triggerKeyword);

        String trustedNumber = prefs.getString(SmsReceiver.KEY_TRUSTED_NUMBER, "");
        etTrustedNumber.setText(trustedNumber);
    }

    private void setupListeners() {
        switchTrackingEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(SmsReceiver.KEY_TRACKING_ENABLED, isChecked).apply();
            updateHeaderBadge();
            if (isChecked) {
                startInternetServiceIfEnabled();
            } else {
                stopService(new Intent(this, InternetBackgroundService.class));
            }
            String msg = isChecked ? "Location Tracking Service enabled" : "Location Tracking Service disabled";
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        });

        // Fetch Live Cloud Location Feed
        btnFetchLiveCloudLocation.setOnClickListener(v -> fetchLiveCloudLocation());

        // Send via Internet (Free / No Cellular Load)
        btnSendInternetRequest.setOnClickListener(v -> sendInternetTrackRequest());

        // Check Internet Response
        btnCheckInternetReply.setOnClickListener(v -> checkInternetReply());

        // Send via Cellular SMS
        btnSendTrackSms.setOnClickListener(v -> sendRemoteTrackSms());

        btnOpenRemoteMaps.setOnClickListener(v -> {
            String url = receivedRemoteMapsUrl != null ? receivedRemoteMapsUrl : currentMapsUrl;
            openMapsUrl(url);
        });

        tvRemoteSmsStatus.setOnClickListener(v -> {
            if (receivedRemoteMapsUrl != null && !receivedRemoteMapsUrl.trim().isEmpty()) {
                openMapsUrl(receivedRemoteMapsUrl);
            }
        });

        btnOpenSmsApp.setOnClickListener(v -> {
            Intent defaultSms = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"));
            startActivity(defaultSms);
        });

        // Save Settings (This Device Number, Trigger Keyword, Trusted Phone Number)
        btnSaveSettings.setOnClickListener(v -> {
            String thisDeviceNum = etThisDeviceNumber.getText() != null ? etThisDeviceNumber.getText().toString().trim() : "";
            String keyword = etTriggerKeyword.getText() != null ? etTriggerKeyword.getText().toString().trim() : "";
            if (TextUtils.isEmpty(keyword)) {
                etTriggerKeyword.setError("Keyword cannot be empty");
                return;
            }

            String trustedNumber = etTrustedNumber.getText() != null ? etTrustedNumber.getText().toString().trim() : "";

            prefs.edit()
                    .putString("this_device_number", thisDeviceNum)
                    .putString(SmsReceiver.KEY_TRIGGER_KEYWORD, keyword)
                    .putString(SmsReceiver.KEY_TRUSTED_NUMBER, trustedNumber)
                    .apply();

            etRemoteTriggerKeyword.setText(keyword);
            Toast.makeText(MainActivity.this, R.string.trusted_number_saved_toast, Toast.LENGTH_SHORT).show();
        });

        btnRequestPermissions.setOnClickListener(v -> requestMissingPermissions());

        btnTestLocation.setOnClickListener(v -> testLocationFetch());

        btnOpenMaps.setOnClickListener(v -> {
            if (currentMapsUrl != null) {
                openMapsUrl(currentMapsUrl);
            }
        });

        btnViewTos.setOnClickListener(v -> showTermsOfServiceDialog(false));
    }

    private void fetchLiveCloudLocation() {
        String targetId = etLostPhoneNumber.getText() != null ? etLostPhoneNumber.getText().toString().trim() : "";
        if (TextUtils.isEmpty(targetId)) {
            etLostPhoneNumber.setError("Enter lost phone's number or ID");
            return;
        }

        tvRemoteSmsStatus.setVisibility(View.VISIBLE);
        btnOpenRemoteMaps.setVisibility(View.GONE);
        tvRemoteSmsStatus.setText("Fetching live cloud location feed...");

        InternetMessagingManager.fetchLiveLocationFeed(this, targetId, new InternetMessagingManager.LocationResponseCallback() {
            @Override
            public void onSuccess(double lat, double lon, String mapsUrl, String fromDevice) {
                runOnUiThread(() -> {
                    receivedRemoteMapsUrl = mapsUrl;
                    currentMapsUrl = mapsUrl;
                    String statusText = String.format(Locale.US,
                            "📍 LIVE CLOUD LOCATION RECEIVED!\nDevice: %s\nLatitude: %.6f\nLongitude: %.6f\n\nURL:\n%s",
                            fromDevice, lat, lon, mapsUrl);
                    tvRemoteSmsStatus.setText(statusText);
                    btnOpenRemoteMaps.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, "Live cloud location fetched!", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    tvRemoteSmsStatus.setText("Status: " + errorMessage + "\nEnsure lost phone has Internet and TrackPhone service running.");
                });
            }
        });
    }

    private void sendInternetTrackRequest() {
        String targetId = etLostPhoneNumber.getText() != null ? etLostPhoneNumber.getText().toString().trim() : "";
        if (TextUtils.isEmpty(targetId)) {
            etLostPhoneNumber.setError("Enter lost phone's number or ID");
            return;
        }

        String triggerCode = etRemoteTriggerKeyword.getText() != null ? etRemoteTriggerKeyword.getText().toString().trim() : "";
        if (TextUtils.isEmpty(triggerCode)) {
            etRemoteTriggerKeyword.setError("Enter trigger keyword");
            return;
        }

        tvRemoteSmsStatus.setVisibility(View.VISIBLE);
        btnOpenRemoteMaps.setVisibility(View.GONE);
        tvRemoteSmsStatus.setText("Sending request via Internet (Free)...");

        InternetMessagingManager.sendInternetLocationRequest(this, targetId, triggerCode, new InternetMessagingManager.SendRequestCallback() {
            @Override
            public void onRequestSent() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, R.string.internet_sent_toast, Toast.LENGTH_LONG).show();
                    tvRemoteSmsStatus.setText(String.format(Locale.US,
                            "Internet request sent to %s!\nChecking for response from lost phone...", targetId));
                    checkInternetReply();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    tvRemoteSmsStatus.setText("Error sending via internet: " + errorMessage + "\nTry tapping 'Send via Cellular SMS' instead.");
                });
            }
        });
    }

    private void checkInternetReply() {
        tvRemoteSmsStatus.setVisibility(View.VISIBLE);
        tvRemoteSmsStatus.setText("Checking internet for location reply...");

        InternetMessagingManager.listenForInternetResponse(this, new InternetMessagingManager.LocationResponseCallback() {
            @Override
            public void onSuccess(double lat, double lon, String mapsUrl, String fromDevice) {
                runOnUiThread(() -> {
                    receivedRemoteMapsUrl = mapsUrl;
                    currentMapsUrl = mapsUrl;
                    String statusText = String.format(Locale.US,
                            "📍 LOST PHONE LOCATION RECEIVED!\nFrom: %s\nLatitude: %.6f\nLongitude: %.6f\n\nURL:\n%s",
                            fromDevice, lat, lon, mapsUrl);
                    tvRemoteSmsStatus.setText(statusText);
                    btnOpenRemoteMaps.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, "Location received via Internet!", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    tvRemoteSmsStatus.setText("Status: " + errorMessage + "\nEnsure lost phone is connected to Internet and has TrackPhone enabled.");
                });
            }
        });
    }

    private void sendRemoteTrackSms() {
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            Toast.makeText(this, "SEND_SMS permission is required to send cellular track requests.", Toast.LENGTH_SHORT).show();
            requestMissingPermissions();
            return;
        }

        String phoneNum = etLostPhoneNumber.getText() != null ? etLostPhoneNumber.getText().toString().trim() : "";
        if (TextUtils.isEmpty(phoneNum)) {
            etLostPhoneNumber.setError("Enter the lost phone's number");
            return;
        }

        String triggerCode = etRemoteTriggerKeyword.getText() != null ? etRemoteTriggerKeyword.getText().toString().trim() : "";
        if (TextUtils.isEmpty(triggerCode)) {
            etRemoteTriggerKeyword.setError("Enter trigger keyword");
            return;
        }

        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager != null) {
                smsManager.sendTextMessage(phoneNum, null, triggerCode, null, null);
                Toast.makeText(this, R.string.sms_sent_toast, Toast.LENGTH_LONG).show();

                tvRemoteSmsStatus.setVisibility(View.VISIBLE);
                tvRemoteSmsStatus.setText(String.format(Locale.US,
                        "Track request SMS (\"%s\") sent to %s!\nWhen the lost phone receives it, it will reply back to your Messages app with a Google Maps link.",
                        triggerCode, phoneNum));
                btnOpenSmsApp.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SMS: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isSmsPermissionGranted() {
        return hasPermission(Manifest.permission.RECEIVE_SMS) && hasPermission(Manifest.permission.SEND_SMS);
    }

    private boolean isLocationPermissionGranted() {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void updatePermissionUI() {
        boolean smsGranted = isSmsPermissionGranted();
        boolean locationGranted = isLocationPermissionGranted();

        // Update SMS Status Badge
        if (smsGranted) {
            tvSmsStatus.setText(R.string.perm_granted);
            tvSmsStatus.setTextColor(ContextCompat.getColor(this, R.color.status_ready_text));
            tvSmsStatus.setBackgroundResource(R.drawable.bg_status_badge);
        } else {
            tvSmsStatus.setText(R.string.perm_missing);
            tvSmsStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning_text));
            tvSmsStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_warning_bg));
        }

        // Update Location Status Badge
        if (locationGranted) {
            tvLocationStatus.setText(R.string.perm_granted);
            tvLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.status_ready_text));
            tvLocationStatus.setBackgroundResource(R.drawable.bg_status_badge);
        } else {
            tvLocationStatus.setText(R.string.perm_missing);
            tvLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning_text));
            tvLocationStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_warning_bg));
        }

        // Update Button
        if (smsGranted && locationGranted) {
            btnRequestPermissions.setText("All Permissions Granted");
            btnRequestPermissions.setEnabled(false);
            btnRequestPermissions.setIconResource(R.drawable.ic_check);
        } else {
            btnRequestPermissions.setText(R.string.grant_permissions_btn);
            btnRequestPermissions.setEnabled(true);
            btnRequestPermissions.setIconResource(R.drawable.ic_shield);
        }

        updateHeaderBadge();
    }

    private void updateHeaderBadge() {
        boolean trackingEnabled = switchTrackingEnabled.isChecked();
        boolean allPermissionsGranted = isSmsPermissionGranted() && isLocationPermissionGranted();
        boolean termsAccepted = prefs.getBoolean("terms_accepted", false);

        if (!termsAccepted) {
            tvHeaderStatusBadge.setText("Terms Accept Required");
            tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning_text));
            tvHeaderStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_warning_bg));
        } else if (!allPermissionsGranted) {
            tvHeaderStatusBadge.setText(R.string.status_permissions_needed);
            tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning_text));
            tvHeaderStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_warning_bg));
        } else if (!trackingEnabled) {
            tvHeaderStatusBadge.setText(R.string.status_disabled);
            tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning_text));
            tvHeaderStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_warning_bg));
        } else {
            tvHeaderStatusBadge.setText(R.string.status_ready);
            tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_ready_text));
            tvHeaderStatusBadge.setBackgroundResource(R.drawable.bg_status_badge);
        }
    }

    private void requestMissingPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (!hasPermission(Manifest.permission.RECEIVE_SMS)) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS);
        }
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS);
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            Toast.makeText(this, "All required permissions are already granted!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updatePermissionUI();
        }
    }

    private void testLocationFetch() {
        if (!isLocationPermissionGranted()) {
            Toast.makeText(this, "Please grant Location Permission first", Toast.LENGTH_SHORT).show();
            requestMissingPermissions();
            return;
        }

        tvLocationResult.setText(R.string.fetching_location);
        btnOpenMaps.setVisibility(View.GONE);

        LocationHelper.getBestLocation(this, new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationFound(double lat, double lon, boolean isLastKnown, String timeText) {
                currentMapsUrl = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);
                String label = isLastKnown ? " (Last Known: " + timeText + ")" : " (Fresh GPS Fix)";
                String text = String.format(Locale.US,
                        "Latitude: %.6f\nLongitude: %.6f%s\n\nGoogle Maps URL:\n%s",
                        lat, lon, label, currentMapsUrl);
                tvLocationResult.setText(text);
                btnOpenMaps.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLocationError(String errorMessage) {
                tvLocationResult.setText("Error: " + errorMessage);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionUI();
    }

    private void openMapsUrl(String mapsUrl) {
        if (mapsUrl != null && !mapsUrl.trim().isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl.trim()));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Error opening Maps: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
