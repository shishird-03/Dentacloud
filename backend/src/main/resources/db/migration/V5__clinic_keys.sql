-- Per-clinic data keys (PRD 14): each clinic's AES-256 key, wrapped by the master key (FIELD_ENC_KEY,
-- the stand-in for KMS). Keys never change in place; rotation would add a new key version.
CREATE TABLE clinic_key (
  clinic_id uuid PRIMARY KEY REFERENCES clinic (id),
  wrapped_key bytea NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE clinic_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinic_key FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON clinic_key USING (clinic_id = app_clinic()) WITH CHECK (clinic_id = app_clinic());

REVOKE UPDATE, DELETE ON clinic_key FROM ${appRole};
