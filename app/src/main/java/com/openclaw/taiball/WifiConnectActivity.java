package com.openclaw.taiball;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

public class WifiConnectActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int POLL_INTERVAL_MS = 1500;
    private static final int MAX_POLL_ATTEMPTS = 15;

    private EditText etWifiName;
    private EditText etWifiPassword;
    private Button btnConnect;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvConnecting;
    private ImageButton btnTogglePassword;

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Handler handler;
    private boolean passwordVisible = false;
    private boolean isConnecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_connect);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        handler = new Handler(Looper.getMainLooper());

        initViews();
        requestRequiredPermissions();
    }

    private void initViews() {
        etWifiName = findViewById(R.id.etWifiName);
        etWifiPassword = findViewById(R.id.etWifiPassword);
        btnConnect = findViewById(R.id.btnConnect);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvConnecting = findViewById(R.id.tvConnecting);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);

        btnConnect.setOnClickListener(v -> startWifiConnection());

        btnTogglePassword.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            if (passwordVisible) {
                etWifiPassword.setTransformationMethod(null);
                btnTogglePassword.setImageResource(R.drawable.ic_visibility);
            } else {
                etWifiPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            }
            etWifiPassword.setSelection(etWifiPassword.getText().length());
        });
    }

    private void requestRequiredPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: NEARBY_WIFI_DEVICES replaces location for Wi-Fi scanning
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE);
        }
    }

    private void startWifiConnection() {
        if (isConnecting) return;

        String ssid = etWifiName.getText().toString().trim();
        String password = etWifiPassword.getText().toString();

        if (ssid.isEmpty()) {
            etWifiName.setError("请输入WiFi名称");
            etWifiName.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etWifiPassword.setError("请输入WiFi密码");
            etWifiPassword.requestFocus();
            return;
        }

        // Check WiFi enabled state
        if (!wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                wifiManager.setWifiEnabled(true);
            } else {
                Toast.makeText(this, "请先在系统设置中开启WiFi", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.Panel.ACTION_WIFI));
                return;
            }
        }

        setConnectingState(true);
        showStatus("", false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiAndroid10Plus(ssid, password);
        } else {
            connectWifiLegacy(ssid, password);
        }
    }

    /** Android 10+ (API 29+): use WifiNetworkSpecifier + NetworkRequest */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.Q)
    private void connectWifiAndroid10Plus(String ssid, String password) {
        cleanupNetworkCallback();

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
                // Bind process to this network so WebView uses it
                connectivityManager.bindProcessToNetwork(network);
                handler.post(() -> {
                    setConnectingState(false);
                    openWebView();
                });
            }

            @Override
            public void onUnavailable() {
                handler.post(() -> {
                    setConnectingState(false);
                    showManualConnectDialog();
                });
            }
        };

        connectivityManager.requestNetwork(request, networkCallback);

        // Timeout fallback
        handler.postDelayed(() -> {
            if (isConnecting) {
                cleanupNetworkCallback();
                setConnectingState(false);
                showManualConnectDialog();
            }
        }, CONNECT_TIMEOUT_MS);
    }

    /** Android < 10 (API < 29): use WifiConfiguration (deprecated but functional) */
    @SuppressWarnings({"deprecation"})
    private void connectWifiLegacy(String ssid, String password) {
        // Remove existing saved network with same SSID to avoid conflicts
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (config.SSID != null && config.SSID.equals("\"" + ssid + "\"")) {
                    wifiManager.removeNetwork(config.networkId);
                }
            }
        }

        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"" + ssid + "\"";
        wifiConfig.preSharedKey = "\"" + password + "\"";
        wifiConfig.status = WifiConfiguration.Status.ENABLED;
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

        int networkId = wifiManager.addNetwork(wifiConfig);
        if (networkId == -1) {
            setConnectingState(false);
            showManualConnectDialog();
            return;
        }

        wifiManager.disconnect();
        boolean enabled = wifiManager.enableNetwork(networkId, true);
        wifiManager.reconnect();

        if (!enabled) {
            setConnectingState(false);
            showManualConnectDialog();
            return;
        }

        // Poll for connection result
        final String targetSsid = ssid;
        final int[] attempts = {0};
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                attempts[0]++;
                if (!isConnecting) return;

                @SuppressWarnings("deprecation")
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String connected = wifiInfo != null ? wifiInfo.getSSID() : null;

                if (connected != null && (connected.equals("\"" + targetSsid + "\"") || connected.equals(targetSsid))) {
                    setConnectingState(false);
                    openWebView();
                } else if (attempts[0] < 15) {
                    handler.postDelayed(this, 1500);
                } else {
                    setConnectingState(false);
                    showManualConnectDialog();
                }
            }
        }, 1500);
    }

    private void openWebView() {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra(WebViewActivity.EXTRA_URL, "https://www.baidu.com/?tn=68018901_16_pg");
        startActivity(intent);
    }

    private void showManualConnectDialog() {
        if (isFinishing() || isDestroyed()) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_manual_connect, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnManualConnect = dialogView.findViewById(R.id.btnManualConnect);
        Button btnCancelDialog = dialogView.findViewById(R.id.btnCancelDialog);

        btnManualConnect.setOnClickListener(v -> {
            dialog.dismiss();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(new Intent(Settings.Panel.ACTION_WIFI));
            } else {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });

        btnCancelDialog.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void setConnectingState(boolean connecting) {
        isConnecting = connecting;
        btnConnect.setEnabled(!connecting);
        progressBar.setVisibility(connecting ? View.VISIBLE : View.GONE);
        tvConnecting.setVisibility(connecting ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String message, boolean isError) {
        if (message == null || message.isEmpty()) {
            tvStatus.setVisibility(View.GONE);
            return;
        }
        tvStatus.setText(message);
        tvStatus.setTextColor(isError ? 0xFFF44336 : 0xFF4CAF50);
        tvStatus.setVisibility(View.VISIBLE);
    }

    private void cleanupNetworkCallback() {
        if (networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
            networkCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "需要位置权限才能搜索WiFi网络", Toast.LENGTH_LONG).show();
                    break;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        cleanupNetworkCallback();
    }
}
