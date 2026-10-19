package com.dentacloud.app.ui.calendar;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.dao.AppointmentRow;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.databinding.FragmentAppointmentDetailBinding;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.ui.common.Colors;
import com.dentacloud.app.ui.common.Dialogs;
import com.dentacloud.app.ui.common.Intents;
import com.dentacloud.app.util.Dates;

import dagger.hilt.android.AndroidEntryPoint;

/** Target of the reminder notification deep link (Part B #7). */
@AndroidEntryPoint
public class AppointmentDetailFragment extends Fragment {
    private FragmentAppointmentDetailBinding b;
    private AppointmentDetailViewModel vm;
    private AppointmentRow row;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentAppointmentDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(AppointmentDetailViewModel.class);
        String id = Args.get(getArguments(), Args.APPOINTMENT_ID);
        vm.appointment(id).observe(getViewLifecycleOwner(), this::bind);

        b.arrived.setOnClickListener(v -> vm.setStatus(id, AppointmentEntity.ARRIVED));
        b.inChair.setOnClickListener(v -> vm.setStatus(id, AppointmentEntity.IN_CHAIR));
        b.done.setOnClickListener(v -> vm.setStatus(id, AppointmentEntity.DONE));
        b.noShow.setOnClickListener(v -> vm.setStatus(id, AppointmentEntity.NO_SHOW));
        b.cancel.setOnClickListener(v -> Dialogs.confirm(requireContext(), getString(R.string.cancel_appointment),
                getString(R.string.cancel_appointment_message), () -> vm.setStatus(id, AppointmentEntity.CANCELLED)));
        b.patient.setOnClickListener(v -> {
            if (row != null) NavHostFragment.findNavController(this).navigate(R.id.patientDetailFragment,
                    Args.of(Args.PATIENT_ID, row.appt.patientId));
        });
        b.call.setOnClickListener(v -> Intents.call(requireContext(), row.patientPhone));
        b.sms.setOnClickListener(v -> Intents.sms(requireContext(), row.patientPhone,
                getString(R.string.sms_reminder_body, vm.clinicName(), Dates.dayTime(row.appt.startAt))));
    }

    private void bind(AppointmentRow r) {
        if (r == null) return;
        row = r;
        b.patient.setText(r.patientName);
        b.when.setText(Dates.dayTime(r.appt.startAt) + " – " + Dates.time(r.appt.endAt));
        b.what.setText(r.procedureName == null ? r.appt.reason : r.procedureName);
        b.status.setText(r.appt.status.replace('_', ' '));
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(24f);
        chip.setColor(ContextCompat.getColor(requireContext(), Colors.status(r.appt.status)));
        b.status.setBackground(chip);
        boolean hasPhone = r.patientPhone != null && !r.patientPhone.isEmpty();
        b.call.setEnabled(hasPhone);
        b.sms.setEnabled(hasPhone);
        boolean open = r.appt.isActive() && !AppointmentEntity.DONE.equals(r.appt.status);
        b.cancel.setEnabled(open);
        b.statuses.setEnabled(open);
        for (int i = 0; i < b.statuses.getChildCount(); i++) b.statuses.getChildAt(i).setEnabled(r.appt.isActive());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
