package com.dentacloud.app.ui.tips;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/**
 * Part B #8: WebView with two pages (tips and the clinic page). Content ships in the APK, works
 * offline, and JavaScript stays off because nothing needs it.
 */
public class TipsFragment extends Fragment {
    private WebView web;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        web = new WebView(requireContext());
        web.getSettings().setJavaScriptEnabled(false);
        web.getSettings().setAllowFileAccess(false); // android_asset URLs work regardless
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Only the bundled pages open inside the app.
                return !request.getUrl().toString().startsWith("file:///android_asset/tips/");
            }
        });
        if (state == null) web.loadUrl("file:///android_asset/tips/index.html");
        else web.restoreState(state);
        return web;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    public void onDestroyView() {
        web.destroy();
        web = null;
        super.onDestroyView();
    }
}
