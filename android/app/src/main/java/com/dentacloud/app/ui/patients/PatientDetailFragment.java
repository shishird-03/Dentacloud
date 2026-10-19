package com.dentacloud.app.ui.patients;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.dao.AppointmentRow;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.data.local.entity.InvoiceEntity;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.databinding.FragmentPatientDetailBinding;
import com.dentacloud.app.ui.calendar.AppointmentAdapter;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.ui.common.Intents;
import com.dentacloud.app.ui.common.Photos;
import com.dentacloud.app.util.Dates;
import com.dentacloud.app.util.Money;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/** Patient profile: header, alerts, one-tap call / SMS / book / bill, history. */
@AndroidEntryPoint
public class PatientDetailFragment extends Fragment {
    private FragmentPatientDetailBinding b;
    private PatientDetailViewModel vm;
    private PatientEntity patient;
    private List<AppointmentRow> appointments = List.of();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentPatientDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(PatientDetailViewModel.class);
        String id = Args.get(getArguments(), Args.PATIENT_ID);

        AppointmentAdapter adapter = new AppointmentAdapter(true,
                row -> nav().navigate(R.id.appointmentDetailFragment, Args.of(Args.APPOINTMENT_ID, row.appt.id)), null);
        b.appointments.setAdapter(adapter);

        vm.patient(id).observe(getViewLifecycleOwner(), this::bind);
        vm.appointments(id).observe(getViewLifecycleOwner(), list -> {
            appointments = list;
            adapter.submitList(list);
        });
        vm.invoices(id).observe(getViewLifecycleOwner(), this::bindInvoices);

        b.call.setOnClickListener(v -> Intents.call(requireContext(), patient.phone));
        b.sms.setOnClickListener(v -> Intents.sms(requireContext(), patient.phone, reminderText()));
        b.book.setOnClickListener(v -> nav().navigate(R.id.bookAppointmentFragment, Args.of(Args.PATIENT_ID, id)));
        b.bill.setOnClickListener(v -> nav().navigate(R.id.billingFragment, Args.of(Args.PATIENT_ID, id)));
        b.chart.setOnClickListener(v -> nav().navigate(R.id.chartFragment, Args.of(Args.PATIENT_ID, id)));
        b.visit.setOnClickListener(v -> nav().navigate(R.id.visitFragment, Args.of(Args.PATIENT_ID, id)));
        b.plan.setOnClickListener(v -> nav().navigate(R.id.planFragment, Args.of(Args.PATIENT_ID, id)));
        b.rx.setOnClickListener(v -> nav().navigate(R.id.prescriptionFragment, Args.of(Args.PATIENT_ID, id)));
    }

    private void bind(PatientEntity p) {
        if (p == null) return;
        patient = p;
        b.name.setText(p.fullName);
        StringBuilder meta = new StringBuilder(p.patientNo);
        if (p.dob != null) meta.append(" · ").append(Period.between(LocalDate.parse(p.dob), LocalDate.now()).getYears()).append(" y");
        if (p.gender != null) meta.append(" · ").append(p.gender);
        if (p.phone != null) meta.append(" · ").append(p.phone);
        b.meta.setText(meta);
        boolean hasPhone = p.phone != null && !p.phone.isEmpty();
        b.call.setEnabled(hasPhone);
        b.sms.setEnabled(hasPhone);

        StringBuilder alerts = new StringBuilder();
        if (p.allergies != null) alerts.append(getString(R.string.allergy_alert, p.allergies));
        if (p.medicalHistory != null) {
            if (alerts.length() > 0) alerts.append('\n');
            alerts.append(getString(R.string.history_alert, p.medicalHistory));
        }
        b.alerts.setText(alerts);
        b.alerts.setVisibility(alerts.length() == 0 ? View.GONE : View.VISIBLE);
        if (p.photoPath != null) b.headerPhoto.setImageBitmap(Photos.thumbnail(p.photoPath, 720));
    }

    private void bindInvoices(List<InvoiceEntity> list) {
        b.invoices.removeAllViews();
        for (InvoiceEntity inv : list) {
            TextView t = new TextView(requireContext());
            t.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch));
            t.setGravity(android.view.Gravity.CENTER_VERTICAL);
            t.setText(getString(R.string.invoice_row, inv.invoiceNo, Dates.dayTime(inv.issuedAt), Money.format(inv.totalPaise), inv.status));
            t.setOnClickListener(v -> nav().navigate(R.id.invoiceDetailFragment, Args.of(Args.INVOICE_ID, inv.id)));
            b.invoices.addView(t);
        }
    }

    /** SMS carries clinic and time only, never clinical details. */
    private String reminderText() {
        long now = System.currentTimeMillis();
        for (int i = appointments.size() - 1; i >= 0; i--) {
            AppointmentEntity a = appointments.get(i).appt;
            if (a.startAt > now && AppointmentEntity.BOOKED.equals(a.status)) {
                return getString(R.string.sms_reminder_body, vm.clinicName(), Dates.dayTime(a.startAt));
            }
        }
        return getString(R.string.sms_generic_body, vm.clinicName());
    }

    private NavController nav() {
        return NavHostFragment.findNavController(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
