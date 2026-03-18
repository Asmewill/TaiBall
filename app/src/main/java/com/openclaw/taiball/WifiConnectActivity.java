package com.openclaw.taiball;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class WifiConnectActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private static final String WEB_URL = "https://www.baidu.com/?tn=68018901_16_pg";
    private static final int CONNECTION_TIMEOUT_MS = 20000;

    private TextInputEditText etSsid;
    private TextInputEditText etPassword;
    private Button btnConnect;
    private LinearLayout layoutStatus;
    private TextView tvStatus;

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private BroadcastReceiver wifiScanReceiver;
    private BroadcastReceiver networkStateReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String targetSsid;
    private String targetPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_connect);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        etSsid = findViewById(R.id.et_ssid);
        etPassword = findViewById(R.id.et_password);
        btnConnect = findViewById(R.id.btn_connect);
        layoutStatus = findViewById(R.id.layout_status);
        tvStatus = findViewById(R.id.tv_status);

        btnConnect.setOnClickListener(v -> attemptConnect());
    }

    private void attemptConnect() {
        String ssid = etSsid.getText() != null ? etSsid.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (TextUtils.isEmpty(ssid)) {
            etSsid.setError(getString(R.string.error_ssid_empty));
            etSsid.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.error_password_empty));
            etPassword.requestFocus();
            return;
        }

        targetSsid = ssid;
        targetPassword = password;
        checkPermissionsAndConnect();
    }

    private void checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: NEARBY_WIFI_DEVICES permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.NEARBY_WIFI_DEVICES,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        REQUEST_PERMISSIONS);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6–12: ACCESS_FINE_LOCATION
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        REQUEST_PERMISSIONS);
                return;
            }
        }
        startConnectFlow();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            // Even if location was denied we can still try the specifier path on API 29+
            startConnectFlow();
        }
    }

    private void startConnectFlow() {
        // Enable WiFi if it is off
        if (!wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                //noinspection deprecation
                wifiManager.setWifiEnabled(true);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWithNetworkSpecifier();
        } else {
            scanAndConnectLegacy();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Android 9 and below: scan → match SSID → WifiConfiguration
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void scanAndConnectLegacy() {
        showStatus(true, getString(R.string.status_scanning));

        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                safeUnregister(wifiScanReceiver);
                wifiScanReceiver = null;

                List<ScanResult> results = wifiManager.getScanResults();
                if (isSsidAvailable(results)) {
                    connectWithConfiguration();
                } else {
                    showStatus(false, null);
                    showManualConnectDialog();
                }
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, filter);

        boolean started = wifiManager.startScan();
        if (!started) {
            // Use cached results immediately
            safeUnregister(wifiScanReceiver);
            wifiScanReceiver = null;
            List<ScanResult> results = wifiManager.getScanResults();
            if (isSsidAvailable(results)) {
                connectWithConfiguration();
            } else {
                showStatus(false, null);
                showManualConnectDialog();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isSsidAvailable(List<ScanResult> results) {
        for (ScanResult r : results) {
            if (targetSsid.equals(r.SSID)) return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private void connectWithConfiguration() {
        showStatus(true, getString(R.string.status_connecting));

        // Remove any previously added config for this SSID
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration c : configs) {
                if (("\"" + targetSsid + "\"").equals(c.SSID)) {
                    wifiManager.removeNetwork(c.networkId);
                }
            }
        }

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + targetSsid + "\"";
        config.preSharedKey = "\"" + targetPassword + "\"";
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.status = WifiConfiguration.Status.ENABLED;

        int networkId = wifiManager.addNetwork(config);
        if (networkId == -1) {
            showStatus(false, null);
            showManualConnectDialog();
            return;
        }

        wifiManager.disconnect();
        wifiManager.enableNetwork(networkId, true);
        wifiManager.reconnect();

        networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                android.net.NetworkInfo info =
                        intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info != null &&
                        info.getType() == ConnectivityManager.TYPE_WIFI &&
                        info.isConnected()) {
                    android.net.wifi.WifiInfo wi = wifiManager.getConnectionInfo();
                    if (wi != null && ("\"" + targetSsid + "\"").equals(wi.getSSID())) {
                        handler.removeCallbacksAndMessages(null);
                        safeUnregister(networkStateReceiver);
                        networkStateReceiver = null;
                        showStatus(false, null);
                        openWebView();
                    }
                }
            }
        };
        registerReceiver(networkStateReceiver,
                new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));

        // Timeout: check if connected, else show dialog
        handler.postDelayed(() -> {
            safeUnregister(networkStateReceiver);
            networkStateReceiver = null;
            showStatus(false, null);
            android.net.wifi.WifiInfo wi = wifiManager.getConnectionInfo();
            if (wi != null && ("\"" + targetSsid + "\"").equals(wi.getSSID())
                    && wi.getNetworkId() != -1) {
                openWebView();
            } else {
                showManualConnectDialog();
            }
        }, CONNECTION_TIMEOUT_MS);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Android 10+: WifiNetworkSpecifier via ConnectivityManager.requestNetwork
    // ──────────────────────────────────────────────────────────────────────────

    private void connectWithNetworkSpecifier() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        showStatus(true, getString(R.string.status_connecting));

        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(targetSsid)
                .setWpa2Passphrase(targetPassword)
                .build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                handler.post(() -> {
                    showStatus(false, null);
                    // Bind app traffic to this WiFi network so WebView can use it
                    connectivityManager.bindProcessToNetwork(network);
                    openWebView();
                });
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                handler.post(() -> {
                    showStatus(false, null);
                    showManualConnectDialog();
                });
            }
        };

        connectivityManager.requestNetwork(request, networkCallback,
                handler, CONNECTION_TIMEOUT_MS);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void openWebView() {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(WebViewActivity.EXTRA_URL, WEB_URL);
        startActivity(intent);
    }

    private void showManualConnectDialog() {
        runOnUiThread(() ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title)
                        .setMessage(R.string.dialog_message)
                        .setPositiveButton(R.string.dialog_connect_now, (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setCancelable(true)
                        .show()
        );
    }

    private void showStatus(boolean show, String message) {
        runOnUiThread(() -> {
            layoutStatus.setVisibility(show ? View.VISIBLE : View.GONE);
            if (message != null) tvStatus.setText(message);
            btnConnect.setEnabled(!show);
        });
    }

    private void safeUnregister(BroadcastReceiver receiver) {
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        safeUnregister(wifiScanReceiver);
        safeUnregister(networkStateReceiver);
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
                // Ignore
            }
            networkCallback = null;
        }
    }
}
