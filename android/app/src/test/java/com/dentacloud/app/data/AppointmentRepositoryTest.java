package com.dentacloud.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.repo.AppointmentRepository;
import com.dentacloud.app.util.Dates;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RunWith(RobolectricTestRunner.class)
public class AppointmentRepositoryTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private AppDatabase db;
    private AppointmentRepository repo;

    @Before
    public void setUp() {
        db = TestDb.memory();
        repo = new AppointmentRepository(db, TestDb.session(), TestDb.direct());
        PatientEntity p = new PatientEntity();
        p.id = "p1";
        p.clinicId = TestDb.CLINIC;
        p.patientNo = "P-0001";
        p.fullName = "Anita Rao";
        db.patients().upsert(p);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private String book(int hour, int minute, int minutes) {
        AppointmentEntity a = new AppointmentEntity();
        a.patientId = "p1";
        a.startAt = Dates.toMillis(LocalDateTime.of(DAY.getYear(), DAY.getMonth(), DAY.getDayOfMonth(), hour, minute));
        a.endAt = a.startAt + minutes * 60_000L;
        String[] err = {"not called"};
        repo.book(a, e -> err[0] = e);
        return err[0];
    }

    @Test
    public void preventsOverlapsButAllowsBackToBack() {
        assertNull(book(10, 0, 30));
        assertNotNull("overlap must be rejected", book(10, 15, 30));
        assertNull("10:30 starts as 10:00-10:30 ends", book(10, 30, 30));
        assertEquals(2, TestDb.value(repo.range(DAY, 1)).size());
    }

    @Test
    public void cancelledSlotCanBeRebooked() {
        assertNull(book(11, 0, 30));
        String id = TestDb.value(repo.range(DAY, 1)).get(0).appt.id;
        repo.setStatus(id, AppointmentEntity.CANCELLED);
        assertNull(book(11, 0, 30));
    }

    @Test
    public void rejectsEndBeforeStart() {
        assertNotNull(book(9, 0, 0));
    }

    @Test
    public void weekRangeIncludesLaterDays() {
        assertNull(book(9, 0, 30));
        assertEquals(1, TestDb.value(repo.range(DAY.minusDays(1), 7)).size());
        assertEquals(0, TestDb.value(repo.range(DAY.plusDays(1), 7)).size());
    }
}
