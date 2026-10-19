package com.dentacloud.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.repo.PatientRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class PatientDaoTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private AppDatabase db;
    private PatientRepository repo;

    @Before
    public void setUp() {
        db = TestDb.memory();
        repo = new PatientRepository(db, TestDb.session(), TestDb.direct());
        save("Anita Rao", "9845000001");
        save("Ravi Kumar", "9845000002");
        save("Meera Shetty", null);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private PatientEntity save(String name, String phone) {
        PatientEntity p = new PatientEntity();
        p.fullName = name;
        p.phone = phone;
        PatientEntity[] out = new PatientEntity[1];
        repo.save(p, s -> out[0] = s);
        return out[0];
    }

    @Test
    public void numbersPatientsAndQueuesSync() {
        List<PatientEntity> all = TestDb.value(repo.search(""));
        assertEquals(3, all.size());
        assertEquals("Anita Rao", all.get(0).fullName); // ordered by name
        assertEquals("P-0001", all.get(0).patientNo);
        assertEquals(3, (int) TestDb.value(db.pendingOps().count()));
    }

    @Test
    public void searchesByNamePhoneAndId() {
        assertEquals("Ravi Kumar", TestDb.value(repo.search("ravi")).get(0).fullName);
        assertEquals("Anita Rao", TestDb.value(repo.search("000001")).get(0).fullName);
        assertEquals("Meera Shetty", TestDb.value(repo.search("P-0003")).get(0).fullName);
        assertTrue(TestDb.value(repo.search("nobody")).isEmpty());
    }

    @Test
    public void deletedPatientsAreHiddenButKept() {
        PatientEntity ravi = TestDb.value(repo.search("ravi")).get(0);
        repo.delete(ravi.id);
        assertTrue(TestDb.value(repo.search("ravi")).isEmpty());
        assertEquals(ravi.fullName, db.patients().get(ravi.id).fullName);
    }

    @Test
    public void flagsDuplicatePhone() {
        List<PatientEntity>[] dups = new List[1];
        repo.duplicates("9845000001", "", d -> dups[0] = d);
        assertEquals(1, dups[0].size());
        PatientEntity anita = dups[0].get(0);
        repo.duplicates("9845000001", anita.id, d -> dups[0] = d);
        assertTrue("editing the same patient is not a duplicate", dups[0].isEmpty());
    }

    @Test
    public void otherClinicsAreInvisible() {
        PatientEntity other = new PatientEntity();
        other.id = "x";
        other.clinicId = "clinic-b";
        other.patientNo = "P-0001";
        other.fullName = "Anita Other";
        db.patients().upsert(other);
        assertEquals(1, TestDb.value(repo.search("anita")).size());
        assertNull(TestDb.value(repo.search("anita")).get(0).deletedAt);
    }
}
