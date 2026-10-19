package com.dentacloud.app.ui.calendar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.local.dao.AppointmentRow;
import com.dentacloud.app.data.repo.AppointmentRepository;
import com.dentacloud.app.ui.common.Colors;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class CalendarViewModel extends ViewModel {
    /** Day or week window starting at {@code start}. */
    public record Window(LocalDate start, boolean week) {
        int days() { return week ? 7 : 1; }
    }

    private final AppointmentRepository repo;
    private final MutableLiveData<Window> window = new MutableLiveData<>(new Window(LocalDate.now(), false));
    private final LiveData<List<AppointmentRow>> rows;

    @Inject
    public CalendarViewModel(AppointmentRepository repo) {
        this.repo = repo;
        rows = Transformations.switchMap(window, w -> repo.range(w.start(), w.days()));
    }

    public LiveData<Window> window() { return window; }
    public LiveData<List<AppointmentRow>> rows() { return rows; }

    public void show(LocalDate day, boolean week) {
        window.setValue(new Window(week ? day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : day, week));
    }

    public void step(int direction) {
        Window w = window.getValue();
        show(w.start().plusDays((long) direction * w.days()), w.week());
    }

    public void randomColor(AppointmentRow r) {
        repo.setColor(r.appt.id, Colors.randomLabel());
    }
}
