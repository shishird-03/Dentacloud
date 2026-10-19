package com.dentacloud.app.ui.patients;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.databinding.FragmentPatientListBinding;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.ui.common.Dialogs;
import com.dentacloud.app.ui.common.Intents;

import dagger.hilt.android.AndroidEntryPoint;

/** Part B #4 (list from DB), #5 (search by phone or ID, then call), Part A #5 (context menu). */
@AndroidEntryPoint
public class PatientListFragment extends Fragment {
    private FragmentPatientListBinding b;
    private PatientListViewModel vm;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentPatientListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(PatientListViewModel.class);
        PatientAdapter adapter = new PatientAdapter(this::open,
                p -> Intents.call(requireContext(), p.phone), this::contextMenu);
        b.list.setAdapter(adapter);

        vm.patients().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            b.empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { vm.search(s.toString()); }
        });
        b.add.setOnClickListener(v -> nav().navigate(R.id.patientFormStep1Fragment));
    }

    /** Tablets show the profile in the right-hand pane; phones navigate to it. */
    private void open(PatientEntity p) {
        if (b.detailPane != null) {
            PatientDetailFragment detail = new PatientDetailFragment();
            detail.setArguments(Args.of(Args.PATIENT_ID, p.id));
            getChildFragmentManager().beginTransaction().replace(R.id.detail_pane, detail).commit();
        } else {
            nav().navigate(R.id.patientDetailFragment, Args.of(Args.PATIENT_ID, p.id));
        }
    }

    private void contextMenu(ContextMenu menu, PatientEntity p) {
        requireActivity().getMenuInflater().inflate(R.menu.patient_context, menu);
        menu.setHeaderTitle(p.fullName);
        menu.findItem(R.id.action_edit).setOnMenuItemClickListener(i -> {
            nav().navigate(R.id.patientFormStep1Fragment, Args.of(Args.PATIENT_ID, p.id));
            return true;
        });
        menu.findItem(R.id.action_color).setOnMenuItemClickListener(i -> {
            vm.randomColor(p);
            return true;
        });
        menu.findItem(R.id.action_delete).setOnMenuItemClickListener(i -> {
            Dialogs.confirm(requireContext(), getString(R.string.delete_patient_title),
                    getString(R.string.delete_patient_message, p.fullName), () -> vm.delete(p));
            return true;
        });
    }

    private androidx.navigation.NavController nav() {
        return NavHostFragment.findNavController(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
