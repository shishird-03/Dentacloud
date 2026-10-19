-- PRD 10: clinical records, treatment plans, the billing ledger and prescriptions.
-- ponytail: PRD's visit.dek_id (per-clinic data keys) arrives with KMS key wrapping in M7.

CREATE TABLE visit (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  patient_id uuid NOT NULL REFERENCES patient(id), appointment_id uuid REFERENCES appointment(id),
  dentist_id uuid NOT NULL REFERENCES clinic_member(id), visited_at timestamptz NOT NULL DEFAULT now(),
  complaint_enc bytea, diagnosis_enc bytea, notes_enc bytea,
  updated_at timestamptz NOT NULL DEFAULT now(), version int NOT NULL DEFAULT 0
);
CREATE INDEX ON visit (clinic_id, patient_id, visited_at DESC);

CREATE TABLE tooth_record (                 -- append-only chart history; current state is derived
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  patient_id uuid NOT NULL REFERENCES patient(id), visit_id uuid REFERENCES visit(id),
  tooth_fdi smallint NOT NULL CHECK (tooth_fdi BETWEEN 11 AND 85),
  surface text CHECK (surface IN ('M','O','D','B','L')),
  condition text NOT NULL, note text, recorded_by uuid NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON tooth_record (clinic_id, patient_id, tooth_fdi, recorded_at DESC);

CREATE TABLE treatment_plan (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  patient_id uuid NOT NULL REFERENCES patient(id), title text,
  status text NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED','ACCEPTED','IN_PROGRESS','DONE','DEFERRED')),
  estimate_paise bigint NOT NULL DEFAULT 0, created_by uuid,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE plan_item (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  plan_id uuid NOT NULL REFERENCES treatment_plan(id),
  procedure_id uuid NOT NULL REFERENCES procedure_catalog(id), tooth_fdi smallint, surface text,
  est_paise bigint NOT NULL CHECK (est_paise >= 0),
  status text NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','DONE','DEFERRED')),
  sequence int, done_visit_id uuid REFERENCES visit(id)
);
CREATE INDEX ON plan_item (plan_id);

CREATE TABLE invoice (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  patient_id uuid NOT NULL REFERENCES patient(id), invoice_no text NOT NULL,
  subtotal_paise bigint NOT NULL, discount_paise bigint NOT NULL DEFAULT 0,
  tax_paise bigint NOT NULL DEFAULT 0, total_paise bigint NOT NULL,
  status text NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED','PARTIAL','PAID','VOID')),
  issued_at timestamptz NOT NULL DEFAULT now(), idempotency_key text,
  UNIQUE (clinic_id, invoice_no), UNIQUE (clinic_id, idempotency_key),
  CHECK (total_paise = subtotal_paise - discount_paise + tax_paise)
);
CREATE INDEX ON invoice (clinic_id, patient_id);

-- Lines carry clinic_id (unlike the PRD sketch) so RLS needs no join.
CREATE TABLE invoice_line (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  invoice_id uuid NOT NULL REFERENCES invoice(id),
  plan_item_id uuid UNIQUE REFERENCES plan_item(id),   -- a plan item is billed at most once
  description text NOT NULL, qty int NOT NULL DEFAULT 1 CHECK (qty > 0),
  amount_paise bigint NOT NULL CHECK (amount_paise >= 0), tax_basis_points int NOT NULL DEFAULT 0
);

CREATE TABLE payment_entry (                 -- never updated or deleted
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  invoice_id uuid NOT NULL REFERENCES invoice(id),
  kind text NOT NULL CHECK (kind IN ('PAYMENT','REFUND')),
  mode text NOT NULL CHECK (mode IN ('CASH','UPI','CARD','BANK')),
  amount_paise bigint NOT NULL CHECK (amount_paise > 0), reference text,
  idempotency_key text NOT NULL, recorded_by uuid NOT NULL, recorded_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (clinic_id, idempotency_key)
);
CREATE INDEX ON payment_entry (invoice_id);

CREATE TABLE prescription (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  patient_id uuid NOT NULL REFERENCES patient(id), visit_id uuid REFERENCES visit(id),
  dentist_id uuid NOT NULL REFERENCES clinic_member(id), issued_at timestamptz NOT NULL DEFAULT now(), notes text
);
CREATE TABLE prescription_item (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  prescription_id uuid NOT NULL REFERENCES prescription(id),
  drug_name text NOT NULL, dose text, frequency text, duration_days int, instructions text
);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['visit','tooth_record','treatment_plan','plan_item','invoice','invoice_line',
                           'payment_entry','prescription','prescription_item'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (clinic_id = app_clinic()) WITH CHECK (clinic_id = app_clinic())', t);
  END LOOP;
END $$;

-- Children sync with their parent: lines and items are re-sent inside the parent's payload.
CREATE TRIGGER visit_sync AFTER INSERT OR UPDATE ON visit FOR EACH ROW EXECUTE FUNCTION record_sync_change('visit');
CREATE TRIGGER tooth_record_sync AFTER INSERT ON tooth_record FOR EACH ROW EXECUTE FUNCTION record_sync_change('tooth_record');
CREATE TRIGGER treatment_plan_sync AFTER INSERT OR UPDATE ON treatment_plan FOR EACH ROW EXECUTE FUNCTION record_sync_change('treatment_plan');
CREATE TRIGGER invoice_sync AFTER INSERT OR UPDATE ON invoice FOR EACH ROW EXECUTE FUNCTION record_sync_change('invoice');
CREATE TRIGGER payment_sync AFTER INSERT ON payment_entry FOR EACH ROW EXECUTE FUNCTION record_sync_change('payment');
CREATE TRIGGER prescription_sync AFTER INSERT OR UPDATE ON prescription FOR EACH ROW EXECUTE FUNCTION record_sync_change('prescription');

-- A plan item change re-syncs its plan.
CREATE FUNCTION record_plan_item_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO sync_change (clinic_id, entity, entity_id, op) VALUES (NEW.clinic_id, 'treatment_plan', NEW.plan_id, TG_OP);
  RETURN NEW;
END $$;
CREATE TRIGGER plan_item_sync AFTER INSERT OR UPDATE ON plan_item FOR EACH ROW EXECUTE FUNCTION record_plan_item_change();

-- Append-only, enforced by the database rather than by convention (PRD 6).
REVOKE UPDATE, DELETE ON tooth_record FROM ${appRole};
REVOKE UPDATE, DELETE ON payment_entry FROM ${appRole};
REVOKE DELETE ON invoice, invoice_line FROM ${appRole};
REVOKE UPDATE ON invoice_line FROM ${appRole};
