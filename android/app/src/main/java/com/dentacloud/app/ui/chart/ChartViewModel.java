package com.dentacloud.app.ui.chart;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.local.entity.ToothRecordEntity;
import com.dentacloud.app.data.repo.ClinicalRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ChartViewModel extends ViewModel {
    private final ClinicalRepository clinical;

    @Inject
    public ChartViewModel(ClinicalRepository clinical) {
        this.clinical = clinical;
    }

    public LiveData<List<ToothRecordEntity>> chart(String patientId) {
        return clinical.chart(patientId);
    }

    public void add(String patientId, String visitId, int tooth, String surface, String condition) {
        clinical.addRecord(patientId, visitId, tooth, surface, condition, null);
    }
}
