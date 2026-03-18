package com.openclaw.taiball;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

public class WifiActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private static final int CONNECT_TIMEOUT_MS = 15000;

    private EditText etSsid;
    private EditText etPassword;
    private Button btnConnect;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private ImageView ivTogglePassword;

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean passwordVisible = false;
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private boolean isConnecting = false;

    private BroadcastReceiver wifiScanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        etSsid = findViewById(R.id.etSsid);
        etPassword = findViewById(R.id.etPassword);
        btnConnect = findViewById(R.id.btnConnect);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);

        ivTogglePassword.setOnClickListener(v -> togglePasswordVisibility());

        btnConnect.setOnClickListener(v -> {
            hideKeyboard();
            String ssid = etSsid.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (TextUtils.isEmpty(ssid)) {
                etSsid.setError(getString(R.string.error_ssid_empty));
                return;
            }
            if (TextUtils.isEmpty(password)) {
                etPassword.setError(getString(R.string.error_password_empty));
                return;
            }
            checkPermissionsAndConnect(ssid, password);
        });
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            etPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivTogglePassword.setImageResource(android.R.drawable.ic_menu_view);
        } else {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivTogglePassword.setImageResource(android.R.drawable.ic_secure);
        }
        etPassword.setSelection(etPassword.getText().length());
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void checkPermissionsAndConnect(String ssid, String password) {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
            // store for retry after permission granted
            etSsid.setTag(ssid);
            etPassword.setTag(password);
        } else {
            startWifiConnection(ssid, password);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                String ssid = (String) etSsid.getTag();
                String password = (String) etPassword.getTag();
                if (ssid != null && password != null) {
                    startWifiConnection(ssid, password);
                }
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startWifiConnection(String ssid, String password) {
        if (isConnecting) return;
        isConnecting = true;
        setConnectingState(true);
        tvStatus.setText(R.string.status_searching);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiAndroid10Plus(ssid, password);
        } else {
            connectWifiLegacy(ssid, password);
        }
    }

    // Android 10+ (API 29+): use WifiNetworkSpecifier
    private void connectWifiAndroid10Plus(String ssid, String password) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                cancelTimeout();
                runOnUiThread(() -> {
                    isConnecting = false;
                    setConnectingState(false);
                    tvStatus.setText(R.string.status_connected);
                    openWebView();
                });
            }

            @Override
            public void onUnavailable() {
                cancelTimeout();
                runOnUiThread(() -> {
                    isConnecting = false;
                    setConnectingState(false);
                    tvStatus.setText(R.string.status_failed);
                    showManualConnectDialog();
                });
            }
        };

        connectivityManager.requestNetwork(request, networkCallback);
        startConnectTimeout();
    }

    // Legacy (API < 29): use WifiConfiguration
    @SuppressWarnings("deprecation")
    private void connectWifiLegacy(String ssid, String password) {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        // scan first to find the network
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                unregisterReceiver(wifiScanReceiver);
                wifiScanReceiver = null;

                if (ActivityCompat.checkSelfPermission(WifiActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> {
                        isConnecting = false;
                        setConnectingState(false);
                        showManualConnectDialog();
                    });
                    return;
                }

                List<ScanResult> results = wifiManager.getScanResults();
                boolean found = false;
                for (ScanResult r : results) {
                    if (ssid.equals(r.SSID)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    cancelTimeout();
                    runOnUiThread(() -> {
                        isConnecting = false;
                        setConnectingState(false);
                        tvStatus.setText(R.string.status_not_found);
                        showManualConnectDialog();
                    });
                    return;
                }

                // found – try to connect
                WifiConfiguration config = new WifiConfiguration();
                config.SSID = "\"" + ssid + "\"";
                config.preSharedKey = "\"" + password + "\"";
                config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);

                // remove old config for this SSID if any
                List<WifiConfiguration> existing = wifiManager.getConfiguredNetworks();
                if (existing != null) {
                    for (WifiConfiguration w : existing) {
                        if (("\"" + ssid + "\"").equals(w.SSID)) {
                            wifiManager.removeNetwork(w.networkId);
                        }
                    }
                }

                int netId = wifiManager.addNetwork(config);
                if (netId == -1) {
                    cancelTimeout();
                    runOnUiThread(() -> {
                        isConnecting = false;
                        setConnectingState(false);
                        tvStatus.setText(R.string.status_failed);
                        showManualConnectDialog();
                    });
                    return;
                }

                wifiManager.disconnect();
                wifiManager.enableNetwork(netId, true);
                wifiManager.reconnect();

                // poll for connection state
                pollForConnection(ssid);
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, filter);
        wifiManager.startScan();
        startConnectTimeout();
    }

    private void pollForConnection(String targetSsid) {
        Handler handler = new Handler(Looper.getMainLooper());
        long startTime = System.currentTimeMillis();
        Runnable pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isConnecting) return;
                android.net.wifi.WifiInfo info = wifiManager.getConnectionInfo();
                String connectedSsid = info.getSSID();
                if (connectedSsid != null && connectedSsid.equals("\"" + targetSsid + "\"")) {
                    cancelTimeout();
                    isConnecting = false;
                    setConnectingState(false);
                    tvStatus.setText(R.string.status_connected);
                    openWebView();
                } else if (System.currentTimeMillis() - startTime < CONNECT_TIMEOUT_MS) {
                    handler.postDelayed(this, 1000);
                } else {
                    cancelTimeout();
                    isConnecting = false;
                    setConnectingState(false);
                    tvStatus.setText(R.string.status_failed);
                    showManualConnectDialog();
                }
            }
        };
        handler.postDelayed(pollRunnable, 1500);
    }

    private void startConnectTimeout() {
        timeoutRunnable = () -> {
            if (isConnecting) {
                if (networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        connectivityManager.unregisterNetworkCallback(networkCallback);
                    } catch (Exception ignored) {}
                    networkCallback = null;
                }
                isConnecting = false;
                setConnectingState(false);
                tvStatus.setText(R.string.status_failed);
                showManualConnectDialog();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, CONNECT_TIMEOUT_MS);
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void setConnectingState(boolean connecting) {
        runOnUiThread(() -> {
            progressBar.setVisibility(connecting ? View.VISIBLE : View.GONE);
            btnConnect.setEnabled(!connecting);
            btnConnect.setText(connecting ? R.string.btn_connecting : R.string.btn_connect);
        });
    }

    private void openWebView() {
        runOnUiThread(() -> {
            Intent intent = new Intent(WifiActivity.this, WebViewActivity.class);
            startActivity(intent);
        });
    }

    private void showManualConnectDialog() {
        runOnUiThread(() -> {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_wifi, null);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(true)
                    .create();

            Button btnGoToSettings = dialogView.findViewById(R.id.btnGoToSettings);
            Button btnCancel = dialogView.findViewById(R.id.btnCancelDialog);

            btnGoToSettings.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                startActivity(intent);
            });

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            dialog.show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimeout();
        if (networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        if (wifiScanReceiver != null) {
            try {
                unregisterReceiver(wifiScanReceiver);
            } catch (Exception ignored) {}
        }
    }
}
