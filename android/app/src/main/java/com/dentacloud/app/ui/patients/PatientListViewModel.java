package com.dentacloud.app.ui.patients;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.repo.PatientRepository;
import com.dentacloud.app.ui.common.Colors;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class PatientListViewModel extends ViewModel {
    private final PatientRepository repo;
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final LiveData<List<PatientEntity>> patients;

    @Inject
    public PatientListViewModel(PatientRepository repo) {
        this.repo = repo;
        patients = Transformations.switchMap(query, repo::search);
    }

    public LiveData<List<PatientEntity>> patients() { return patients; }

    public void search(String q) {
        if (!q.equals(query.getValue())) query.setValue(q);
    }

    public void randomColor(PatientEntity p) {
        repo.setColor(p.id, Colors.randomLabel());
    }

    public void delete(PatientEntity p) {
        repo.delete(p.id);
    }
}
