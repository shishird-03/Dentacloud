package com.dentacloud.app.ui.calendar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.local.entity.ProcedureEntity;
import com.dentacloud.app.data.repo.AppointmentRepository;
import com.dentacloud.app.data.repo.BillingRepository;
import com.dentacloud.app.data.repo.PatientRepository;
import com.dentacloud.app.util.Dates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class BookViewModel extends ViewModel {
    public enum Result { NONE, BOOKED }

    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final BillingRepository billing;
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Result> result = new MutableLiveData<>(Result.NONE);

    public LocalDate date = LocalDate.now();
    public LocalTime time = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);

    @Inject
    public BookViewModel(AppointmentRepository appointments, PatientRepository patients, BillingRepository billing) {
        this.appointments = appointments;
        this.patients = patients;
        this.billing = billing;
    }

    public LiveData<List<PatientEntity>> patients() { return patients.search(""); }
    public LiveData<List<ProcedureEntity>> procedures() { return billing.procedures(); }
    public LiveData<PatientEntity> patient(String id) { return patients.observe(id); }
    public LiveData<String> error() { return error; }
    public LiveData<Result> result() { return result; }

    public void book(PatientEntity patient, ProcedureEntity procedure, String reason) {
        if (patient == null) {
            error.setValue("Choose a patient");
            return;
        }
        AppointmentEntity a = new AppointmentEntity();
        a.patientId = patient.id;
        a.startAt = Dates.toMillis(LocalDateTime.of(date, time));
        int minutes = procedure == null ? 30 : procedure.defaultMinutes;
        a.endAt = a.startAt + minutes * 60_000L;
        a.procedureId = procedure == null ? null : procedure.id;
        a.reason = reason.isBlank() ? null : reason.trim();
        appointments.book(a, err -> {
            error.postValue(err);
            if (err == null) result.postValue(Result.BOOKED);
        });
    }
}
