package com.dentacloud.app.ui.calendar;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.dentacloud.app.data.local.dao.AppointmentRow;
import com.dentacloud.app.databinding.ItemAppointmentBinding;
import com.dentacloud.app.ui.common.Colors;
import com.dentacloud.app.util.Dates;

import java.util.function.Consumer;

public class AppointmentAdapter extends ListAdapter<AppointmentRow, AppointmentAdapter.Holder> {
    private static final DiffUtil.ItemCallback<AppointmentRow> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull AppointmentRow a, @NonNull AppointmentRow b) { return a.appt.id.equals(b.appt.id); }
        @Override public boolean areContentsTheSame(@NonNull AppointmentRow a, @NonNull AppointmentRow b) { return a.sameContent(b); }
    };

    private boolean showDate;
    private final Consumer<AppointmentRow> onOpen;
    private final Consumer<AppointmentRow> onLongPress;

    /** onLongPress may be null. */
    public AppointmentAdapter(boolean showDate, Consumer<AppointmentRow> onOpen, Consumer<AppointmentRow> onLongPress) {
        super(DIFF);
        this.showDate = showDate;
        this.onOpen = onOpen;
        this.onLongPress = onLongPress;
    }

    /** Week view shows the day on each row; day view shows only times. */
    public void setShowDate(boolean showDate) {
        if (this.showDate == showDate) return;
        this.showDate = showDate;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemAppointmentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppointmentRow r = getItem(position);
        String when = (showDate ? Dates.dayTime(r.appt.startAt) : Dates.time(r.appt.startAt)) + " – " + Dates.time(r.appt.endAt);
        h.b.time.setText(when);
        h.b.title.setText(r.patientName);
        h.b.subtitle.setText(r.procedureName == null ? (r.appt.reason == null ? "" : r.appt.reason) : r.procedureName);
        h.b.status.setText(r.appt.status.replace('_', ' '));
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(24f);
        chip.setColor(ContextCompat.getColor(h.itemView.getContext(), Colors.status(r.appt.status)));
        h.b.status.setBackground(chip);
        h.b.label.setBackgroundColor(r.appt.colorLabel != 0 ? r.appt.colorLabel
                : ContextCompat.getColor(h.itemView.getContext(), com.dentacloud.app.R.color.primary));
        h.itemView.setOnClickListener(v -> onOpen.accept(r));
        h.itemView.setOnLongClickListener(onLongPress == null ? null : v -> {
            onLongPress.accept(r);
            return true;
        });
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemAppointmentBinding b;

        Holder(ItemAppointmentBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
