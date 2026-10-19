package com.dentacloud.app.ui.plans;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.PlanItemEntity;
import com.dentacloud.app.data.local.entity.ProcedureEntity;
import com.dentacloud.app.data.local.entity.TreatmentPlanEntity;
import com.dentacloud.app.data.repo.BillingRepository;
import com.dentacloud.app.data.repo.ClinicalRepository;
import com.dentacloud.app.databinding.FragmentPlanBinding;
import com.dentacloud.app.ui.chart.ChartGeometry;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.util.Money;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.lifecycle.HiltViewModel;

/** Treatment plans (F7): items per tooth with estimates, accept or defer, progress, invoice selected. */
@AndroidEntryPoint
public class PlanFragment extends Fragment {
    @HiltViewModel
    public static class PlanViewModel extends ViewModel {
        final ClinicalRepository clinical;
        final BillingRepository billing;
        final MutableLiveData<String> planId = new MutableLiveData<>();
        final LiveData<List<PlanItemEntity>> items;
        final MutableLiveData<String> invoiced = new MutableLiveData<>();

        @Inject
        public PlanViewModel(ClinicalRepository clinical, BillingRepository billing) {
            this.clinical = clinical;
            this.billing = billing;
            items = Transformations.switchMap(planId, clinical::items);
        }
    }

    private FragmentPlanBinding b;
    private PlanViewModel vm;
    private String patientId;
    private List<TreatmentPlanEntity> plans = List.of();
    private List<PlanItemEntity> items = List.of();
    private final Map<String, String> procedureNames = new HashMap<>();
    private final Set<String> selected = new HashSet<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentPlanBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(PlanViewModel.class);
        patientId = Args.get(getArguments(), Args.PATIENT_ID);

        vm.clinical.plans(patientId).observe(getViewLifecycleOwner(), list -> {
            plans = list;
            List<String> titles = new ArrayList<>();
            for (TreatmentPlanEntity p : list) titles.add(p.title == null ? getString(R.string.untitled_plan) : p.title);
            ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, titles);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            b.plans.setAdapter(a);
            int at = Math.max(0, indexOf(vm.planId.getValue()));
            if (!list.isEmpty()) {
                b.plans.setSelection(at);
                if (vm.planId.getValue() == null) vm.planId.setValue(list.get(at).id);
            }
            renderStatus();
        });
        b.plans.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String next = plans.get(pos).id;
                if (!next.equals(vm.planId.getValue())) {
                    selected.clear();
                    vm.planId.setValue(next);
                }
                renderStatus();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        vm.items.observe(getViewLifecycleOwner(), list -> {
            items = list;
            renderItems();
        });
        vm.billing.procedures().observe(getViewLifecycleOwner(), list -> {
            for (ProcedureEntity p : list) procedureNames.put(p.id, p.name);
            ArrayAdapter<ProcedureEntity> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, list);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            b.procedure.setAdapter(a);
            renderItems();
        });
        b.procedure.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                ProcedureEntity proc = (ProcedureEntity) p.getItemAtPosition(pos);
                b.price.setText(BigDecimal.valueOf(proc.defaultPaise, 2).toPlainString());
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        vm.invoiced.observe(getViewLifecycleOwner(), invoiceId -> {
            if (invoiceId == null) return;
            vm.invoiced.setValue(null);
            NavHostFragment.findNavController(this).navigate(R.id.invoiceDetailFragment, Args.of(Args.INVOICE_ID, invoiceId));
        });

        b.newPlan.setOnClickListener(v -> vm.clinical.newPlan(patientId,
                getString(R.string.plan_title_default, plans.size() + 1), p -> vm.planId.postValue(p.id)));
        b.add.setOnClickListener(v -> addItem());
        b.accept.setOnClickListener(v -> withPlan(id -> vm.clinical.setPlanStatus(id, "ACCEPTED")));
        b.done.setOnClickListener(v -> withSelection(ids -> vm.clinical.setItemStatus(vm.planId.getValue(), ids, "DONE")));
        b.defer.setOnClickListener(v -> withSelection(ids -> vm.clinical.setItemStatus(vm.planId.getValue(), ids, "DEFERRED")));
        b.invoice.setOnClickListener(v -> withSelection(ids -> {
            List<PlanItemEntity> chosen = new ArrayList<>();
            for (PlanItemEntity it : items) if (ids.contains(it.id)) chosen.add(it);
            vm.billing.issueFromPlan(patientId, chosen, 0, inv -> {
                if (inv == null) toastOnUi(R.string.already_billed);
                else vm.invoiced.postValue(inv.id);
            });
        }));
    }

    private void addItem() {
        String planId = vm.planId.getValue();
        ProcedureEntity proc = (ProcedureEntity) b.procedure.getSelectedItem();
        if (planId == null || proc == null) {
            Toast.makeText(requireContext(), R.string.create_plan_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Integer tooth = null;
        String t = String.valueOf(b.tooth.getText()).trim();
        if (!t.isEmpty()) {
            tooth = Integer.parseInt(t);
            if (!validFdi(tooth)) {
                b.tooth.setError(getString(R.string.bad_tooth));
                return;
            }
        }
        long price;
        try {
            price = com.dentacloud.app.util.Money.parseRupees(String.valueOf(b.price.getText()));
        } catch (NumberFormatException | ArithmeticException e) {
            b.price.setError(getString(R.string.invalid_amount));
            return;
        }
        vm.clinical.addItem(planId, proc.id, tooth, price);
        b.tooth.setText("");
    }

    static boolean validFdi(int fdi) {
        for (boolean child : new boolean[]{false, true}) for (int t : ChartGeometry.teeth(child)) if (t == fdi) return true;
        return false;
    }

    private void renderStatus() {
        TreatmentPlanEntity p = current();
        b.planStatus.setText(p == null ? getString(R.string.no_plans)
                : getString(R.string.plan_status, p.status.replace('_', ' '), Money.format(p.estimatePaise)));
    }

    private void renderItems() {
        b.items.removeAllViews();
        int done = 0;
        for (PlanItemEntity it : items) {
            if ("DONE".equals(it.status)) done++;
            MaterialCheckBox cb = new MaterialCheckBox(requireContext());
            String proc = procedureNames.getOrDefault(it.procedureId, "Procedure");
            String tooth = it.toothFdi == null ? "" : getString(R.string.tooth_prefix, it.toothFdi);
            cb.setText(getString(R.string.plan_item_row, tooth, proc, Money.format(it.estPaise), it.status));
            cb.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch));
            cb.setChecked(selected.contains(it.id));
            cb.setOnCheckedChangeListener((v, checked) -> {
                if (checked) selected.add(it.id); else selected.remove(it.id);
            });
            b.items.addView(cb);
        }
        renderStatus();
        if (!items.isEmpty()) b.planStatus.append(getString(R.string.plan_progress, done, items.size()));
    }

    private TreatmentPlanEntity current() {
        int i = indexOf(vm.planId.getValue());
        return i < 0 ? null : plans.get(i);
    }

    private int indexOf(String id) {
        for (int i = 0; i < plans.size(); i++) if (plans.get(i).id.equals(id)) return i;
        return -1;
    }

    private void withPlan(java.util.function.Consumer<String> action) {
        if (vm.planId.getValue() != null) action.accept(vm.planId.getValue());
    }

    private void withSelection(java.util.function.Consumer<List<String>> action) {
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.select_items_first, Toast.LENGTH_SHORT).show();
            return;
        }
        action.accept(new ArrayList<>(selected));
        selected.clear();
    }

    private void toastOnUi(int res) {
        if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
