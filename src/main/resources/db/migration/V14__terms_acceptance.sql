-- LGPD: guarda quando e qual versão dos Termos de Uso / Política de Privacidade a pessoa aceitou
ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMP;
ALTER TABLE users ADD COLUMN terms_version VARCHAR(20);
