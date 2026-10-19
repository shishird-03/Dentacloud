-- PRD 8 and 10: reliable reminders without heavy infrastructure (outbox table plus worker).

CREATE TABLE outbox_event (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id), topic text NOT NULL, payload jsonb NOT NULL,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','DONE','FAILED','SKIPPED')),
  attempts int NOT NULL DEFAULT 0, last_error text,
  run_after timestamptz NOT NULL DEFAULT now(), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON outbox_event (status, run_after);

CREATE TABLE notification (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id), patient_id uuid REFERENCES patient(id),
  channel text NOT NULL CHECK (channel IN ('SMS','PUSH','EMAIL')),
  template_key text NOT NULL, status text NOT NULL, provider_ref text, error text,
  scheduled_for timestamptz, sent_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON notification (clinic_id, sent_at DESC);

-- Staff devices that receive FCM "please sync" pings.
CREATE TABLE device (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id), member_id uuid NOT NULL REFERENCES clinic_member(id),
  fcm_token text NOT NULL UNIQUE, platform text NOT NULL DEFAULT 'android', updated_at timestamptz NOT NULL DEFAULT now()
);

-- F10: patients can opt out of reminders (DLT rules require honouring opt-outs).
ALTER TABLE patient ADD COLUMN reminders_opt_out boolean NOT NULL DEFAULT false;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['outbox_event','notification','device'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (clinic_id = app_clinic()) WITH CHECK (clinic_id = app_clinic())', t);
  END LOOP;
END $$;

-- The worker serves every clinic, so claiming work is the one cross-tenant step. This owner-run
-- function hands out due events exactly once (FOR UPDATE SKIP LOCKED); each event is then
-- processed under its own clinic's tenant, with RLS back in force.
CREATE FUNCTION claim_outbox(p_limit int)
  RETURNS TABLE (id uuid, clinic_id uuid, topic text, payload jsonb, attempts int)
  LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = public
AS $$
  UPDATE outbox_event o SET status = 'PROCESSING', attempts = o.attempts + 1
  WHERE o.id IN (SELECT e.id FROM outbox_event e WHERE e.status = 'PENDING' AND e.run_after <= now()
                 ORDER BY e.run_after LIMIT p_limit FOR UPDATE SKIP LOCKED)
  RETURNING o.id, o.clinic_id, o.topic, o.payload, o.attempts
$$;
REVOKE ALL ON FUNCTION claim_outbox(int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_outbox(int) TO ${appRole};

-- Events stuck in PROCESSING (worker crashed mid-send) go back to the queue after 10 minutes.
CREATE FUNCTION release_stuck_outbox() RETURNS int
  LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = public
AS $$
  WITH r AS (UPDATE outbox_event SET status = 'PENDING'
             WHERE status = 'PROCESSING' AND run_after < now() - interval '10 minutes' RETURNING 1)
  SELECT count(*)::int FROM r
$$;
REVOKE ALL ON FUNCTION release_stuck_outbox() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION release_stuck_outbox() TO ${appRole};
