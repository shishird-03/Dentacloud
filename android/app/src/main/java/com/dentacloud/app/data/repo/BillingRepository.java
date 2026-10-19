package com.dentacloud.app.data.repo;

import androidx.lifecycle.LiveData;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.InvoiceEntity;
import com.dentacloud.app.data.local.entity.InvoiceLineEntity;
import com.dentacloud.app.data.local.entity.PaymentEntryEntity;
import com.dentacloud.app.data.local.entity.PlanItemEntity;
import com.dentacloud.app.data.local.entity.ProcedureEntity;
import com.dentacloud.app.data.sync.PendingOps;
import com.dentacloud.app.util.BillMath;
import com.dentacloud.app.util.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Price list, invoices and the append-only payment ledger (F8). */
@Singleton
public class BillingRepository {
    public static final String ENTITY = "invoice";

    /** Result of a payment attempt: the saved entry, or a user-facing error. */
    public record PaymentResult(PaymentEntryEntity entry, String error) {}

    private final AppDatabase db;
    private final SessionStore session;
    private final ExecutorService io;

    @Inject
    public BillingRepository(AppDatabase db, SessionStore session, ExecutorService io) {
        this.db = db;
        this.session = session;
        this.io = io;
    }

    public LiveData<List<ProcedureEntity>> procedures() {
        return db.procedures().all(session.clinicId());
    }

    public LiveData<List<InvoiceEntity>> invoices(String patientId) {
        return db.invoices().forPatient(patientId);
    }

    public LiveData<InvoiceEntity> invoice(String id) {
        return db.invoices().observe(id);
    }

    public LiveData<List<InvoiceLineEntity>> lines(String invoiceId) {
        return db.invoices().observeLines(invoiceId);
    }

    public LiveData<List<PaymentEntryEntity>> payments(String invoiceId) {
        return db.invoices().payments(invoiceId);
    }

    /** First-run price list for the offline demo. Real clinics get theirs from the server. */
    public void seedCatalogIfEmpty() {
        String clinicId = session.clinicId();
        io.execute(() -> {
            if (db.procedures().count(clinicId) > 0) return;
            List<ProcedureEntity> list = new ArrayList<>();
            Object[][] seed = {
                    {"CONS", "Consultation", 300_00L, 15},
                    {"SCAL", "Scaling and polishing", 1_200_00L, 30},
                    {"FILL", "Composite filling", 1_500_00L, 30},
                    {"RCT", "Root canal treatment", 6_000_00L, 60},
                    {"EXT", "Extraction", 1_000_00L, 30},
                    {"CRWN", "Crown (PFM)", 8_000_00L, 45},
                    {"IOPA", "X-ray (IOPA)", 300_00L, 10},
            };
            for (Object[] s : seed) {
                ProcedureEntity p = new ProcedureEntity();
                p.id = Ids.uuid7();
                p.clinicId = clinicId;
                p.code = (String) s[0];
                p.name = (String) s[1];
                p.defaultPaise = (Long) s[2];
                p.defaultMinutes = (Integer) s[3];
                list.add(p);
            }
            db.procedures().upsertAll(list);
        });
    }

    /** Invoice from procedures chosen on the billing screen (procedure -> qty). */
    public void issueInvoice(String patientId, Map<ProcedureEntity, Integer> items, long discountPaise,
                             Consumer<InvoiceEntity> onSaved) {
        List<InvoiceLineEntity> lines = new ArrayList<>();
        for (Map.Entry<ProcedureEntity, Integer> e : items.entrySet()) {
            InvoiceLineEntity l = new InvoiceLineEntity();
            l.description = e.getKey().name;
            l.qty = e.getValue();
            l.amountPaise = e.getKey().defaultPaise;
            l.taxBasisPoints = e.getKey().taxBasisPoints;
            lines.add(l);
        }
        io.execute(() -> onSaved.accept(issue(patientId, lines, discountPaise)));
    }

