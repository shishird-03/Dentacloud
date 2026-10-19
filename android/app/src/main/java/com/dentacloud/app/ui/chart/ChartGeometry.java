package com.dentacloud.app.ui.chart;

/**
 * FDI odontogram layout and hit-testing, as plain math so it is unit-testable without a View.
 * Screen left is the patient's right (dentist's view): quadrants 1/4 (5/8) left, 2/3 (6/7) right.
 */
public final class ChartGeometry {
    public static final String[] SURFACES = {"M", "O", "D", "B", "L"};
    public static final int M = 0, O = 1, D = 2, B = 3, L = 4;

    private static final int[] ADULT = {18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28,
            48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38};
    private static final int[] CHILD = {55, 54, 53, 52, 51, 61, 62, 63, 64, 65,
            85, 84, 83, 82, 81, 71, 72, 73, 74, 75};

    private ChartGeometry() {}

    /** Teeth in draw order: the upper arch left to right, then the lower arch left to right. */
    public static int[] teeth(boolean child) {
        return child ? CHILD : ADULT;
    }

    public static int perRow(boolean child) {
        return child ? 5 * 2 : 8 * 2;
    }

    public static boolean upper(int fdi) {
        int q = fdi / 10;
        return q == 1 || q == 2 || q == 5 || q == 6;
    }

    /** Quadrants drawn on the left half have their mesial side towards the screen centre (right). */
    static boolean leftHalf(int fdi) {
        int q = fdi / 10;
        return q == 1 || q == 4 || q == 5 || q == 8;
    }

    /**
     * Which surface a tap at (x, y), relative to the tooth cell (0..1 each way), lands on.
     * Centre square = occlusal/incisal; the four sides are buccal, lingual, mesial and distal.
     */
    public static int surfaceAt(int fdi, float x, float y) {
        if (x >= 0.3f && x <= 0.7f && y >= 0.3f && y <= 0.7f) return O;
        float dx = x - 0.5f, dy = y - 0.5f;
        if (Math.abs(dy) >= Math.abs(dx)) {
            boolean top = dy < 0;
            // Buccal faces the lips: outward, i.e. up for the upper arch and down for the lower.
            return top == upper(fdi) ? B : L;
        }
        boolean right = dx > 0;
        return right == leftHalf(fdi) ? M : D;
    }
}
