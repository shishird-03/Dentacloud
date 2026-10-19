-- PRD 10: users, tenants and access. Runs as the schema owner; the API connects as ${appRole}.
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Tenant of the current transaction; NULL when unset so policies match nothing.
CREATE FUNCTION app_clinic() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('app.clinic_id', true), '')::uuid $$;

CREATE TABLE clinic (
  id uuid PRIMARY KEY, name text NOT NULL, slug text UNIQUE NOT NULL,
  phone text, email text, address jsonb, gstin text,
  timezone text NOT NULL DEFAULT 'Asia/Kolkata', currency char(3) NOT NULL DEFAULT 'INR',
  plan_id uuid, status text NOT NULL DEFAULT 'TRIAL',
  trial_ends_at timestamptz, settings jsonb NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
  id uuid PRIMARY KEY, email citext UNIQUE, phone text UNIQUE,
  password_hash text,
  full_name text NOT NULL, mfa_enabled boolean NOT NULL DEFAULT false,
  mfa_secret_enc bytea, status text NOT NULL DEFAULT 'ACTIVE',
  last_login_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE role (
  id uuid PRIMARY KEY, clinic_id uuid REFERENCES clinic(id),   -- null = system role
  name text NOT NULL, is_system boolean NOT NULL DEFAULT false
);
CREATE TABLE role_permission (
  role_id uuid REFERENCES role(id), scope text NOT NULL,
  PRIMARY KEY (role_id, scope)
);

CREATE TABLE clinic_member (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  user_id uuid NOT NULL REFERENCES app_user(id),
  role_id uuid NOT NULL REFERENCES role(id),
  is_dentist boolean NOT NULL DEFAULT false,
  registration_no text, color text, status text NOT NULL DEFAULT 'ACTIVE',
  UNIQUE (clinic_id, user_id)
);

CREATE TABLE refresh_token (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_user(id),
  clinic_id uuid NOT NULL REFERENCES clinic(id),
  token_hash text NOT NULL UNIQUE, family_id uuid NOT NULL,
  device_name text, device_id text, fcm_token text, ip inet,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL, revoked_at timestamptz
);
CREATE INDEX ON refresh_token (family_id);

CREATE TABLE chair (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  name text NOT NULL, active boolean NOT NULL DEFAULT true
);

-- Row-level security. FORCE so the table owner is subject to it too (superusers still bypass).
ALTER TABLE clinic ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinic FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON clinic USING (id = app_clinic()) WITH CHECK (id = app_clinic());

ALTER TABLE role ENABLE ROW LEVEL SECURITY;
ALTER TABLE role FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON role USING (clinic_id IS NULL OR clinic_id = app_clinic())
  WITH CHECK (clinic_id = app_clinic());

ALTER TABLE clinic_member ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinic_member FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON clinic_member USING (clinic_id = app_clinic()) WITH CHECK (clinic_id = app_clinic());

ALTER TABLE chair ENABLE ROW LEVEL SECURITY;
ALTER TABLE chair FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON chair USING (clinic_id = app_clinic()) WITH CHECK (clinic_id = app_clinic());

-- Login runs before a tenant is known: this owner-run function is the one sanctioned cross-tenant read.
CREATE FUNCTION auth_memberships(p_user uuid)
  RETURNS TABLE (member_id uuid, clinic_id uuid, clinic_name text, role_id uuid, role_name text)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT m.id, m.clinic_id, c.name, r.id, r.name
  FROM clinic_member m JOIN clinic c ON c.id = m.clinic_id JOIN role r ON r.id = m.role_id
  WHERE m.user_id = p_user AND m.status = 'ACTIVE'
  ORDER BY c.created_at
$$;

-- System roles and scopes (PRD 3 permission matrix). Fixed ids so code and tests can refer to them.
INSERT INTO role (id, clinic_id, name, is_system) VALUES
  ('00000000-0000-7000-8000-000000000001', NULL, 'OWNER', true),
  ('00000000-0000-7000-8000-000000000002', NULL, 'DENTIST', true),
  ('00000000-0000-7000-8000-000000000003', NULL, 'RECEPTIONIST', true),
  ('00000000-0000-7000-8000-000000000004', NULL, 'ASSISTANT', true),
  ('00000000-0000-7000-8000-000000000005', NULL, 'ACCOUNTANT', true);

INSERT INTO role_permission (role_id, scope)
SELECT '00000000-0000-7000-8000-000000000001'::uuid, s FROM unnest(ARRAY[
  'clinic.admin','staff.manage','patient.read','patient.write','appointment.read','appointment.write',
  'appointment.status','chart.read','chart.write','plan.read','plan.write','prescription.read',
  'prescription.write','image.read','image.write','invoice.read','invoice.write','invoice.refund',
  'expense.write','report.read','audit.read','data.export']) s
UNION ALL
SELECT '00000000-0000-7000-8000-000000000002'::uuid, s FROM unnest(ARRAY[
  'patient.read','patient.write','appointment.read','appointment.write','appointment.status',
  'chart.read','chart.write','plan.read','plan.write','prescription.read','prescription.write',
  'image.read','image.write','invoice.read','report.read']) s
UNION ALL
SELECT '00000000-0000-7000-8000-000000000003'::uuid, s FROM unnest(ARRAY[
  'patient.read','patient.write','appointment.read','appointment.write','appointment.status',
  'plan.read','image.read','invoice.read','invoice.write','report.read']) s
UNION ALL
SELECT '00000000-0000-7000-8000-000000000004'::uuid, s FROM unnest(ARRAY[
  'patient.read','appointment.read','appointment.status','chart.read','plan.read',
  'image.read','image.write','invoice.read']) s
UNION ALL
SELECT '00000000-0000-7000-8000-000000000005'::uuid, s FROM unnest(ARRAY[
  'patient.read','appointment.read','plan.read','image.read','invoice.read','invoice.write',
  'invoice.refund','expense.write','report.read','audit.read']) s;

-- role_permission rows follow their role's visibility.
ALTER TABLE role_permission ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permission FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON role_permission
  USING (EXISTS (SELECT 1 FROM role r WHERE r.id = role_id));

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${appRole};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${appRole};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${appRole};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ${appRole};
REVOKE SELECT, INSERT, UPDATE, DELETE ON flyway_schema_history FROM ${appRole};