    /** Invoice from treatment plan items; items already billed are skipped. Null when nothing is left. */
    public void issueFromPlan(String patientId, List<PlanItemEntity> chosen, long discountPaise,
                              Consumer<InvoiceEntity> onSaved) {
        io.execute(() -> {
            List<String> ids = new ArrayList<>();
            for (PlanItemEntity it : chosen) ids.add(it.id);
            List<String> billed = db.clinical().billedItemIds(ids);
            List<InvoiceLineEntity> lines = new ArrayList<>();
            for (PlanItemEntity it : chosen) {
                if (billed.contains(it.id)) continue;
                ProcedureEntity proc = db.procedures().get(it.procedureId);
                InvoiceLineEntity l = new InvoiceLineEntity();
                l.planItemId = it.id;
                String name = proc == null ? "Treatment" : proc.name;
                l.description = it.toothFdi == null ? name : name + " (tooth " + it.toothFdi + ")";
                l.amountPaise = it.estPaise;
                l.taxBasisPoints = proc == null ? 0 : proc.taxBasisPoints;
                lines.add(l);
            }
            onSaved.accept(lines.isEmpty() ? null : issue(patientId, lines, discountPaise));
        });
    }

    private InvoiceEntity issue(String patientId, List<InvoiceLineEntity> lines, long discountPaise) {
        InvoiceEntity inv = new InvoiceEntity();
        inv.id = Ids.uuid7();
        List<BillMath.Line> math = new ArrayList<>();
        for (InvoiceLineEntity l : lines) {
            l.id = Ids.uuid7();
            l.invoiceId = inv.id;
            math.add(new BillMath.Line(l.amountPaise, l.qty, l.taxBasisPoints));
        }
        BillMath.Totals t = BillMath.compute(math, discountPaise);
        inv.clinicId = session.clinicId();
        inv.patientId = patientId;
        inv.subtotalPaise = t.subtotalPaise();
        inv.discountPaise = t.discountPaise();
        inv.taxPaise = t.taxPaise();
        inv.totalPaise = t.totalPaise();
        inv.issuedAt = System.currentTimeMillis();
        db.runInTransaction(() -> {
            // ponytail: device-local numbering, the server assigns the final number on push
            inv.invoiceNo = String.format(Locale.ROOT, "INV-%05d", db.invoices().countAll(inv.clinicId) + 1);
            db.invoices().upsert(inv);
            db.invoices().upsertLines(lines);
            PendingOps.enqueue(db, ENTITY, inv.id, inv, "lines", lines);
        });
        return inv;
    }

    /**
     * Appends a PAYMENT or REFUND (split payments are simply several entries). Same rules as the
     * server: no overpaying, no refunding more than was paid. Invoice status follows the ledger.
     */
    public void record(String invoiceId, String kind, String mode, long amountPaise, String reference,
                       Consumer<PaymentResult> onDone) {
        io.execute(() -> {
            PaymentResult[] out = new PaymentResult[1];
            db.runInTransaction(() -> {
                InvoiceEntity inv = db.invoices().get(invoiceId);
                long paid = db.invoices().paid(invoiceId);
                if (amountPaise <= 0) {
                    out[0] = new PaymentResult(null, "Enter an amount");
                } else if (PaymentEntryEntity.PAYMENT.equals(kind) && amountPaise > inv.totalPaise - paid) {
                    out[0] = new PaymentResult(null, "That is more than the balance due");
                } else if (PaymentEntryEntity.REFUND.equals(kind) && amountPaise > paid) {
                    out[0] = new PaymentResult(null, "Refund is more than what was paid");
                } else {
                    PaymentEntryEntity p = new PaymentEntryEntity();
                    p.id = Ids.uuid7();
                    p.clinicId = inv.clinicId;
                    p.invoiceId = invoiceId;
                    p.kind = kind;
                    p.mode = mode;
                    p.amountPaise = amountPaise;
                    p.reference = reference;
                    p.idempotencyKey = p.id;
                    p.recordedBy = session.memberId();
                    p.recordedAt = System.currentTimeMillis();
                    db.invoices().insertPayment(p);
                    long now = paid + (PaymentEntryEntity.PAYMENT.equals(kind) ? amountPaise : -amountPaise);
                    inv.status = now <= 0 ? "ISSUED" : now >= inv.totalPaise ? "PAID" : "PARTIAL";
                    db.invoices().upsert(inv); // derived locally; the server derives it the same way
                    PendingOps.enqueue(db, "payment", p.id, p);
                    out[0] = new PaymentResult(p, null);
                }
            });
            onDone.accept(out[0]);
        });
    }

    public void paid(String invoiceId, Consumer<Long> onResult) {
        io.execute(() -> onResult.accept(db.invoices().paid(invoiceId)));
    }
}
