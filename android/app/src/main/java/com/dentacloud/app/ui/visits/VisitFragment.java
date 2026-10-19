package com.dentacloud.app.ui.visits;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.VisitEntity;
import com.dentacloud.app.data.repo.ClinicalRepository;
import com.dentacloud.app.databinding.FragmentVisitBinding;
import com.dentacloud.app.ui.common.Args;
import com.google.android.material.chip.Chip;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.lifecycle.HiltViewModel;

/** Visit notes (F6): complaint, diagnosis, notes, with quick phrases for common entries. */
@AndroidEntryPoint
public class VisitFragment extends Fragment {
    private static final String[] PHRASES = {"Medical history reviewed", "LA given", "Scaling done",
            "Advised RCT", "Restoration done", "Post-op instructions given", "Review in 1 week", "Review in 6 months"};

    @HiltViewModel
    public static class VisitViewModel extends ViewModel {
        private final ClinicalRepository clinical;
        private final MutableLiveData<VisitEntity> visit = new MutableLiveData<>();

        @Inject
        public VisitViewModel(ClinicalRepository clinical) {
            this.clinical = clinical;
        }

        LiveData<VisitEntity> visit() { return visit; }

        void start(String visitId, String patientId, String appointmentId) {
            if (visit.getValue() != null) return;
            if (visitId != null) {
                clinical.loadVisit(visitId, visit::postValue);
            } else {
                VisitEntity v = new VisitEntity();
                v.patientId = patientId;
                v.appointmentId = appointmentId;
                visit.setValue(v);
            }
        }

        void save(String complaint, String diagnosis, String notes, Runnable onSaved) {
            VisitEntity v = visit.getValue();
            if (v == null) return;
            v.complaint = complaint;
            v.diagnosis = diagnosis;
            v.notes = notes;
            clinical.saveVisit(v, saved -> {
                visit.postValue(saved);
                onSaved.run();
            });
        }
    }

    private FragmentVisitBinding b;
    private VisitViewModel vm;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentVisitBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(VisitViewModel.class);
        String patientId = Args.get(getArguments(), Args.PATIENT_ID);
        vm.start(Args.get(getArguments(), Args.VISIT_ID), patientId, Args.get(getArguments(), Args.APPOINTMENT_ID));
        vm.visit().observe(getViewLifecycleOwner(), v -> {
            if (v == null || b.complaint.length() > 0 || b.diagnosis.length() > 0 || b.notes.length() > 0) return;
            b.complaint.setText(v.complaint);
            b.diagnosis.setText(v.diagnosis);
            b.notes.setText(v.notes);
        });

        for (String p : PHRASES) {
            Chip chip = new Chip(requireContext());
            chip.setText(p);
            chip.setOnClickListener(v -> {
                String now = String.valueOf(b.notes.getText()).trim();
                b.notes.setText(now.isEmpty() ? p + "." : now + " " + p + ".");
                b.notes.setSelection(b.notes.length());
            });
            b.phrases.addView(chip);
        }
        b.save.setOnClickListener(v -> vm.save(text(b.complaint), text(b.diagnosis), text(b.notes), () ->
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), R.string.visit_saved, Toast.LENGTH_SHORT).show())));
        b.chart.setOnClickListener(v -> nav().navigate(R.id.chartFragment, Args.of(Args.PATIENT_ID, patientId,
                Args.VISIT_ID, visitIdOrNull())));
        b.prescribe.setOnClickListener(v -> nav().navigate(R.id.prescriptionFragment, Args.of(Args.PATIENT_ID, patientId,
                Args.VISIT_ID, visitIdOrNull())));
    }

    /** Chart records and prescriptions link to the visit once it has been saved. */
    private String visitIdOrNull() {
        VisitEntity v = vm.visit().getValue();
        return v == null || v.id.isEmpty() ? null : v.id;
    }

    private static String text(EditText e) {
        String s = String.valueOf(e.getText()).trim();
        return s.isEmpty() ? null : s;
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
