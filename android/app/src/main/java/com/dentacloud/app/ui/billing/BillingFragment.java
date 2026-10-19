package com.dentacloud.app.ui.billing;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.ProcedureEntity;
import com.dentacloud.app.databinding.FragmentBillingBinding;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.util.BillMath;
import com.dentacloud.app.util.Calculator;
import com.dentacloud.app.util.Money;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.math.BigDecimal;

import dagger.hilt.android.AndroidEntryPoint;

/** Billing: procedure checkboxes (Part A #1), discount, totals, invoice, and the calculator (Part A #2). */
@AndroidEntryPoint
public class BillingFragment extends Fragment {
    private FragmentBillingBinding b;
    private BillingViewModel vm;
    private BigDecimal calcResult;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentBillingBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(BillingViewModel.class);
        String patientId = Args.get(getArguments(), Args.PATIENT_ID);

        vm.patient(patientId).observe(getViewLifecycleOwner(), p -> b.patient.setText(p == null ? "" : p.fullName));
        vm.procedures().observe(getViewLifecycleOwner(), list -> {
            b.checks.removeAllViews();
            for (ProcedureEntity p : list) {
                MaterialCheckBox cb = new MaterialCheckBox(requireContext());
                cb.setText(getString(R.string.procedure_price, p.name, Money.format(p.defaultPaise)));
                cb.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch));
                cb.setChecked(vm.isSelected(p));
                cb.setOnCheckedChangeListener((v, checked) -> vm.toggle(p, checked));
                b.checks.addView(cb);
            }
        });
        vm.totals().observe(getViewLifecycleOwner(), this::showTotals);
        vm.error().observe(getViewLifecycleOwner(), err -> {
            b.error.setText(err);
            b.error.setVisibility(err == null ? View.GONE : View.VISIBLE);
        });
        vm.issued().observe(getViewLifecycleOwner(), inv -> {
            if (inv == null) return;
            Toast.makeText(requireContext(), getString(R.string.invoice_issued, inv.invoiceNo, Money.format(inv.totalPaise)),
                    Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).navigate(R.id.invoiceDetailFragment, Args.of(Args.INVOICE_ID, inv.id),
                    new androidx.navigation.NavOptions.Builder().setPopUpTo(R.id.billingFragment, true).build());
        });

        b.showTotal.setOnClickListener(v -> Toast.makeText(requireContext(),
                getString(R.string.estimated_total, Money.format(vm.totals().getValue().totalPaise())), Toast.LENGTH_SHORT).show());
        b.discount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    vm.setDiscount(Money.parseRupees(s.toString()));
                    b.discountLayout.setError(null);
                } catch (NumberFormatException | ArithmeticException e) {
                    b.discountLayout.setError(getString(R.string.invalid_amount));
                }
            }
        });
        b.issue.setOnClickListener(v -> vm.issue(patientId));

        b.opAdd.setOnClickListener(v -> calc(Calculator.Op.ADD));
        b.opSub.setOnClickListener(v -> calc(Calculator.Op.SUB));
        b.opMul.setOnClickListener(v -> calc(Calculator.Op.MUL));
        b.opDiv.setOnClickListener(v -> calc(Calculator.Op.DIV));
        b.opClear.setOnClickListener(v -> {
            b.num1.setText("");
            b.num2.setText("");
            b.calcResult.setText("");
            calcResult = null;
            b.useAsDiscount.setEnabled(false);
        });
        b.useAsDiscount.setOnClickListener(v -> {
            if (calcResult != null) b.discount.setText(calcResult.toPlainString());
        });
    }

    private void showTotals(BillMath.Totals t) {
        b.summary.setText(getString(R.string.bill_summary, Money.format(t.subtotalPaise()), Money.format(t.discountPaise()),
                Money.format(t.taxPaise()), Money.format(t.totalPaise())));
        if (b.num1.getText().length() == 0 && t.subtotalPaise() > 0) {
            b.num1.setText(BigDecimal.valueOf(t.subtotalPaise(), 2).toPlainString());
        }
    }

    private void calc(Calculator.Op op) {
        try {
            calcResult = Calculator.apply(number(b.num1), number(b.num2), op);
            b.calcResult.setText(getString(R.string.calc_result, calcResult.toPlainString()));
            b.useAsDiscount.setEnabled(calcResult.signum() >= 0);
        } catch (NumberFormatException e) {
            b.calcResult.setText(R.string.invalid_amount);
        } catch (ArithmeticException e) {
            b.calcResult.setText(e.getMessage());
        }
    }

    private static BigDecimal number(EditText e) {
        String s = e.getText().toString().trim();
        return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
