package com.dentacloud.app.ui.patients;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.repo.PatientRepository;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Part B #6: two-step registration ("resume builder"). Activity-scoped so both steps share one draft.
 */
@HiltViewModel
public class PatientFormViewModel extends ViewModel {
    private final PatientRepository repo;
    private final MutableLiveData<PatientEntity> draft = new MutableLiveData<>();
    private final MutableLiveData<String> duplicateWarning = new MutableLiveData<>();
    private final MutableLiveData<PatientEntity> saved = new MutableLiveData<>();
    private String editingId;

    @Inject
    public PatientFormViewModel(PatientRepository repo) {
        this.repo = repo;
    }

    public LiveData<PatientEntity> draft() { return draft; }
    public LiveData<String> duplicateWarning() { return duplicateWarning; }
    public LiveData<PatientEntity> saved() { return saved; }

    /** Called by step 1; a different id (or null for a new patient) resets the draft. */
    public void start(String patientId) {
        if (draft.getValue() != null && Objects.equals(editingId, patientId)) return;
        editingId = patientId;
        saved.setValue(null);
        duplicateWarning.setValue(null);
        if (patientId == null) {
            draft.setValue(new PatientEntity());
        } else {
            repo.load(patientId, draft::postValue);
        }
    }

    public void checkDuplicatePhone(String phone) {
        PatientEntity d = draft.getValue();
        if (d == null || phone.isBlank()) {
            duplicateWarning.setValue(null);
            return;
        }
        repo.duplicates(phone.trim(), d.id, (List<PatientEntity> dups) -> duplicateWarning.postValue(
                dups.isEmpty() ? null : dups.get(0).fullName + " (" + dups.get(0).patientNo + ")"));
    }

    public void save() {
        PatientEntity d = draft.getValue();
        if (d == null) return;
        repo.save(d, p -> {
            editingId = null;
            draft.postValue(null);
            saved.postValue(p);
        });
    }
}
