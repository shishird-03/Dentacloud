package com.dentacloud.app.ui.prescriptions;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.local.entity.PrescriptionEntity;
import com.dentacloud.app.data.local.entity.PrescriptionItemEntity;
import com.dentacloud.app.data.repo.PatientRepository;
import com.dentacloud.app.data.repo.PrescriptionRepository;
import com.dentacloud.app.databinding.FragmentPrescriptionBinding;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.ui.common.Dialogs;
import com.dentacloud.app.ui.common.Share;
import com.dentacloud.app.util.Dates;
import com.dentacloud.app.util.PdfGenerator;
import com.dentacloud.app.util.PdfGenerator.Row;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.lifecycle.HiltViewModel;

/** Prescriptions (F9): drug catalog, favourites, PDF with clinic letterhead via the share sheet. */
@AndroidEntryPoint
public class PrescriptionFragment extends Fragment {
    @HiltViewModel
    public static class RxViewModel extends ViewModel {
        final PrescriptionRepository repo;
        final PatientRepository patients;
        final SessionStore session;
        final ExecutorService io;
        final List<PrescriptionItemEntity> items = new ArrayList<>();
        final MutableLiveData<File> pdf = new MutableLiveData<>();

        @Inject
        public RxViewModel(PrescriptionRepository repo, PatientRepository patients, SessionStore session, ExecutorService io) {
            this.repo = repo;
            this.patients = patients;
            this.session = session;
            this.io = io;
        }

        void issue(Context app, String patientId, String visitId, String notes) {
            repo.issue(patientId, visitId, notes, new ArrayList<>(items), rx -> patients.load(patientId,
                    p -> io.execute(() -> pdf.postValue(render(app, rx, p)))));
        }

        private File render(Context app, PrescriptionEntity rx, PatientEntity p) {
            List<Row> rows = new ArrayList<>();
            rows.add(Row.of("Patient: " + (p == null ? "" : p.fullName + " (" + p.patientNo + ")")));
            if (p != null && p.allergies != null) rows.add(new Row("Allergies: " + p.allergies, null, true));
            rows.add(Row.of("Date: " + Dates.dayTime(rx.issuedAt)));
            rows.add(Row.of(""));
            int n = 1;
            for (PrescriptionItemEntity it : items) {
                rows.add(new Row(n++ + ". " + it.drugName, null, true));
                rows.add(Row.of("    " + join(it.dose, it.frequency, it.durationDays == null ? null : it.durationDays + " days")));
                if (it.instructions != null) rows.add(Row.of("    " + it.instructions));
            }
            if (rx.notes != null) {
                rows.add(Row.of(""));
                rows.add(Row.of("Advice: " + rx.notes));
            }
            rows.add(Row.of(""));
            rows.add(Row.of("Dr. " + session.userName().replaceFirst("^Dr\\.?\\s*", "")));
            try {
                return PdfGenerator.write(app, "rx-" + rx.id + ".pdf", session.clinicName(), null, "Prescription",
                        rows, "Take medicines as advised. Contact the clinic if you have side effects.");
            } catch (IOException e) {
                return null;
            }
        }

        private static String join(String... parts) {
            StringBuilder sb = new StringBuilder();
            for (String s : parts) {
                if (s == null || s.isBlank()) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(s);
            }
            return sb.toString();
        }
    }

    private FragmentPrescriptionBinding b;
    private RxViewModel vm;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentPrescriptionBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(RxViewModel.class);
        String patientId = Args.get(getArguments(), Args.PATIENT_ID);
        String visitId = Args.get(getArguments(), Args.VISIT_ID);

        List<String> names = new ArrayList<>();
        for (String[] d : PrescriptionRepository.CATALOG) names.add(d[0]);
        b.drug.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names));
        b.drug.setOnItemClickListener((p, v, pos, id) -> fillDefaults((String) p.getItemAtPosition(pos)));
        for (int i = 0; i < 5; i++) {
            String[] d = PrescriptionRepository.CATALOG[i];
            Chip chip = new Chip(requireContext());
            chip.setText(d[0]);
            chip.setOnClickListener(v -> {
                fillDefaults(d[0]);
                addItem();
            });
            b.favourites.addView(chip);
        }
        b.add.setOnClickListener(v -> addItem());
        b.issue.setOnClickListener(v -> {
            if (vm.items.isEmpty()) {
                Toast.makeText(requireContext(), R.string.add_a_drug, Toast.LENGTH_SHORT).show();
                return;
            }
            b.issue.setEnabled(false);
            String notes = String.valueOf(b.notes.getText()).trim();
            vm.issue(requireContext().getApplicationContext(), patientId, visitId, notes.isEmpty() ? null : notes);
        });
        vm.pdf.observe(getViewLifecycleOwner(), f -> {
            if (f == null) return;
            vm.pdf.setValue(null);
            Share.pdf(requireContext(), f, getString(R.string.prescription));
            NavHostFragment.findNavController(this).popBackStack();
        });
        renderItems();
    }

    private void fillDefaults(String drug) {
        for (String[] d : PrescriptionRepository.CATALOG) {
            if (!d[0].equals(drug)) continue;
            b.drug.setText(d[0], false);
            b.dose.setText(d[1]);
            b.frequency.setText(d[2]);
            b.days.setText(d[3]);
        }
    }

    private void addItem() {
        String drug = String.valueOf(b.drug.getText()).trim();
        if (drug.isEmpty()) {
            b.drug.setError(getString(R.string.required));
            return;
        }
        PrescriptionItemEntity it = new PrescriptionItemEntity();
        it.drugName = drug;
        it.dose = text(b.dose);
        it.frequency = text(b.frequency);
        String days = String.valueOf(b.days.getText()).trim();
        it.durationDays = days.isEmpty() ? null : Integer.parseInt(days);
        vm.items.add(it);
        b.drug.setText("", false);
        b.dose.setText("");
        b.frequency.setText("");
        b.days.setText("");
        renderItems();
    }

    private void renderItems() {
        b.items.removeAllViews();
        for (PrescriptionItemEntity it : vm.items) {
            TextView t = new TextView(requireContext());
            t.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch));
            t.setGravity(android.view.Gravity.CENTER_VERTICAL);
            t.setText(getString(R.string.rx_item_row, it.drugName, it.dose == null ? "" : it.dose,
                    it.frequency == null ? "" : it.frequency, it.durationDays == null ? "-" : String.valueOf(it.durationDays)));
            t.setOnLongClickListener(v -> {
                Dialogs.confirm(requireContext(), getString(R.string.remove_drug), it.drugName, () -> {
                    vm.items.remove(it);
                    renderItems();
                });
                return true;
            });
            b.items.addView(t);
        }
    }

    private static String text(android.widget.EditText e) {
        String s = String.valueOf(e.getText()).trim();
        return s.isEmpty() ? null : s;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
