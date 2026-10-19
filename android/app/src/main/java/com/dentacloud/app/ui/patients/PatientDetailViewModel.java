package com.dentacloud.app.ui.patients;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.dao.AppointmentRow;
import com.dentacloud.app.data.local.entity.InvoiceEntity;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.repo.AppointmentRepository;
import com.dentacloud.app.data.repo.BillingRepository;
import com.dentacloud.app.data.repo.PatientRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class PatientDetailViewModel extends ViewModel {
    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    private final BillingRepository billing;
    private final SessionStore session;

    @Inject
    public PatientDetailViewModel(PatientRepository patients, AppointmentRepository appointments,
                                  BillingRepository billing, SessionStore session) {
        this.patients = patients;
        this.appointments = appointments;
        this.billing = billing;
        this.session = session;
    }

    public LiveData<PatientEntity> patient(String id) { return patients.observe(id); }
    public LiveData<List<AppointmentRow>> appointments(String id) { return appointments.forPatient(id); }
    public LiveData<List<InvoiceEntity>> invoices(String id) { return billing.invoices(id); }
    public String clinicName() { return session.clinicName(); }
}
