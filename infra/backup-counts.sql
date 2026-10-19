-- Restore fingerprint: row count per table, plus a digest of the audit hash chain.
-- Runs as the owner (superuser in the postgres image), so RLS doesn't hide rows.
SELECT table_name || '=' || (xpath('/row/c/text()',
         query_to_xml(format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;
SELECT 'audit_chain=' || coalesce(md5(string_agg(encode(hash, 'hex'), '' ORDER BY id)), 'empty') FROM audit_event;
