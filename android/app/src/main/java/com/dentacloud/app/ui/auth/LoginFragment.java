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
import com.dentacloud.app.databinding.FragmentLoginBinding;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LoginFragment extends Fragment {
    private FragmentLoginBinding b;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentLoginBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        LoginViewModel vm = new ViewModelProvider(this).get(LoginViewModel.class);
        b.demoHint.setText(R.string.demo_hint);
        b.login.setOnClickListener(v -> vm.login(String.valueOf(b.email.getText()), String.valueOf(b.password.getText())));
        b.signup.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.signupFragment));
        b.demo.setOnClickListener(v -> vm.demo());

        vm.error().observe(getViewLifecycleOwner(), err -> {
            b.error.setText(err);
            b.error.setVisibility(err == null ? View.GONE : View.VISIBLE);
        });
        vm.state().observe(getViewLifecycleOwner(), s -> {
            b.progress.setVisibility(s == LoginViewModel.State.BUSY ? View.VISIBLE : View.GONE);
            b.login.setEnabled(s != LoginViewModel.State.BUSY);
            b.demo.setEnabled(s != LoginViewModel.State.BUSY);
            if (s == LoginViewModel.State.DONE) {
                NavHostFragment.findNavController(this).navigate(R.id.dashboardFragment, null,
                        new NavOptions.Builder().setPopUpTo(R.id.loginFragment, true).build());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
