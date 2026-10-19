package com.dentacloud.app.ui.calendar;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.local.entity.ProcedureEntity;
import com.dentacloud.app.databinding.FragmentBookBinding;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.util.Dates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/** Part A #8: date picker + procedure spinner, Toast on success. */
@AndroidEntryPoint
public class BookAppointmentFragment extends Fragment {
    private FragmentBookBinding b;
    private BookViewModel vm;
    private PatientEntity patient;
    private List<PatientEntity> allPatients = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentBookBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(BookViewModel.class);
        String fixedPatientId = Args.get(getArguments(), Args.PATIENT_ID);

        if (fixedPatientId != null) {
            b.patient.setEnabled(false);
            vm.patient(fixedPatientId).observe(getViewLifecycleOwner(), p -> {
                patient = p;
                if (p != null) b.patient.setText(p.fullName, false);
            });
        } else {
            vm.patients().observe(getViewLifecycleOwner(), list -> {
                allPatients = list;
                List<String> labels = new ArrayList<>();
                for (PatientEntity p : list) labels.add(label(p));
                b.patient.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, labels));
            });
            b.patient.setOnItemClickListener((parent, v, pos, id) -> {
                String chosen = (String) parent.getItemAtPosition(pos);
                for (PatientEntity p : allPatients) if (label(p).equals(chosen)) patient = p;
            });
        }

        vm.procedures().observe(getViewLifecycleOwner(), list -> {
            ArrayAdapter<ProcedureEntity> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, list);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            b.procedure.setAdapter(a);
        });

        showWhen();
        b.date.setOnClickListener(v -> {
            DatePickerDialog d = new DatePickerDialog(requireContext(), (p, y, m, day) -> {
                vm.date = LocalDate.of(y, m + 1, day);
                showWhen();
            }, vm.date.getYear(), vm.date.getMonthValue() - 1, vm.date.getDayOfMonth());
            d.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            d.show();
        });
        b.time.setOnClickListener(v -> new TimePickerDialog(requireContext(), (p, h, min) -> {
            vm.time = vm.time.withHour(h).withMinute(min);
            showWhen();
        }, vm.time.getHour(), vm.time.getMinute(), DateFormat.is24HourFormat(requireContext())).show());

        vm.error().observe(getViewLifecycleOwner(), err -> {
            b.error.setText(err);
            b.error.setVisibility(err == null ? View.GONE : View.VISIBLE);
            b.book.setEnabled(true);
        });
        vm.result().observe(getViewLifecycleOwner(), r -> {
            if (r != BookViewModel.Result.BOOKED) return;
            Toast.makeText(requireContext(), getString(R.string.booked_toast,
                    Dates.dayTime(Dates.toMillis(LocalDateTime.of(vm.date, vm.time)))), Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).popBackStack();
        });
        b.book.setOnClickListener(v -> {
            b.book.setEnabled(false);
            vm.book(patient, (ProcedureEntity) b.procedure.getSelectedItem(), String.valueOf(b.reason.getText()));
        });
    }

    private static String label(PatientEntity p) {
        return p.fullName + " · " + p.patientNo;
    }

    private void showWhen() {
        b.date.setText(Dates.day(vm.date));
        b.time.setText(Dates.time(Dates.toMillis(LocalDateTime.of(vm.date, vm.time))));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
