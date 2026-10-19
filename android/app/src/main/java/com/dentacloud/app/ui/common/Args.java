package com.dentacloud.app.ui.common;

import android.os.Bundle;

/** Navigation argument keys shared across screens. */
public final class Args {
    public static final String PATIENT_ID = "patientId";
    public static final String APPOINTMENT_ID = "appointmentId";
    public static final String VISIT_ID = "visitId";
    public static final String INVOICE_ID = "invoiceId";

    private Args() {}

    public static Bundle of(String key, String value) {
        Bundle b = new Bundle();
        b.putString(key, value);
        return b;
    }

    public static Bundle of(String k1, String v1, String k2, String v2) {
        Bundle b = of(k1, v1);
        b.putString(k2, v2);
        return b;
    }

    public static String get(Bundle b, String key) {
        return b == null ? null : b.getString(key);
    }
}
