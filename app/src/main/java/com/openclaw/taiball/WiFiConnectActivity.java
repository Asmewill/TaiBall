package com.openclaw.taiball;

import android.Manifest;
import android.app.Dialog;
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
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

public class WiFiConnectActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String TARGET_URL = "https://www.baidu.com/?tn=68018901_16_pg";
    private static final int CONNECTION_TIMEOUT_MS = 15000;

    private EditText etSsid;
    private EditText etPassword;
    private Button btnConnect;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private ImageView ivTogglePassword;

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver scanReceiver;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String targetSsid;
    private String targetPassword;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_connect);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        etSsid = findViewById(R.id.et_ssid);
        etPassword = findViewById(R.id.et_password);
        btnConnect = findViewById(R.id.btn_connect);
        tvStatus = findViewById(R.id.tv_status);
        progressBar = findViewById(R.id.progress_bar);
        ivTogglePassword = findViewById(R.id.iv_toggle_password);

        ivTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        btnConnect.setOnClickListener(v -> startWifiConnection());
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivTogglePassword.setImageResource(R.drawable.ic_eye_off);
        } else {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivTogglePassword.setImageResource(R.drawable.ic_eye);
        }
        etPassword.setSelection(etPassword.getText().length());
    }

    private void startWifiConnection() {
        targetSsid = etSsid.getText().toString().trim();
        targetPassword = etPassword.getText().toString();

        if (targetSsid.isEmpty()) {
            Toast.makeText(this, "请输入WiFi名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                wifiManager.setWifiEnabled(true);
            } else {
                Toast.makeText(this, "请先开启WiFi", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                return;
            }
        }

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
                        PERMISSION_REQUEST_CODE);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6–12: location permission required for WiFi scan
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        PERMISSION_REQUEST_CODE);
                return;
            }
        }
        connectToWifi();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToWifi();
            } else {
                Toast.makeText(this, "需要相关权限才能搜索WiFi，请在设置中授予权限",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void connectToWifi() {
        setUiConnecting(true, "正在搜索WiFi...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiAndroid10Plus();
        } else {
            scanAndConnectLegacy();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Android 10+ (API 29+) connection via WifiNetworkSpecifier
    // ──────────────────────────────────────────────────────────────────────────
    private void connectWifiAndroid10Plus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        releaseNetworkCallback();

        WifiNetworkSpecifier.Builder specBuilder = new WifiNetworkSpecifier.Builder()
                .setSsid(targetSsid);
        if (!targetPassword.isEmpty()) {
            specBuilder.setWpa2Passphrase(targetPassword);
        }
        WifiNetworkSpecifier specifier = specBuilder.build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // Bind process to this network so WebView traffic uses it
                connectivityManager.bindProcessToNetwork(network);
                runOnUiThread(() -> {
                    setUiConnecting(false, "连接成功！");
                    openWebView();
                });
            }

            @Override
            public void onUnavailable() {
                runOnUiThread(() -> {
                    setUiConnecting(false, "连接失败");
                    showManualConnectDialog();
                });
            }
        };

        connectivityManager.requestNetwork(request, networkCallback, handler, CONNECTION_TIMEOUT_MS);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Android 9 and below (deprecated APIs, still functional)
    // ──────────────────────────────────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private void scanAndConnectLegacy() {
        unregisterScanReceiver();

        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterScanReceiver();
                handleScanResults();
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(scanReceiver, filter);

        boolean scanStarted = wifiManager.startScan();
        if (!scanStarted) {
            // Scan throttled; fall back to cached results
            unregisterScanReceiver();
            handleScanResults();
        }
    }

    @SuppressWarnings("deprecation")
    private void handleScanResults() {
        List<ScanResult> results = wifiManager.getScanResults();
        boolean found = false;
        for (ScanResult r : results) {
            if (targetSsid.equals(r.SSID)) {
                found = true;
                break;
            }
        }

        if (found) {
            setUiConnecting(true, "正在连接...");
            connectLegacy();
        } else {
            setUiConnecting(false, "未找到该WiFi网络");
            showManualConnectDialog();
        }
    }

    @SuppressWarnings("deprecation")
    private void connectLegacy() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + targetSsid + "\"";

        if (targetPassword.isEmpty()) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            config.preSharedKey = "\"" + targetPassword + "\"";
        }

        // Remove existing network with same SSID to avoid duplicates
        List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
        if (existingConfigs != null) {
            for (WifiConfiguration existing : existingConfigs) {
                if (("\"" + targetSsid + "\"").equals(existing.SSID)) {
                    wifiManager.removeNetwork(existing.networkId);
                    break;
                }
            }
        }

        int netId = wifiManager.addNetwork(config);
        if (netId == -1) {
            setUiConnecting(false, "连接失败（无法添加网络）");
            showManualConnectDialog();
            return;
        }

        wifiManager.disconnect();
        boolean enabled = wifiManager.enableNetwork(netId, true);
        if (!enabled) {
            setUiConnecting(false, "连接失败");
            showManualConnectDialog();
            return;
        }
        wifiManager.reconnect();

        // Poll for connection result
        final int[] attempts = {0};
        final int maxAttempts = CONNECTION_TIMEOUT_MS / 1000;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                attempts[0]++;
                WifiInfo info = wifiManager.getConnectionInfo();
                String connectedSsid = info != null ? info.getSSID() : null;
                String expectedSsid = "\"" + targetSsid + "\"";

                if (expectedSsid.equals(connectedSsid)) {
                    setUiConnecting(false, "连接成功！");
                    openWebView();
                } else if (attempts[0] < maxAttempts) {
                    handler.postDelayed(this, 1000);
                } else {
                    setUiConnecting(false, "连接失败（超时）");
                    showManualConnectDialog();
                }
            }
        }, 1000);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ──────────────────────────────────────────────────────────────────────────
    private void setUiConnecting(boolean connecting, String status) {
        btnConnect.setEnabled(!connecting);
        progressBar.setVisibility(connecting ? View.VISIBLE : View.GONE);
        tvStatus.setText(status);
    }

    private void openWebView() {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra("url", TARGET_URL);
        startActivity(intent);
    }

    private void showManualConnectDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_manual_connect);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvMessage = dialog.findViewById(R.id.tv_dialog_message);
        tvMessage.setText("未找到 WiFi \"" + targetSsid + "\" 或密码不正确，\n请手动连接设备WiFi后重试。");

        Button btnManual = dialog.findViewById(R.id.btn_manual_connect);
        btnManual.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        });

        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────────────────
    private void releaseNetworkCallback() {
        if (networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
            networkCallback = null;
        }
    }

    private void unregisterScanReceiver() {
        if (scanReceiver != null) {
            try {
                unregisterReceiver(scanReceiver);
            } catch (Exception ignored) {
            }
            scanReceiver = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        releaseNetworkCallback();
        unregisterScanReceiver();
    }
}
