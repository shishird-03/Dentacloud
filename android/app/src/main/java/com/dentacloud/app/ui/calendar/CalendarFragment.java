package com.dentacloud.app.ui.calendar;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.databinding.FragmentCalendarBinding;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.util.Dates;

import java.time.LocalDate;

import dagger.hilt.android.AndroidEntryPoint;

/** Day and week views of the schedule (F4). Long-press an appointment to give it a random color label. */
@AndroidEntryPoint
public class CalendarFragment extends Fragment {
    private FragmentCalendarBinding b;
    private CalendarViewModel vm;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentCalendarBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(CalendarViewModel.class);
        AppointmentAdapter adapter = new AppointmentAdapter(false,
                r -> NavHostFragment.findNavController(this).navigate(R.id.appointmentDetailFragment,
                        Args.of(Args.APPOINTMENT_ID, r.appt.id)),
                r -> {
                    vm.randomColor(r);
                    Toast.makeText(requireContext(), R.string.color_changed, Toast.LENGTH_SHORT).show();
                });
        b.list.setAdapter(adapter);

        vm.window().observe(getViewLifecycleOwner(), w -> {
            b.date.setText(w.week() ? getString(R.string.week_of, Dates.day(w.start())) : Dates.day(w.start()));
            adapter.setShowDate(w.week());
            b.mode.check(w.week() ? R.id.mode_week : R.id.mode_day); // no-op when already checked
        });
        vm.rows().observe(getViewLifecycleOwner(), rows -> {
            adapter.submitList(rows);
            b.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });

        b.prev.setOnClickListener(v -> vm.step(-1));
        b.next.setOnClickListener(v -> vm.step(1));
        b.date.setOnClickListener(v -> {
            LocalDate d = vm.window().getValue().start();
            new DatePickerDialog(requireContext(), (p, y, m, day) -> vm.show(LocalDate.of(y, m + 1, day), isWeek()),
                    d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
        });
        b.mode.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) vm.show(vm.window().getValue().start(), id == R.id.mode_week);
        });
        b.book.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.bookAppointmentFragment));
    }

    private boolean isWeek() {
        return b.mode.getCheckedButtonId() == R.id.mode_week;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
