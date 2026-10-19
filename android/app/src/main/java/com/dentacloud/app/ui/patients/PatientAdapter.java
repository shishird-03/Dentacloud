package com.dentacloud.app.ui.patients;

import android.content.res.ColorStateList;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.dentacloud.app.R;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.databinding.ItemPatientBinding;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PatientAdapter extends ListAdapter<PatientEntity, PatientAdapter.Holder> {
    private static final DiffUtil.ItemCallback<PatientEntity> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull PatientEntity a, @NonNull PatientEntity b) { return a.id.equals(b.id); }
        @Override public boolean areContentsTheSame(@NonNull PatientEntity a, @NonNull PatientEntity b) { return a.sameContent(b); }
    };

    private final Consumer<PatientEntity> onOpen;
    private final Consumer<PatientEntity> onCall;
    private final BiConsumer<ContextMenu, PatientEntity> onContextMenu;

    public PatientAdapter(Consumer<PatientEntity> onOpen, Consumer<PatientEntity> onCall,
                          BiConsumer<ContextMenu, PatientEntity> onContextMenu) {
        super(DIFF);
        this.onOpen = onOpen;
        this.onCall = onCall;
        this.onContextMenu = onContextMenu;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPatientBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        PatientEntity p = getItem(position);
        h.b.name.setText(p.fullName);
        h.b.meta.setText(p.phone == null || p.phone.isEmpty() ? p.patientNo : p.patientNo + " · " + p.phone);
        boolean allergic = p.allergies != null && !p.allergies.isBlank();
        h.b.allergy.setVisibility(allergic ? View.VISIBLE : View.GONE);
        if (allergic) h.b.allergy.setText(h.itemView.getContext().getString(R.string.allergy_alert, p.allergies));
        h.b.card.setCardBackgroundColor(p.cardColor == 0 ? null : ColorStateList.valueOf(p.cardColor));
        h.b.call.setVisibility(p.phone == null || p.phone.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        h.b.call.setOnClickListener(v -> onCall.accept(p));
        h.itemView.setOnClickListener(v -> onOpen.accept(p));
        h.itemView.setOnCreateContextMenuListener((menu, v, info) -> onContextMenu.accept(menu, p));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemPatientBinding b;

        Holder(ItemPatientBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
