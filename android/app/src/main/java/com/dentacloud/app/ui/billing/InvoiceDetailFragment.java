package com.dentacloud.app.ui.billing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.InvoiceEntity;
import com.dentacloud.app.data.local.entity.InvoiceLineEntity;
import com.dentacloud.app.data.local.entity.PaymentEntryEntity;
import com.dentacloud.app.databinding.FragmentInvoiceDetailBinding;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.ui.common.Dialogs;
import com.dentacloud.app.ui.common.Share;
import com.dentacloud.app.util.Dates;
import com.dentacloud.app.util.Money;

import java.math.BigDecimal;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/** One invoice: lines, the payment ledger, split payments, refunds and the receipt PDF. */
@AndroidEntryPoint
public class InvoiceDetailFragment extends Fragment {
    private FragmentInvoiceDetailBinding b;
    private InvoiceViewModel vm;
    private InvoiceEntity invoice;
    private List<InvoiceLineEntity> lines = List.of();
    private List<PaymentEntryEntity> payments = List.of();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentInvoiceDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(InvoiceViewModel.class);
        String id = Args.get(getArguments(), Args.INVOICE_ID);
        b.refund.setVisibility(vm.canRefund() ? View.VISIBLE : View.GONE);

        vm.invoice(id).observe(getViewLifecycleOwner(), inv -> {
            invoice = inv;
            render();
        });
        vm.lines(id).observe(getViewLifecycleOwner(), ls -> {
            lines = ls;
            b.lines.removeAllViews();
            for (InvoiceLineEntity l : ls) {
                row(b.lines, getString(R.string.line_row, l.qty, l.description, Money.format(l.amountPaise * l.qty)));
            }
        });
        vm.payments(id).observe(getViewLifecycleOwner(), ps -> {
            payments = ps;
            b.payments.removeAllViews();
            if (ps.isEmpty()) row(b.payments, getString(R.string.no_payments));
            for (PaymentEntryEntity p : ps) {
                boolean refund = PaymentEntryEntity.REFUND.equals(p.kind);
                row(b.payments, getString(refund ? R.string.refund_row : R.string.payment_row,
                        Dates.dayTime(p.recordedAt), p.mode, Money.format(p.amountPaise)));
            }
            render();
        });
        vm.message().observe(getViewLifecycleOwner(), m -> {
            if (m == null) return;
            vm.messageShown();
            Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
            b.reference.setText("");
        });
        vm.pdf().observe(getViewLifecycleOwner(), f -> {
            if (f == null) return;
            vm.pdfShown();
            Share.pdf(requireContext(), f, getString(R.string.share_receipt));
        });

        b.pay.setOnClickListener(v -> submit(id, PaymentEntryEntity.PAYMENT));
        b.refund.setOnClickListener(v -> Dialogs.confirm(requireContext(), getString(R.string.refund),
                getString(R.string.refund_confirm, String.valueOf(b.amount.getText())),
                () -> submit(id, PaymentEntryEntity.REFUND)));
        b.receipt.setOnClickListener(v -> {
            if (invoice != null) vm.receipt(requireContext().getApplicationContext(), invoice, lines, payments);
        });
    }

    private long paid() {
        long sum = 0;
        for (PaymentEntryEntity p : payments) sum += PaymentEntryEntity.REFUND.equals(p.kind) ? -p.amountPaise : p.amountPaise;
        return sum;
    }

    private void render() {
        if (invoice == null) return;
        long paid = paid(), balance = invoice.totalPaise - paid;
        b.title.setText(getString(R.string.invoice_title, invoice.invoiceNo, Dates.dayTime(invoice.issuedAt)));
        b.status.setText(invoice.status);
        b.summary.setText(getString(R.string.invoice_summary, Money.format(invoice.subtotalPaise),
                Money.format(invoice.discountPaise), Money.format(invoice.taxPaise), Money.format(invoice.totalPaise),
                Money.format(paid), Money.format(balance)));
        if (b.amount.getText() == null || b.amount.getText().length() == 0) {
            b.amount.setText(BigDecimal.valueOf(Math.max(balance, 0), 2).toPlainString());
        }
        b.pay.setEnabled(balance > 0);
    }

    private void submit(String invoiceId, String kind) {
        long paise;
        try {
            paise = Money.parseRupees(String.valueOf(b.amount.getText()));
            b.amountLayout.setError(null);
        } catch (NumberFormatException | ArithmeticException e) {
            b.amountLayout.setError(getString(R.string.invalid_amount));
            return;
        }
        int checked = b.mode.getCheckedChipId();
        String mode = checked == R.id.mode_upi ? "UPI" : checked == R.id.mode_card ? "CARD" : checked == R.id.mode_bank ? "BANK" : "CASH";
        b.amount.setText("");
        vm.record(invoiceId, kind, mode, paise, String.valueOf(b.reference.getText()));
    }

    private void row(ViewGroup parent, String text) {
        TextView t = new TextView(requireContext());
        t.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch));
        t.setGravity(android.view.Gravity.CENTER_VERTICAL);
        t.setText(text);
        parent.addView(t);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
