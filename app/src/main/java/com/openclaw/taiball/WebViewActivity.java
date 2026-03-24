package com.openclaw.taiball;

import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";

    private WebView webView;
    private ProgressBar pageLoadProgress;
    private ConnectivityManager connectivityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        webView = findViewById(R.id.webView);
        pageLoadProgress = findViewById(R.id.page_load_progress);
        ImageButton btnBack = findViewById(R.id.btn_back);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        btnBack.setOnClickListener(v -> navigateBack());

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    pageLoadProgress.setVisibility(View.VISIBLE);
                    pageLoadProgress.setProgress(newProgress);
                } else {
                    pageLoadProgress.setVisibility(View.GONE);
                }
            }
        });

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        }
    }

    private void navigateBack() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        // Release the WiFi network binding so other activities can use the default network
        if (connectivityManager != null) {
            connectivityManager.bindProcessToNetwork(null);
        }
        super.onDestroy();
    }
}
