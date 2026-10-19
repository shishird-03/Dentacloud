package com.dentacloud.app.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.dao.AppointmentRow;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.data.repo.AppointmentRepository;
import com.dentacloud.app.util.Dates;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/** F11: today's schedule, collections, pending dues and new patients, all from Room (works offline). */
@HiltViewModel
public class DashboardViewModel extends ViewModel {
    private final AppointmentRepository appointments;
    private final LiveData<List<AppointmentRow>> today;
    private final LiveData<Integer> newPatientsThisWeek;
    private final LiveData<Long> collectedToday;
    private final LiveData<Long> dues;
    private final SessionStore session;

    @Inject
    public DashboardViewModel(AppointmentRepository appointments, AppDatabase db, SessionStore session) {
        this.appointments = appointments;
        this.session = session;
        LocalDate now = LocalDate.now(Dates.zone());
        today = appointments.range(now, 1);
        long weekStart = Dates.startOfDay(now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        newPatientsThisWeek = db.patients().countNewSince(session.clinicId(), weekStart);
        collectedToday = db.invoices().collectedSince(session.clinicId(), Dates.startOfDay(now));
        dues = db.invoices().duesTotal(session.clinicId());
    }

    public LiveData<List<AppointmentRow>> today() { return today; }
    public LiveData<Integer> newPatientsThisWeek() { return newPatientsThisWeek; }
    public LiveData<Long> collectedToday() { return collectedToday; }
    public LiveData<Long> dues() { return dues; }
    public String userName() { return session.userName(); }
    public String clinicName() { return session.clinicName(); }

    /** Quick check-in from the dashboard (receptionist's busiest job: one long-press). */
    public void checkIn(AppointmentRow row) {
        if (AppointmentEntity.BOOKED.equals(row.appt.status)) appointments.setStatus(row.appt.id, AppointmentEntity.ARRIVED);
    }
}
