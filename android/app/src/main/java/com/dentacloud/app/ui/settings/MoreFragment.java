package com.dentacloud.app.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.repo.AuthRepository;
import com.dentacloud.app.databinding.FragmentMoreBinding;
import com.dentacloud.app.service.ReminderForegroundService;
import com.dentacloud.app.service.ReminderWorker;
import com.dentacloud.app.service.Reminders;
import com.dentacloud.app.ui.common.Dialogs;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MoreFragment extends Fragment {
    @Inject SessionStore session;
    @Inject AuthRepository auth;
    private FragmentMoreBinding b;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentMoreBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        b.who.setText(getString(R.string.signed_in_as, session.userName(), session.clinicName(), session.role()));
        b.settings.setOnClickListener(v -> nav().navigate(R.id.settingsFragment));
        b.map.setOnClickListener(v -> nav().navigate(R.id.clinicMapFragment));
        b.tips.setOnClickListener(v -> nav().navigate(R.id.tipsFragment));
        b.lab.setOnClickListener(v -> nav().navigate(R.id.asyncTaskLabFragment));
        b.logout.setOnClickListener(v -> Dialogs.confirm(requireContext(), getString(R.string.logout),
                getString(R.string.logout_message), () -> {
                    Reminders.setEnabled(requireContext(), false);
                    ReminderForegroundService.stop(requireContext());
                    ReminderWorker.cancel(requireContext());
                    auth.logout();
                    nav().navigate(R.id.loginFragment, null,
                            new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build());
                }));
    }

    private NavController nav() {
        return NavHostFragment.findNavController(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
