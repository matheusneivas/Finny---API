-- Telefone no cadastro (CPF já existe como cpf_hash)
ALTER TABLE users ADD COLUMN phone VARCHAR(20);

-- Assinatura no cartão (uma linha por usuário)
CREATE TABLE subscriptions (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    provider     VARCHAR(20)   NOT NULL,
    external_id  VARCHAR(64)   UNIQUE,
    status       VARCHAR(10)   NOT NULL,
    price        NUMERIC(10,2) NOT NULL,
    checkout_url VARCHAR(500),
    canceled_at  TIMESTAMP,
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NOT NULL
);

-- Quem já usou o teste grátis (HMAC do CPF, ou user:<id> para contas sem CPF)
CREATE TABLE billing_trials (
    trial_key   VARCHAR(64) PRIMARY KEY,
    user_id     UUID        NOT NULL,
    created_at  TIMESTAMP   NOT NULL
);

-- Pagamentos já creditados (idempotência dos webhooks)
CREATE TABLE billing_events (
    id           VARCHAR(100) PRIMARY KEY,
    event_type   VARCHAR(60)  NOT NULL,
    received_at  TIMESTAMP    NOT NULL
);

-- Limites do plano grátis contam lançamentos criados no mês
CREATE INDEX idx_transactions_user_created ON transactions(user_id, created_at);
