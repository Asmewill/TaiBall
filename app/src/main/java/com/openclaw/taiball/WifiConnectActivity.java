package com.openclaw.taiball;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class WifiConnectActivity extends AppCompatActivity {

    private static final String TAG = "WifiConnectActivity";
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final String TARGET_URL = "https://www.baidu.com/?tn=68018901_16_pg";

    private EditText etSsid;
    private EditText etPassword;
    private Button btnConnect;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private Handler mainHandler;
    private Runnable timeoutRunnable;

    // Stored as instance fields so they can always be cleaned up in onDestroy
    private BroadcastReceiver wifiStateReceiver;
    private BroadcastReceiver scanReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean isConnecting = false;
    private boolean connectionHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_connect);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());

        etSsid = findViewById(R.id.etSsid);
        etPassword = findViewById(R.id.etPassword);
        btnConnect = findViewById(R.id.btnConnect);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);

        CheckBox cbShowPassword = findViewById(R.id.cbShowPassword);
        cbShowPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                etPassword.setTransformationMethod(null);
            } else {
                etPassword.setTransformationMethod(new PasswordTransformationMethod());
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        btnConnect.setOnClickListener(v -> {
            String ssid = etSsid.getText().toString().trim();
            String password = etPassword.getText().toString();
            if (TextUtils.isEmpty(ssid)) {
                etSsid.setError(getString(R.string.error_empty_ssid));
                return;
            }
            if (TextUtils.isEmpty(password)) {
                etPassword.setError(getString(R.string.error_empty_password));
                return;
            }
            checkPermissionsAndConnect(ssid, password);
        });
    }

    private void checkPermissionsAndConnect(String ssid, String password) {
        List<String> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        List<String> missingPermissions = new ArrayList<>();
        for (String perm : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(perm);
            }
        }

        if (!missingPermissions.isEmpty()) {
            // Store pending connection info in view tags for use after permission result
            etSsid.setTag(ssid);
            etPassword.setTag(password);
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]),
                    REQUEST_PERMISSIONS);
        } else {
            startWifiConnection(ssid, password);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            String ssid = etSsid.getTag() != null
                    ? etSsid.getTag().toString()
                    : etSsid.getText().toString().trim();
            String password = etPassword.getTag() != null
                    ? etPassword.getTag().toString()
                    : etPassword.getText().toString();
            // Proceed regardless; scan may return cached results on some devices
            startWifiConnection(ssid, password);
        }
    }

    private void startWifiConnection(String ssid, String password) {
        if (isConnecting) return;
        isConnecting = true;
        connectionHandled = false;

        showLoading(true);
        setStatus(getString(R.string.status_connecting, ssid));

        if (!wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                //noinspection deprecation
                wifiManager.setWifiEnabled(true);
            } else {
                // Android 10+ disallows programmatic WiFi toggle; ask user to enable it
                isConnecting = false;
                showLoading(false);
                showManualConnectDialog(ssid);
                return;
            }
        }

        timeoutRunnable = () -> {
            if (!connectionHandled) {
                Log.d(TAG, "Connection timed out for SSID: " + ssid);
                onConnectionFailed(ssid);
            }
        };
        mainHandler.postDelayed(timeoutRunnable, CONNECT_TIMEOUT_MS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectModern(ssid, password);
        } else {
            connectLegacy(ssid, password);
        }
    }

    // ── Android 10+ path ────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private void connectModern(String ssid, String password) {
        setStatus(getString(R.string.status_scanning));

        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterScanReceiver();
                handleScanResultsModern(ssid, password);
            }
        };
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        boolean scanStarted = wifiManager.startScan();
        if (!scanStarted) {
            unregisterScanReceiver();
            handleScanResultsModern(ssid, password);
        }
    }

    @SuppressLint("MissingPermission")
    private void handleScanResultsModern(String ssid, String password) {
        if (!isSsidInScanResults(ssid)) {
            Log.d(TAG, "SSID not found in scan results: " + ssid);
            cancelTimeout();
            onConnectionFailed(ssid);
            return;
        }

        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .setNetworkSpecifier(specifier)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.d(TAG, "Network available");
                if (!connectionHandled) {
                    connectionHandled = true;
                    cancelTimeout();
                    mainHandler.post(WifiConnectActivity.this::onConnectionSuccess);
                }
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                Log.d(TAG, "Network unavailable");
                if (!connectionHandled) {
                    cancelTimeout();
                    onConnectionFailed(ssid);
                }
            }
        };

        connectivityManager.requestNetwork(request, networkCallback);
        setStatus(getString(R.string.status_connecting, ssid));
    }

    // ── Android 9 and below path ─────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private void connectLegacy(String ssid, String password) {
        setStatus(getString(R.string.status_scanning));

        scanReceiver = new BroadcastReceiver() {
            @Override
            @SuppressLint("MissingPermission")
            public void onReceive(Context context, Intent intent) {
                unregisterScanReceiver();
                if (!isSsidInScanResults(ssid)) {
                    cancelTimeout();
                    onConnectionFailed(ssid);
                    return;
                }
                doLegacyConnect(ssid, password);
            }
        };
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        //noinspection deprecation
        boolean started = wifiManager.startScan();
        if (!started) {
            unregisterScanReceiver();
            doLegacyConnect(ssid, password);
        }
    }

    @SuppressLint({"MissingPermission", "deprecation"})
    private void doLegacyConnect(String ssid, String password) {
        setStatus(getString(R.string.status_connecting, ssid));

        List<WifiConfiguration> existing = wifiManager.getConfiguredNetworks();
        if (existing != null) {
            for (WifiConfiguration cfg : existing) {
                if (cfg.SSID != null && cfg.SSID.equals("\"" + ssid + "\"")) {
                    wifiManager.removeNetwork(cfg.networkId);
                }
            }
        }

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        config.preSharedKey = "\"" + password + "\"";
        config.status = WifiConfiguration.Status.ENABLED;
        config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

        int networkId = wifiManager.addNetwork(config);
        if (networkId == -1) {
            cancelTimeout();
            onConnectionFailed(ssid);
            return;
        }

        wifiManager.disconnect();
        wifiManager.enableNetwork(networkId, true);
        wifiManager.reconnect();

        wifiStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                    //noinspection deprecation
                    android.net.NetworkInfo networkInfo =
                            intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (networkInfo != null && networkInfo.isConnected()) {
                        //noinspection deprecation
                        String connectedSsid = wifiManager.getConnectionInfo().getSSID();
                        if (("\"" + ssid + "\"").equals(connectedSsid) && !connectionHandled) {
                            connectionHandled = true;
                            cancelTimeout();
                            unregisterWifiStateReceiver();
                            mainHandler.post(WifiConnectActivity.this::onConnectionSuccess);
                        }
                    }
                } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                    int error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
                    if (error == WifiManager.ERROR_AUTHENTICATING && !connectionHandled) {
                        cancelTimeout();
                        unregisterWifiStateReceiver();
                        onConnectionFailed(ssid);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(wifiStateReceiver, filter);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private boolean isSsidInScanResults(String ssid) {
        List<ScanResult> results = wifiManager.getScanResults();
        if (results == null) return false;
        for (ScanResult r : results) {
            if (ssid.equals(r.SSID)) return true;
        }
        return false;
    }

    private void onConnectionSuccess() {
        isConnecting = false;
        showLoading(false);
        setStatus(getString(R.string.status_connected));
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(WebViewActivity.EXTRA_URL, TARGET_URL);
        startActivity(intent);
    }

    private void onConnectionFailed(String ssid) {
        mainHandler.post(() -> {
            isConnecting = false;
            showLoading(false);
            setStatus(getString(R.string.status_failed));
            cleanupCallbacks();
            showManualConnectDialog(ssid);
        });
    }

    private void showManualConnectDialog(String ssid) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_manual_connect, null);
        TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        Button btnGoSettings = dialogView.findViewById(R.id.btnGoSettings);
        Button btnCancel = dialogView.findViewById(R.id.btnDialogCancel);

        tvMessage.setText(getString(R.string.dialog_manual_message, ssid));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnGoSettings.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void unregisterScanReceiver() {
        if (scanReceiver != null) {
            try { unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
            scanReceiver = null;
        }
    }

    private void unregisterWifiStateReceiver() {
        if (wifiStateReceiver != null) {
            try { unregisterReceiver(wifiStateReceiver); } catch (Exception ignored) {}
            wifiStateReceiver = null;
        }
    }

    private void cleanupCallbacks() {
        cancelTimeout();
        unregisterScanReceiver();
        unregisterWifiStateReceiver();
        if (networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); }
            catch (Exception ignored) {}
            networkCallback = null;
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnConnect.setEnabled(!show);
    }

    private void setStatus(String message) {
        runOnUiThread(() -> tvStatus.setText(message));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupCallbacks();
    }
}
