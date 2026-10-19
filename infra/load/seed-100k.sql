-- PRD 13: patient search under 200 ms with 100,000 patients in one clinic.
-- Usage: psql -v clinic=<clinic uuid> -f seed-100k.sql   (as the owner; RLS doesn't apply)
-- Dummy data only. Names are plain (searchable); phones get a random blind index and no ciphertext,
-- which is all search needs. Numbers continue the clinic's P-n sequence.
WITH first(n) AS (SELECT unnest(ARRAY['Asha','Bala','Chitra','Deepak','Esha','Farhan','Gita','Hari','Indu','Jai',
       'Kavya','Lokesh','Meera','Naveen','Oviya','Pranav','Ravi','Sana','Tara','Uday','Vani','Wasim','Yash','Zoya']) n),
     last(n) AS (SELECT unnest(ARRAY['Rao','Iyer','Kumar','Reddy','Nair','Shetty','Gowda','Khan','Menon','Pillai',
       'Sharma','Verma','Das','Bose','Patil','Joshi','Hegde','Naidu','Singh','Mehta']) n),
     names AS (SELECT row_number() OVER () AS k, f.n || ' ' || l.n AS name FROM first f CROSS JOIN last l),
     base AS (SELECT coalesce(max(cast(substring(patient_no FROM 3) AS int)), 0) AS start
              FROM patient WHERE clinic_id = :'clinic'::uuid AND patient_no ~ '^P-[0-9]+$')
INSERT INTO patient (id, clinic_id, patient_no, full_name, phone_blind_idx)
SELECT gen_random_uuid(), :'clinic'::uuid, 'P-' || lpad((base.start + g)::text, 6, '0'),
       names.name, decode(md5(random()::text), 'hex')
FROM generate_series(1, 100000) g
CROSS JOIN base
JOIN names ON names.k = 1 + (g % (SELECT count(*) FROM names));

ANALYZE patient;
