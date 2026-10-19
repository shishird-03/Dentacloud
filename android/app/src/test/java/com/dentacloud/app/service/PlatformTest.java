package com.dentacloud.app.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.dentacloud.app.data.TestDb;
import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.receiver.BootReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class PlatformTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private Context ctx;
    private AppDatabase db;
    private SessionStore session;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        shadowOf((Application) ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Reminders.createChannels(ctx);
        db = TestDb.memory();
        session = TestDb.session();
        PatientEntity p = new PatientEntity();
        p.id = "patient-1";
        p.clinicId = TestDb.CLINIC;
        p.patientNo = "P-0001";
        p.fullName = "Private Name";
        db.patients().upsert(p);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void appt(String id, long start, String status) {
        AppointmentEntity a = new AppointmentEntity();
        a.id = id;
        a.clinicId = TestDb.CLINIC;
        a.patientId = "patient-1";
        a.dentistId = TestDb.DENTIST;
        a.startAt = start;
        a.endAt = start + 1_800_000;
        a.status = status;
        db.appointments().upsert(a);
    }

    @Test
    public void remindsOnceForAppointmentsStartingSoonWithoutPatientNames() {
        long now = System.currentTimeMillis();
        appt("soon", now + 10 * 60_000, AppointmentEntity.BOOKED);
        appt("later", now + 3 * 3_600_000, AppointmentEntity.BOOKED);
        appt("cancelled", now + 5 * 60_000, AppointmentEntity.CANCELLED);

        assertEquals(1, Reminders.check(ctx, db, session, now));
        assertEquals("same appointment is not reminded twice", 0, Reminders.check(ctx, db, session, now + 60_000));

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        List<Notification> posted = shadowOf(nm).getAllNotifications();
        assertEquals(1, posted.size());
        String text = String.valueOf(posted.get(0).extras.getCharSequence(Notification.EXTRA_TEXT))
                + posted.get(0).extras.getCharSequence(Notification.EXTRA_TITLE);
        assertFalse("no PHI in notifications", text.contains("Private Name"));
        assertTrue("tapping opens the appointment", posted.get(0).contentIntent != null);
    }

    @Test
    public void batteryIsLowOnlyWhenDrainingBelowTwentyPercent() {
        assertTrue(com.dentacloud.app.receiver.BatteryReceiverTestAccess.isLow(battery(15, 0)));
        assertFalse("charging is fine", com.dentacloud.app.receiver.BatteryReceiverTestAccess.isLow(battery(15, BatteryManager.BATTERY_PLUGGED_AC)));
        assertFalse(com.dentacloud.app.receiver.BatteryReceiverTestAccess.isLow(battery(60, 0)));
    }

    private static Intent battery(int level, int plugged) {
        return new Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, level)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_PLUGGED, plugged);
    }

    @Test
    public void bootReschedulesRemindersWhenEnabled() throws Exception {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx);
        Reminders.setEnabled(ctx, true);
        new BootReceiver().onReceive(ctx, new Intent(Intent.ACTION_BOOT_COMPLETED));
        List<WorkInfo> work = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(ReminderWorker.NAME).get();
        assertEquals(1, work.size());
        assertEquals(WorkInfo.State.ENQUEUED, work.get(0).getState());
    }
}
