package com.dentacloud.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.dentacloud.app.data.TestDb;
import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.local.entity.ProcedureEntity;
import com.dentacloud.app.data.repo.BillingRepository;
import com.dentacloud.app.data.repo.PatientRepository;
import com.dentacloud.app.ui.billing.BillingViewModel;
import com.dentacloud.app.ui.patients.PatientListViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private AppDatabase db;
    private PatientRepository patients;
    private BillingRepository billing;

    @Before
    public void setUp() {
        db = TestDb.memory();
        SessionStore session = TestDb.session();
        patients = new PatientRepository(db, session, TestDb.direct());
        billing = new BillingRepository(db, session, TestDb.direct());
        for (String n : new String[]{"Anita Rao", "Ravi Kumar"}) {
            PatientEntity p = new PatientEntity();
            p.fullName = n;
            patients.save(p, s -> {});
        }
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void patientListFiltersAsYouType() {
        PatientListViewModel vm = new PatientListViewModel(patients);
        assertEquals(2, TestDb.value(vm.patients()).size());
        vm.search("ravi");
        List<PatientEntity> list = TestDb.value(vm.patients());
        assertEquals(1, list.size());
        assertEquals("Ravi Kumar", list.get(0).fullName);
    }

    @Test
    public void billingTotalsAndInvoice() {
        billing.seedCatalogIfEmpty();
        List<ProcedureEntity> catalog = TestDb.value(billing.procedures());
        ProcedureEntity scaling = catalog.stream().filter(p -> "SCAL".equals(p.code)).findFirst().get();
        ProcedureEntity xray = catalog.stream().filter(p -> "IOPA".equals(p.code)).findFirst().get();

        BillingViewModel vm = new BillingViewModel(billing, patients);
        vm.toggle(scaling, true);
        vm.toggle(xray, true);
        assertEquals(1_500_00, TestDb.value(vm.totals()).totalPaise());

        vm.setDiscount(2_000_00);
        assertNotNull("discount above subtotal is an error", TestDb.value(vm.error()));
        vm.setDiscount(100_00);
        assertNull(vm.error().getValue());
        assertEquals(1_400_00, TestDb.value(vm.totals()).totalPaise());

        String patientId = TestDb.value(patients.search("anita")).get(0).id;
        vm.issue(patientId);
        assertEquals("INV-00001", TestDb.value(vm.issued()).invoiceNo);
        assertEquals(2, db.invoices().lines(TestDb.value(vm.issued()).id).size());
    }
}
