package com.dentacloud.app.ui.patients;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.databinding.FragmentPatientFormStep2Binding;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.ui.common.Photos;

import java.io.File;

import dagger.hilt.android.AndroidEntryPoint;

/** Step 2: contact, medical history, allergies and a photo (Part A #10 camera capture). */
@AndroidEntryPoint
public class PatientFormStep2Fragment extends Fragment {
    private FragmentPatientFormStep2Binding b;
    private PatientFormViewModel vm;
    private PatientEntity draft;
    private File pendingPhoto;

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && draft != null && pendingPhoto != null) {
                    draft.photoPath = pendingPhoto.getAbsolutePath();
                    showPhoto();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentPatientFormStep2Binding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(PatientFormViewModel.class);
        vm.draft().observe(getViewLifecycleOwner(), d -> {
            if (d == null) return;
            draft = d;
            b.email.setText(d.email);
            b.address.setText(d.address);
            b.history.setText(d.medicalHistory);
            b.allergies.setText(d.allergies);
            showPhoto();
        });
        vm.saved().observe(getViewLifecycleOwner(), p -> {
            if (p == null) return;
            Toast.makeText(requireContext(), getString(R.string.patient_saved, p.patientNo), Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).navigate(R.id.patientDetailFragment, Args.of(Args.PATIENT_ID, p.id),
                    new NavOptions.Builder().setPopUpTo(R.id.patientFormStep1Fragment, true).build());
        });
        b.capture.setOnClickListener(v -> {
            pendingPhoto = Photos.newFile(requireContext());
            takePicture.launch(Photos.uriFor(requireContext(), pendingPhoto));
        });
        b.save.setOnClickListener(v -> save());
    }

    private void showPhoto() {
        if (draft != null && draft.photoPath != null) {
            b.photo.setImageBitmap(Photos.thumbnail(draft.photoPath, 256));
        }
    }

    private void save() {
        if (draft == null) return;
        draft.email = text(b.email);
        draft.address = text(b.address);
        draft.medicalHistory = text(b.history);
        draft.allergies = text(b.allergies);
        b.save.setEnabled(false);
        vm.save();
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
