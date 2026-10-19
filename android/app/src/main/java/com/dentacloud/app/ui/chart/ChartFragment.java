package com.dentacloud.app.ui.chart;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.ToothRecordEntity;
import com.dentacloud.app.databinding.FragmentChartBinding;
import com.dentacloud.app.ui.common.Args;
import com.dentacloud.app.util.Dates;
import com.google.android.material.chip.Chip;

import java.util.List;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;

/** Dental chart (F5): tap tooth, then surface, then condition. The slider replays history. */
@AndroidEntryPoint
public class ChartFragment extends Fragment {
    /** Conditions of the whole tooth; the rest apply to the tapped surface. */
    private static final Set<String> WHOLE_TOOTH = Set.of("SOUND", "MISSING", "CROWN", "RCT", "EXTRACT_PLANNED", "IMPLANT", "BRIDGE");
    private static final String[] CONDITIONS = {"CARIES", "FILLED", "FRACTURED", "RCT", "CROWN", "EXTRACT_PLANNED",
            "MISSING", "IMPLANT", "BRIDGE", "SOUND"};

    private FragmentChartBinding b;
    private ChartViewModel vm;
    private List<ToothRecordEntity> records = List.of();
    private int tooth = -1;
    private String surface;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        b = FragmentChartBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        vm = new ViewModelProvider(this).get(ChartViewModel.class);
        String patientId = Args.get(getArguments(), Args.PATIENT_ID);
        String visitId = Args.get(getArguments(), Args.VISIT_ID);

        for (String c : CONDITIONS) {
            Chip chip = new Chip(requireContext());
            chip.setText(label(c));
            chip.setOnClickListener(v -> record(patientId, visitId, c));
            b.conditions.addView(chip);
        }
        b.chart.setOnSurfaceTapListener((fdi, s) -> {
            tooth = fdi;
            surface = s;
            b.selection.setText(getString(R.string.tooth_surface, fdi, s));
            showToothHistory();
        });
        b.dentition.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) b.chart.setChild(id == R.id.child);
        });
        b.history.addOnChangeListener((s, value, fromUser) -> render());

        vm.chart(patientId).observe(getViewLifecycleOwner(), list -> {
            records = list;
            render();
            showToothHistory();
        });
    }

    private void record(String patientId, String visitId, String condition) {
        if (tooth < 0) {
            b.selection.setText(R.string.tap_a_tooth_first);
            return;
        }
        vm.add(patientId, visitId, tooth, WHOLE_TOOTH.contains(condition) ? null : surface, condition);
        b.history.setValue(1f); // back to "now" so the change is visible
    }

    /** The slider maps 0..1 onto the span from the first record to now. */
    private void render() {
        long now = System.currentTimeMillis();
        long first = records.isEmpty() ? now : records.get(0).recordedAt;
        long upTo = first + (long) ((now - first) * b.history.getValue());
        if (b.history.getValue() >= 1f) upTo = Long.MAX_VALUE;
        b.chart.setState(ChartState.at(records, upTo));
        b.asOf.setText(upTo == Long.MAX_VALUE ? getString(R.string.chart_now) : getString(R.string.chart_as_of, Dates.dayTime(upTo)));
    }

    private void showToothHistory() {
        b.toothHistory.removeAllViews();
        if (tooth < 0) return;
        for (int i = records.size() - 1; i >= 0; i--) {
            ToothRecordEntity r = records.get(i);
            if (r.toothFdi != tooth) continue;
            TextView t = new TextView(requireContext());
            t.setPadding(0, 8, 0, 8);
            t.setText(getString(R.string.tooth_history_row, Dates.dayTime(r.recordedAt),
                    r.surface == null ? getString(R.string.whole_tooth) : r.surface, label(r.condition)));
            b.toothHistory.addView(t);
        }
    }

    private static String label(String condition) {
        String s = condition.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
