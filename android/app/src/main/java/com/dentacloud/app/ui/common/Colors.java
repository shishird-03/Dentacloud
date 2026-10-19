package com.dentacloud.app.ui.common;

import android.graphics.Color;

import androidx.annotation.ColorRes;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.AppointmentEntity;

import java.util.Random;

public final class Colors {
    private static final Random RANDOM = new Random();

    private Colors() {}

    /** Part A #3: random pastel label color for patient cards and appointment chips. */
    public static int randomLabel() {
        return Color.HSVToColor(new float[]{RANDOM.nextInt(360), 0.35f, 0.95f});
    }

    @ColorRes
    public static int status(String status) {
        switch (status) {
            case AppointmentEntity.ARRIVED: return R.color.status_arrived;
            case AppointmentEntity.IN_CHAIR: return R.color.status_in_chair;
            case AppointmentEntity.DONE: return R.color.status_done;
            case AppointmentEntity.NO_SHOW: return R.color.status_no_show;
            case AppointmentEntity.CANCELLED: return R.color.status_cancelled;
            default: return R.color.status_booked;
        }
    }
}
