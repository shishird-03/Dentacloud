package com.dentacloud.app.ui.chart;

import com.dentacloud.app.data.local.entity.ToothRecordEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the chart at a moment from the append-only history (PRD 6: "current state is derived").
 * A whole-tooth record (surface null) sets all five surfaces; a later surface record overrides one.
 */
public final class ChartState {
    private ChartState() {}

    /** fdi -> conditions per surface (index {@link ChartGeometry#M} ... {@link ChartGeometry#L}). */
    public static Map<Integer, String[]> at(List<ToothRecordEntity> records, long upTo) {
        Map<Integer, String[]> state = new HashMap<>();
        for (ToothRecordEntity r : records) { // already ordered by recordedAt
            if (r.recordedAt > upTo) break;
            String[] s = state.computeIfAbsent(r.toothFdi, k -> new String[5]);
            if (r.surface == null) {
                java.util.Arrays.fill(s, r.condition);
            } else {
                for (int i = 0; i < 5; i++) {
                    if (ChartGeometry.SURFACES[i].equals(r.surface)) s[i] = r.condition;
                }
            }
        }
        return state;
    }
}
