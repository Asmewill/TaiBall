package com.openclaw.wifitools;

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
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final long CONNECTION_TIMEOUT_MS = 20000L;

    private EditText etSsid;
    private EditText etPassword;
    private Button btnConnect;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnTogglePassword;

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;

    private BroadcastReceiver wifiScanReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    private String targetSsid;
    private String targetPassword;
    private boolean passwordVisible = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isConnecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etSsid = findViewById(R.id.etSsid);
        etPassword = findViewById(R.id.etPassword);
        btnConnect = findViewById(R.id.btnConnect);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());

        btnConnect.setOnClickListener(v -> {
            String ssid = etSsid.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (ssid.isEmpty()) {
                etSsid.setError(getString(R.string.error_empty_ssid));
                etSsid.requestFocus();
                return;
            }
            if (password.isEmpty()) {
                etPassword.setError(getString(R.string.error_empty_password));
                etPassword.requestFocus();
                return;
            }

            targetSsid = ssid;
            targetPassword = password;

            checkPermissionsAndConnect();
        });
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnTogglePassword.setText(R.string.btn_hide);
        } else {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnTogglePassword.setText(R.string.btn_show);
        }
        etPassword.setSelection(etPassword.getText().length());
    }

    private void checkPermissionsAndConnect() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (permissionsNeeded.isEmpty()) {
            startWifiConnect();
        } else {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startWifiConnect();
            } else {
                showManualConnectDialog(getString(R.string.error_permission_denied));
            }
        }
    }

    private void startWifiConnect() {
        if (!wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                startActivity(panelIntent);
                Toast.makeText(this, R.string.msg_enable_wifi_first, Toast.LENGTH_LONG).show();
                return;
            } else {
                wifiManager.setWifiEnabled(true);
            }
        }

        showProgress(true);
        setStatus(getString(R.string.status_scanning, targetSsid));

        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterReceiverSafe(wifiScanReceiver);
                wifiScanReceiver = null;
                handleScanResults();
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, filter);

        boolean scanStarted = wifiManager.startScan();
        if (!scanStarted) {
            unregisterReceiverSafe(wifiScanReceiver);
            wifiScanReceiver = null;
            // Throttled — try existing cached results
            handleScanResults();
        }
    }

    @SuppressWarnings("deprecation")
    private void handleScanResults() {
        List<ScanResult> scanResults = wifiManager.getScanResults();

        boolean ssidFound = false;
        for (ScanResult result : scanResults) {
            String scannedSsid;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                scannedSsid = result.getWifiSsid() != null ? result.getWifiSsid().toString() : "";
                // getWifiSsid() returns a quoted string on some devices; handle both forms
                if (scannedSsid.startsWith("\"") && scannedSsid.endsWith("\"")) {
                    scannedSsid = scannedSsid.substring(1, scannedSsid.length() - 1);
                }
            } else {
                scannedSsid = result.SSID;
            }
            if (targetSsid.equals(scannedSsid)) {
                ssidFound = true;
                break;
            }
        }

        if (!ssidFound && !scanResults.isEmpty()) {
            showProgress(false);
            showManualConnectDialog(getString(R.string.error_ssid_not_found, targetSsid));
            return;
        }

        setStatus(getString(R.string.status_connecting, targetSsid));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiModern();
        } else {
            connectWifiLegacy();
        }
    }

    private void connectWifiModern() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(targetSsid)
                .setWpa2Passphrase(targetPassword)
                .build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                handler.post(() -> {
                    isConnecting = false;
                    handler.removeCallbacksAndMessages(null);
                    connectivityManager.bindProcessToNetwork(network);
                    onWifiConnectedSuccessfully();
                });
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                handler.post(() -> {
                    isConnecting = false;
                    handler.removeCallbacksAndMessages(null);
                    showProgress(false);
                    showManualConnectDialog(
                            getString(R.string.error_connection_failed, targetSsid));
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
            }
        };

        isConnecting = true;
        connectivityManager.requestNetwork(request, networkCallback);

        handler.postDelayed(() -> {
            if (isConnecting) {
                isConnecting = false;
                unregisterNetworkCallbackSafe();
                showProgress(false);
                showManualConnectDialog(getString(R.string.error_connection_timeout, targetSsid));
            }
        }, CONNECTION_TIMEOUT_MS);
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    private void connectWifiLegacy() {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"" + targetSsid + "\"";
        wifiConfig.preSharedKey = "\"" + targetPassword + "\"";

        int netId = wifiManager.addNetwork(wifiConfig);
        if (netId == -1) {
            List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
            if (configs != null) {
                for (WifiConfiguration config : configs) {
                    if (("\"" + targetSsid + "\"").equals(config.SSID)) {
                        netId = config.networkId;
                        break;
                    }
                }
            }
        }

        if (netId == -1) {
            showProgress(false);
            showManualConnectDialog(getString(R.string.error_add_network_failed));
            return;
        }

        final int finalNetId = netId;
        wifiManager.disconnect();
        wifiManager.enableNetwork(finalNetId, true);
        wifiManager.reconnect();

        BroadcastReceiver connReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                android.net.NetworkInfo networkInfo =
                        intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null && networkInfo.isConnected()) {
                    android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    String connectedSsid = wifiInfo != null ? wifiInfo.getSSID() : "";
                    if (("\"" + targetSsid + "\"").equals(connectedSsid)) {
                        unregisterReceiverSafe(this);
                        handler.removeCallbacksAndMessages(null);
                        isConnecting = false;
                        onWifiConnectedSuccessfully();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(connReceiver, filter);
        isConnecting = true;

        handler.postDelayed(() -> {
            if (isConnecting) {
                isConnecting = false;
                unregisterReceiverSafe(connReceiver);
                showProgress(false);
                showManualConnectDialog(getString(R.string.error_connection_timeout, targetSsid));
            }
        }, CONNECTION_TIMEOUT_MS);
    }

    private void onWifiConnectedSuccessfully() {
        showProgress(false);
        setStatus(getString(R.string.status_connected));
        handler.postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
            startActivity(intent);
        }, 600);
    }

    private void showManualConnectDialog(String message) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_manual_connect, null);

        TextView tvMsg = dialogView.findViewById(R.id.tvDialogMessage);
        Button btnGoSettings = dialogView.findViewById(R.id.btnGoToSettings);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelDialog);

        tvMsg.setText(message);

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

    private void showProgress(boolean show) {
        runOnUiThread(() -> {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            btnConnect.setEnabled(!show);
        });
    }

    private void setStatus(String status) {
        runOnUiThread(() -> tvStatus.setText(status));
    }

    private void unregisterReceiverSafe(BroadcastReceiver receiver) {
        if (receiver == null) return;
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void unregisterNetworkCallbackSafe() {
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
            networkCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiverSafe(wifiScanReceiver);
        wifiScanReceiver = null;
        unregisterNetworkCallbackSafe();
        handler.removeCallbacksAndMessages(null);
    }
}
