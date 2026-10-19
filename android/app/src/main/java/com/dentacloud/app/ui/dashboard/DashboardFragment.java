package com.dentacloud.app.ui.dashboard;

import android.Manifest;
import android.os.Build;
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
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.dao.AppointmentRow;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.databinding.FragmentDashboardBinding;
import com.dentacloud.app.ui.calendar.AppointmentAdapter;
import com.dentacloud.app.service.ReminderForegroundService;
import com.dentacloud.app.service.ReminderWorker;
import com.dentacloud.app.service.Reminders;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.util.Money;

import dagger.hilt.android.AndroidEntryPoint;

/** Today: schedule, quick actions and headline numbers (F11). */
@AndroidEntryPoint
public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding b;
    private DashboardViewModel vm;
    private final ActivityResultLauncher<String> askNotifications =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted && b != null) Toast.makeText(requireContext(), R.string.notifications_denied, Toast.LENGTH_LONG).show();
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentDashboardBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(DashboardViewModel.class);
        b.greeting.setText(getString(R.string.greeting, vm.userName()));
        b.clinic.setText(vm.clinicName());

        AppointmentAdapter adapter = new AppointmentAdapter(false,
                r -> nav().navigate(R.id.appointmentDetailFragment, Args.of(Args.APPOINTMENT_ID, r.appt.id)),
                r -> {
                    vm.checkIn(r);
                    Toast.makeText(requireContext(), R.string.checked_in, Toast.LENGTH_SHORT).show();
                });
        b.today.setAdapter(adapter);
        vm.today().observe(getViewLifecycleOwner(), rows -> {
            adapter.submitList(rows);
            b.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            int active = 0, waiting = 0;
            for (AppointmentRow r : rows) {
                if (r.appt.isActive()) active++;
                if (AppointmentEntity.ARRIVED.equals(r.appt.status)) waiting++;
            }
            b.statAppointments.setText(String.valueOf(active));
            b.statWaiting.setText(String.valueOf(waiting));
        });
        vm.newPatientsThisWeek().observe(getViewLifecycleOwner(), n -> b.statNewPatients.setText(String.valueOf(n)));
        vm.collectedToday().observe(getViewLifecycleOwner(), p -> b.statCollected.setText(Money.format(p == null ? 0 : p)));
        vm.dues().observe(getViewLifecycleOwner(), p -> b.statDues.setText(Money.format(p == null ? 0 : Math.max(p, 0))));

        b.reminders.setChecked(Reminders.enabled(requireContext()));
        b.reminders.setOnCheckedChangeListener((s, on) -> {
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS);
            setReminders(on);
        });

        b.newPatient.setOnClickListener(v -> nav().navigate(R.id.patientFormStep1Fragment));
        b.book.setOnClickListener(v -> nav().navigate(R.id.bookAppointmentFragment));
    }

    /** Part B #1: the foreground service watches today's appointments; WorkManager covers when it's stopped. */
    private void setReminders(boolean on) {
        Reminders.setEnabled(requireContext(), on);
        if (on) {
            ReminderForegroundService.start(requireContext());
            ReminderWorker.schedule(requireContext());
        } else {
            ReminderForegroundService.stop(requireContext());
            ReminderWorker.cancel(requireContext());
        }
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
