package com.dentacloud.app.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.databinding.FragmentSignupBinding;

import dagger.hilt.android.AndroidEntryPoint;

/** Onboarding step 1 (F1): clinic and owner account. Staff, chairs and hours follow in settings. */
@AndroidEntryPoint
public class SignupFragment extends Fragment {
    private FragmentSignupBinding b;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentSignupBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        LoginViewModel vm = new ViewModelProvider(this).get(LoginViewModel.class);
        b.create.setOnClickListener(v -> vm.signup(text(b.clinic), text(b.name), text(b.email), text(b.phone),
                String.valueOf(b.password.getText())));
        vm.error().observe(getViewLifecycleOwner(), err -> {
            b.error.setText(err);
            b.error.setVisibility(err == null ? View.GONE : View.VISIBLE);
        });
        vm.state().observe(getViewLifecycleOwner(), s -> {
            b.create.setEnabled(s != LoginViewModel.State.BUSY);
            if (s == LoginViewModel.State.DONE) {
                NavHostFragment.findNavController(this).navigate(R.id.dashboardFragment, null,
                        new NavOptions.Builder().setPopUpTo(R.id.loginFragment, true).build());
            }
        });
    }

    private static String text(android.widget.EditText e) {
        return String.valueOf(e.getText()).trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
