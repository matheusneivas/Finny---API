-- Defesa extra no Supabase: os papéis públicos da Data API (anon e authenticated) não podem ler
-- nada do schema da API, nem se alguém expuser o schema por engano no painel.
-- A API conecta como dona das tabelas (postgres), que não é afetada pela RLS.
-- Fora do Supabase esses papéis não existem e o bloco abaixo só liga a RLS.
DO $$
DECLARE
    t record;
    r text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = current_schema() LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', current_schema(), t.tablename);
    END LOOP;

    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA %I FROM %I', current_schema(), r);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA %I FROM %I', current_schema(), r);
            EXECUTE format('REVOKE ALL ON SCHEMA %I FROM %I', current_schema(), r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON TABLES FROM %I', current_schema(), r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON SEQUENCES FROM %I', current_schema(), r);
        END IF;
    END LOOP;
END $$;
