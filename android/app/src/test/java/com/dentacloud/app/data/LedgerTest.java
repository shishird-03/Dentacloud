package com.dentacloud.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.InvoiceEntity;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.local.entity.PaymentEntryEntity;
import com.dentacloud.app.data.local.entity.PendingOpEntity;
import com.dentacloud.app.data.local.entity.PlanItemEntity;
import com.dentacloud.app.data.local.entity.ProcedureEntity;
import com.dentacloud.app.data.repo.BillingRepository;
import com.dentacloud.app.data.repo.ClinicalRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class LedgerTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private AppDatabase db;
    private BillingRepository billing;
    private ClinicalRepository clinical;
    private ProcedureEntity rct;

    @Before
    public void setUp() {
        db = TestDb.memory();
        SessionStore session = TestDb.session();
        billing = new BillingRepository(db, session, TestDb.direct());
        clinical = new ClinicalRepository(db, session, TestDb.direct());
        PatientEntity p = new PatientEntity();
        p.id = "0192f000-0000-7000-8000-000000000021";
        p.clinicId = TestDb.CLINIC;
        p.patientNo = "P-0001";
        p.fullName = "Ledger Patient";
        db.patients().upsert(p);
        billing.seedCatalogIfEmpty();
        rct = TestDb.value(billing.procedures()).stream().filter(x -> "RCT".equals(x.code)).findFirst().get();
    }

    @After
    public void tearDown() {
        db.close();
    }

    private InvoiceEntity invoice() {
        InvoiceEntity[] out = new InvoiceEntity[1];
        billing.issueInvoice("0192f000-0000-7000-8000-000000000021", Map.of(rct, 1), 0, i -> out[0] = i);
        return out[0];
    }

    private String pay(String invoiceId, String kind, long paise) {
        String[] err = {"unset"};
        billing.record(invoiceId, kind, "UPI", paise, null, r -> err[0] = r.error());
        return err[0];
    }

    @Test
    public void ledgerDerivesStatusAndRefusesBadAmounts() {
        InvoiceEntity inv = invoice();
        assertEquals(6_000_00, inv.totalPaise);

        assertNull(pay(inv.id, PaymentEntryEntity.PAYMENT, 2_000_00));
        assertEquals("PARTIAL", db.invoices().get(inv.id).status);
        assertNotNull("overpaying is refused", pay(inv.id, PaymentEntryEntity.PAYMENT, 5_000_00));
        assertNull(pay(inv.id, PaymentEntryEntity.PAYMENT, 4_000_00));
        assertEquals("PAID", db.invoices().get(inv.id).status);

        assertNotNull("refunding more than paid is refused", pay(inv.id, PaymentEntryEntity.REFUND, 7_000_00));
        assertNull(pay(inv.id, PaymentEntryEntity.REFUND, 1_000_00));
        assertEquals("PARTIAL", db.invoices().get(inv.id).status);
        assertEquals(5_000_00, db.invoices().paid(inv.id));

        long paymentOps = db.pendingOps().oldest(50).stream().filter(o -> "payment".equals(o.entity)).count();
        assertEquals("three ledger entries queued for sync", 3, paymentOps);
    }

    @Test
    public void invoicePayloadCarriesLinesForTheServer() {
        InvoiceEntity inv = invoice();
        PendingOpEntity op = db.pendingOps().oldest(10).stream().filter(o -> "invoice".equals(o.entity)).findFirst().get();
        assertTrue(op.payloadJson.contains("\"lines\":["));
        assertTrue(op.payloadJson.contains("\"patientId\":\"" + inv.patientId + "\""));
    }

    @Test
    public void planItemsAreBilledOnce() {
        String patient = "0192f000-0000-7000-8000-000000000021";
        String[] planId = {null};
        clinical.newPlan(patient, "Molar", p -> planId[0] = p.id);
        clinical.addItem(planId[0], rct.id, 36, 6_000_00);
        List<PlanItemEntity> items = db.clinical().itemsNow(planId[0]);
        assertEquals(6_000_00, db.clinical().plan(planId[0]).estimatePaise);

        InvoiceEntity[] first = new InvoiceEntity[1], second = new InvoiceEntity[1];
        billing.issueFromPlan(patient, items, 0, i -> first[0] = i);
        billing.issueFromPlan(patient, items, 0, i -> second[0] = i);
        assertNotNull(first[0]);
        assertEquals("Root canal treatment (tooth 36)", db.invoices().lines(first[0].id).get(0).description);
        assertNull("already billed items produce no second invoice", second[0]);
    }
}
