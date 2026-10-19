package com.dentacloud.app.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.dentacloud.app.data.local.AppLock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AppLockTest {
    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences("app_lock", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void offByDefaultAndNeverLocksSignedOutUsers() {
        AppLock lock = new AppLock(ctx);
        assertFalse(lock.mustUnlock(0, true));
        lock.setEnabled(true);
        assertFalse(new AppLock(ctx).mustUnlock(0, false));
    }

    @Test
    public void locksOnColdStartAndAfterTimeoutInBackground() {
        new AppLock(ctx).setEnabled(true);
        AppLock lock = new AppLock(ctx); // a new process
        assertTrue(lock.mustUnlock(1_000, true));
        lock.unlocked();

        lock.onBackground(10_000);
        assertFalse("rotation or a quick camera trip", lock.mustUnlock(10_000 + AppLock.TIMEOUT_MS - 1, true));
        lock.onBackground(20_000);
        assertTrue(lock.mustUnlock(20_000 + AppLock.TIMEOUT_MS, true));
        lock.onBackground(200_000); // still locked: going to the background doesn't reset the clock
        assertTrue(lock.mustUnlock(200_001, true));
    }

    @Test
    public void enablingDoesNotLockYouOut() {
        AppLock lock = new AppLock(ctx);
        lock.setEnabled(true);
        assertFalse(lock.mustUnlock(5_000, true));
    }
}
