package com.dentacloud.app.ui.calendar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.dao.AppointmentRow;
import com.dentacloud.app.data.repo.AppointmentRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class AppointmentDetailViewModel extends ViewModel {
    private final AppointmentRepository repo;
    private final SessionStore session;

    @Inject
    public AppointmentDetailViewModel(AppointmentRepository repo, SessionStore session) {
        this.repo = repo;
        this.session = session;
    }

    public LiveData<AppointmentRow> appointment(String id) { return repo.observe(id); }
    public void setStatus(String id, String status) { repo.setStatus(id, status); }
    public String clinicName() { return session.clinicName(); }
}
