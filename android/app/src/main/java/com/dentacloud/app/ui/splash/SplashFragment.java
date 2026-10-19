package com.dentacloud.app.ui.splash;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.databinding.FragmentSplashBinding;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** Part B #10: zoom + rotate on the logo, move on the title. */
@AndroidEntryPoint
public class SplashFragment extends Fragment {
    private static final long SPLASH_MS = 1200;

    @Inject SessionStore session;
    private FragmentSplashBinding b;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentSplashBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        AnimationSet logo = new AnimationSet(false);
        logo.addAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.zoom));
        logo.addAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.rotate));
        b.logo.startAnimation(logo);
        b.title.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.move));
        view.postDelayed(this::next, SPLASH_MS);
    }

    private void next() {
        if (b == null) return; // view already gone
        int target = session.isLoggedIn() ? R.id.dashboardFragment : R.id.loginFragment;
        NavHostFragment.findNavController(this).navigate(target, null,
                new NavOptions.Builder().setPopUpTo(R.id.splashFragment, true).build());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
