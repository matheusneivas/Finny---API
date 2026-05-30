ALTER TABLE users ADD COLUMN cpf_hash VARCHAR(64);

CREATE UNIQUE INDEX idx_users_cpf_hash ON users(cpf_hash) WHERE cpf_hash IS NOT NULL;
