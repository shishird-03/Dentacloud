package com.dentacloud.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.dentacloud.app.data.local.AppDatabase;
import com.dentacloud.app.data.local.SessionStore;
import com.dentacloud.app.data.local.TokenStore;
import com.dentacloud.app.data.local.entity.AppointmentEntity;
import com.dentacloud.app.data.local.entity.PatientEntity;
import com.dentacloud.app.data.local.entity.PendingOpEntity;
import com.dentacloud.app.data.remote.api.SyncControllerApi;
import com.dentacloud.app.data.repo.AppointmentRepository;
import com.dentacloud.app.data.repo.PatientRepository;
import com.dentacloud.app.data.sync.SyncEngine;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@RunWith(RobolectricTestRunner.class)
public class SyncEngineTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private static final String P1 = "0192f000-0000-7000-8000-000000000011";
    private MockWebServer server;
    private AppDatabase db;
    private SessionStore session;
    private SyncEngine engine;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        Gson gson = new Gson();
        SyncControllerApi api = new Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create(gson)).build().create(SyncControllerApi.class);
        db = TestDb.memory();
        session = TestDb.session();
        Context ctx = ApplicationProvider.getApplicationContext();
        TokenStore tokens = new TokenStore(ctx.getSharedPreferences("test-tokens", Context.MODE_PRIVATE));
        tokens.save("access", "refresh");
        engine = new SyncEngine(db, api, session, tokens, gson);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        server.shutdown();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    public void pushesQueueThenPullsServerState() throws Exception {
        PatientRepository patients = new PatientRepository(db, session, TestDb.direct());
        PatientEntity p = new PatientEntity();
        p.fullName = "Anita Rao";
        p.photoPath = "/photos/anita.jpg";
        patients.save(p, s -> {});
        PendingOpEntity op = db.pendingOps().oldest(1).get(0);

        server.enqueue(json("{\"results\":[{\"idempotencyKey\":\"" + op.idempotencyKey + "\",\"status\":\"APPLIED\"}]}"));
        server.enqueue(json("{\"changes\":["
                + "{\"seq\":5,\"entity\":\"patient\",\"id\":\"" + p.id + "\",\"data\":{\"id\":\"" + p.id + "\",\"clinicId\":\""
                + TestDb.CLINIC + "\",\"patientNo\":\"P-0007\",\"fullName\":\"Anita Rao\",\"createdAt\":1760000000000,"
                + "\"updatedAt\":1760000000123,\"version\":3}},"
                + "{\"seq\":6,\"entity\":\"procedure\",\"id\":\"0192f000-0000-7000-8000-00000000aaaa\",\"data\":{"
                + "\"id\":\"0192f000-0000-7000-8000-00000000aaaa\",\"clinicId\":\"" + TestDb.CLINIC + "\",\"code\":\"RCT\","
                + "\"name\":\"Root canal\",\"defaultPaise\":600000,\"defaultMinutes\":60,\"taxBasisPoints\":0}}"
                + "],\"nextSince\":6,\"hasMore\":false}"));

        assertEquals(SyncEngine.Outcome.SYNCED, engine.run());

        RecordedRequest push = server.takeRequest();
        assertEquals("/api/v1/sync/push", push.getPath());
        String body = push.getBody().readUtf8();
        assertTrue(body.contains("\"entityId\":\"" + p.id + "\""));
        assertTrue("epoch millis stay integers", body.contains("\"createdAt\":" + p.createdAt));
        assertEquals("/api/v1/sync/pull?since=0&limit=500", server.takeRequest().getPath());

        assertEquals(0, (int) TestDb.value(db.pendingOps().count()));
        PatientEntity stored = db.patients().get(p.id);
        assertEquals("server numbering wins", "P-0007", stored.patientNo);
        assertEquals("photo stays on the device", "/photos/anita.jpg", stored.photoPath);
        assertEquals(1760000000123L, stored.updatedAt);
        assertNotNull(db.procedures().get("0192f000-0000-7000-8000-00000000aaaa"));
        assertEquals(6, db.syncState().get(TestDb.CLINIC).lastSeq);
    }

    @Test
    public void lostSlotBecomesCancelledAndRejectedOpsNeedAttention() throws Exception {
        PatientEntity p = new PatientEntity();
        p.id = P1;
        p.clinicId = TestDb.CLINIC;
        p.patientNo = "P-0001";
        p.fullName = "Ravi";
        db.patients().upsert(p);
        AppointmentRepository appts = new AppointmentRepository(db, session, TestDb.direct());
        AppointmentEntity a = new AppointmentEntity();
        a.patientId = P1;
        a.startAt = 1_900_000_000_000L;
        a.endAt = a.startAt + 1_800_000;
        appts.book(a, err -> {});
        new PatientRepository(db, session, TestDb.direct()).setColor(P1, 7);

        var ops = db.pendingOps().oldest(10);
        server.enqueue(json("{\"results\":["
                + "{\"idempotencyKey\":\"" + ops.get(0).idempotencyKey + "\",\"status\":\"CONFLICT\",\"code\":\"slot_taken\"},"
                + "{\"idempotencyKey\":\"" + ops.get(1).idempotencyKey + "\",\"status\":\"REJECTED\",\"code\":\"forbidden\",\"message\":\"nope\"}]}"));
        server.enqueue(json("{\"changes\":[],\"nextSince\":0,\"hasMore\":false}"));

        engine.run();

        assertEquals(AppointmentEntity.CANCELLED, db.appointments().get(a.id).status);
        assertEquals(1, (int) TestDb.value(db.pendingOps().failedCount()));
        assertTrue("failed ops are not re-sent", db.pendingOps().pushable(10).isEmpty());
    }

    @Test
    public void demoClinicNeverSyncs() throws Exception {
        session.save("u", "Demo", SessionStore.DEMO_CLINIC_ID, "Demo", "OWNER", "u", java.util.Set.of());
        assertEquals(SyncEngine.Outcome.SKIPPED, engine.run());
        assertEquals(0, server.getRequestCount());
    }
}
