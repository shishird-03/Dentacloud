package com.dentacloud.app.ui.billing;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.local.entity.InvoiceEntity;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.local.entity.ProcedureEntity;
import com.dentacloud.app.data.repo.BillingRepository;
import com.dentacloud.app.data.repo.PatientRepository;
import com.dentacloud.app.util.BillMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class BillingViewModel extends ViewModel {
    private final BillingRepository billing;
    private final PatientRepository patients;
    /** Checked procedures by id, in check order (Part A #1). */
    private final Map<String, ProcedureEntity> selected = new LinkedHashMap<>();
    private final MutableLiveData<BillMath.Totals> totals = new MutableLiveData<>(BillMath.compute(List.of(), 0));
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<InvoiceEntity> issued = new MutableLiveData<>();
    private long discountPaise;

    @Inject
    public BillingViewModel(BillingRepository billing, PatientRepository patients) {
        this.billing = billing;
        this.patients = patients;
    }

    public LiveData<List<ProcedureEntity>> procedures() { return billing.procedures(); }
    public LiveData<PatientEntity> patient(String id) { return patients.observe(id); }
    public LiveData<BillMath.Totals> totals() { return totals; }
    public LiveData<String> error() { return error; }
    public LiveData<InvoiceEntity> issued() { return issued; }

    public boolean isSelected(ProcedureEntity p) {
        return selected.containsKey(p.id);
    }

    public void toggle(ProcedureEntity p, boolean checked) {
        if (checked) selected.put(p.id, p); else selected.remove(p.id);
        recompute();
    }

    public void setDiscount(long paise) {
        discountPaise = paise;
        recompute();
    }

    private void recompute() {
        List<BillMath.Line> lines = new ArrayList<>();
        for (ProcedureEntity p : selected.values()) {
            lines.add(new BillMath.Line(p.defaultPaise, 1, p.taxBasisPoints));
        }
        long subtotal = 0;
        for (BillMath.Line l : lines) subtotal += l.amountPaise() * l.qty();
        if (discountPaise > subtotal) {
            error.setValue("Discount can't exceed the subtotal");
            totals.setValue(BillMath.compute(lines, 0));
            return;
        }
        error.setValue(null);
        totals.setValue(BillMath.compute(lines, discountPaise));
    }

    public void issue(String patientId) {
        if (selected.isEmpty()) {
            error.setValue("Select at least one procedure");
            return;
        }
        if (error.getValue() != null) return;
        Map<ProcedureEntity, Integer> items = new LinkedHashMap<>();
        for (ProcedureEntity p : selected.values()) items.put(p, 1);
        billing.issueInvoice(patientId, items, discountPaise, issued::postValue);
    }
}
