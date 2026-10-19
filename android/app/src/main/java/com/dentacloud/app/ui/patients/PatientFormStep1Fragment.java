package com.dentacloud.app.ui.patients;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.databinding.FragmentPatientFormStep1Binding;
import com.dentacloud.app.ui.common.Args;

import java.time.LocalDate;
import java.util.Arrays;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PatientFormStep1Fragment extends Fragment {
    private FragmentPatientFormStep1Binding b;
    private PatientFormViewModel vm;
    private PatientEntity draft;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentPatientFormStep1Binding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(PatientFormViewModel.class);
        vm.start(Args.get(getArguments(), Args.PATIENT_ID));

        vm.draft().observe(getViewLifecycleOwner(), d -> {
            if (d == null) return;
            draft = d;
            b.name.setText(d.fullName);
            b.phone.setText(d.phone);
            showDob();
            String[] genders = getResources().getStringArray(R.array.genders);
            int i = Arrays.asList(genders).indexOf(d.gender);
            b.gender.setSelection(Math.max(i, 0));
        });
        vm.duplicateWarning().observe(getViewLifecycleOwner(), dup ->
                b.phoneLayout.setHelperText(dup == null ? null : getString(R.string.duplicate_warning, dup)));

        b.phone.setOnFocusChangeListener((v, focused) -> {
            if (!focused) vm.checkDuplicatePhone(String.valueOf(b.phone.getText()));
        });
        b.dob.setOnClickListener(v -> pickDob());
        b.next.setOnClickListener(v -> next());
    }

    /** Part A #8: date picker. */
    private void pickDob() {
        LocalDate initial = draft != null && draft.dob != null ? LocalDate.parse(draft.dob) : LocalDate.now().minusYears(30);
        DatePickerDialog dlg = new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
            if (draft == null) return;
            draft.dob = LocalDate.of(y, m + 1, d).toString();
            showDob();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
        dlg.show();
    }

    private void showDob() {
        b.dob.setText(draft == null || draft.dob == null ? getString(R.string.pick_dob) : getString(R.string.dob_value, draft.dob));
    }

    private void next() {
        if (draft == null) return;
        String name = String.valueOf(b.name.getText()).trim();
        String phone = String.valueOf(b.phone.getText()).trim();
        b.nameLayout.setError(name.isEmpty() ? getString(R.string.required) : null);
        b.phoneLayout.setError(!phone.isEmpty() && !phone.matches("\\+?[0-9 ]{7,15}") ? getString(R.string.invalid_phone) : null);
        if (b.nameLayout.getError() != null || b.phoneLayout.getError() != null) return;
        draft.fullName = name;
        draft.phone = phone;
        draft.gender = (String) b.gender.getSelectedItem();
        NavHostFragment.findNavController(this).navigate(R.id.patientFormStep2Fragment);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
