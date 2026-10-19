-- PRD 10: patients and scheduling, plus the procedure price list, mobile sync log and audit trail.

CREATE TABLE patient (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id), patient_no text NOT NULL,
  full_name text NOT NULL, dob date, gender text,
  phone_enc bytea, phone_blind_idx bytea,                  -- searchable via HMAC
  email_enc bytea, address_enc bytea,
  medical_history_enc bytea, allergies_enc bytea,          -- field-level encrypted (AES-256-GCM)
  family_head_id uuid REFERENCES patient(id),
  marketing_opt_in boolean NOT NULL DEFAULT false,
  card_color int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid, deleted_at timestamptz, version int NOT NULL DEFAULT 0,
  UNIQUE (clinic_id, patient_no)
);
CREATE INDEX ON patient (clinic_id, phone_blind_idx);
CREATE INDEX ON patient USING gin (full_name gin_trgm_ops);

CREATE TABLE procedure_catalog (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id), code text, name text NOT NULL,
  default_paise bigint NOT NULL CHECK (default_paise >= 0), default_minutes int NOT NULL DEFAULT 30,
  tax_basis_points int NOT NULL DEFAULT 0,                 -- 1800 = 18%; integer, like money
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- procedure_id replaces the PRD's type_id: the appointment type is the planned procedure.
CREATE TABLE appointment (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  patient_id uuid NOT NULL REFERENCES patient(id),
  dentist_id uuid NOT NULL REFERENCES clinic_member(id), chair_id uuid REFERENCES chair(id),
  procedure_id uuid REFERENCES procedure_catalog(id),
  start_at timestamptz NOT NULL, end_at timestamptz NOT NULL,
  status text NOT NULL DEFAULT 'BOOKED'
    CHECK (status IN ('BOOKED','ARRIVED','IN_CHAIR','DONE','NO_SHOW','CANCELLED')),
  reason text, source text, color_label int NOT NULL DEFAULT 0,
  idempotency_key text,
  version int NOT NULL DEFAULT 0, created_by uuid,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (end_at > start_at),
  UNIQUE (clinic_id, idempotency_key),
  -- Two bookings for the same dentist or chair can never overlap, whatever the app code does.
  CONSTRAINT no_dentist_overlap EXCLUDE USING gist (dentist_id WITH =, tstzrange(start_at, end_at) WITH &&)
    WHERE (status NOT IN ('CANCELLED','NO_SHOW')),
  CONSTRAINT no_chair_overlap EXCLUDE USING gist (chair_id WITH =, tstzrange(start_at, end_at) WITH &&)
    WHERE (chair_id IS NOT NULL AND status NOT IN ('CANCELLED','NO_SHOW'))
);
CREATE INDEX ON appointment (clinic_id, start_at);
CREATE INDEX ON appointment (patient_id);

-- Mobile sync: every change to a synced table appends a row; clients pull by seq.
CREATE TABLE sync_change (
  seq bigserial PRIMARY KEY, clinic_id uuid NOT NULL, entity text NOT NULL,
  entity_id uuid NOT NULL, op text NOT NULL, changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON sync_change (clinic_id, seq);

CREATE FUNCTION record_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO sync_change (clinic_id, entity, entity_id, op) VALUES (NEW.clinic_id, TG_ARGV[0], NEW.id, TG_OP);
  RETURN NEW;
END $$;

CREATE TRIGGER patient_sync AFTER INSERT OR UPDATE ON patient
  FOR EACH ROW EXECUTE FUNCTION record_sync_change('patient');
CREATE TRIGGER appointment_sync AFTER INSERT OR UPDATE ON appointment
  FOR EACH ROW EXECUTE FUNCTION record_sync_change('appointment');
CREATE TRIGGER procedure_sync AFTER INSERT OR UPDATE ON procedure_catalog
  FOR EACH ROW EXECUTE FUNCTION record_sync_change('procedure');

-- Results of pushed operations, so a retried push returns the same answer (idempotency).
CREATE TABLE sync_push_log (
  clinic_id uuid NOT NULL, idempotency_key text NOT NULL, result jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (clinic_id, idempotency_key)
);

-- Append-only, hash-chained per clinic (PRD 14). No patient field values: actions and field names only.
CREATE TABLE audit_event (
  id bigserial PRIMARY KEY, clinic_id uuid NOT NULL, actor_id uuid, action text NOT NULL,
  resource_type text NOT NULL, resource_id uuid, ip inet, device text,
  before jsonb, after jsonb, occurred_at timestamptz NOT NULL DEFAULT now(),
  prev_hash bytea, hash bytea NOT NULL
);
CREATE INDEX ON audit_event (clinic_id, id);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['patient','procedure_catalog','appointment','sync_change','sync_push_log','audit_event'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (clinic_id = app_clinic()) WITH CHECK (clinic_id = app_clinic())', t);
  END LOOP;
END $$;

REVOKE UPDATE, DELETE ON audit_event FROM ${appRole};
REVOKE UPDATE, DELETE ON sync_change FROM ${appRole};
