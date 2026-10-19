package com.dentacloud.app.ui.chart;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.dentacloud.app.data.local.entity.ToothRecordEntity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class ChartTest {
    private static String hit(int fdi, float x, float y) {
        return ChartGeometry.SURFACES[ChartGeometry.surfaceAt(fdi, x, y)];
    }

    @Test
    public void surfacesFollowFdiOrientation() {
        // 16: upper right, drawn on the screen's left half -> mesial faces the screen centre (right).
        assertEquals("O", hit(16, 0.5f, 0.5f));
        assertEquals("B", hit(16, 0.5f, 0.05f));
        assertEquals("L", hit(16, 0.5f, 0.95f));
        assertEquals("M", hit(16, 0.95f, 0.5f));
        assertEquals("D", hit(16, 0.05f, 0.5f));
        // 36: lower left, drawn on the right half: buccal is down, mesial is left.
        assertEquals("L", hit(36, 0.5f, 0.05f));
        assertEquals("B", hit(36, 0.5f, 0.95f));
        assertEquals("M", hit(36, 0.05f, 0.5f));
        assertEquals("D", hit(36, 0.95f, 0.5f));
    }

    @Test
    public void dentitionsHaveTheRightTeeth() {
        assertEquals(32, ChartGeometry.teeth(false).length);
        assertEquals(20, ChartGeometry.teeth(true).length);
        assertEquals(18, ChartGeometry.teeth(false)[0]);
        assertEquals(55, ChartGeometry.teeth(true)[0]);
    }

    private static ToothRecordEntity rec(int fdi, String surface, String condition, long at) {
        ToothRecordEntity r = new ToothRecordEntity();
        r.toothFdi = fdi;
        r.surface = surface;
        r.condition = condition;
        r.recordedAt = at;
        return r;
    }

    @Test
    public void stateIsDerivedFromHistory() {
        List<ToothRecordEntity> history = List.of(
                rec(36, "O", "CARIES", 100),
                rec(36, "O", "FILLED", 200),
                rec(46, null, "CROWN", 300),
                rec(46, "M", "CARIES", 400));
        assertEquals("CARIES", ChartState.at(history, 150).get(36)[ChartGeometry.O]);
        assertEquals("FILLED", ChartState.at(history, Long.MAX_VALUE).get(36)[ChartGeometry.O]);
        assertNull(ChartState.at(history, 150).get(46));
        assertArrayEquals(new String[]{"CARIES", "CROWN", "CROWN", "CROWN", "CROWN"},
                ChartState.at(history, Long.MAX_VALUE).get(46));
    }

    @Test
    public void tappingTheViewReportsToothAndSurface() {
        ToothChartView view = new ToothChartView(ApplicationProvider.getApplicationContext());
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1080, 600);
        int[] tooth = {0};
        String[] surface = {null};
        view.setOnSurfaceTapListener((fdi, s) -> {
            tooth[0] = fdi;
            surface[0] = s;
        });

        float[] c = view.centerOf(36);
        assertNotNull(c);
        view.tap(c[0], c[1]);
        assertEquals(36, tooth[0]);
        assertEquals("O", surface[0]);

        view.setChild(true);
        float[] k = view.centerOf(55);
        view.tap(k[0], k[1]);
        assertEquals(55, tooth[0]);

        view.setState(Map.of(55, new String[]{"CARIES", null, null, null, null})); // recolor must not throw
    }
}
