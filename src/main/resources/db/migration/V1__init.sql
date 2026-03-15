-- ============================================================
-- Finny API - Schema inicial
-- ============================================================

-- ── USERS ────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255),
    plan            VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    plan_expires_at TIMESTAMP,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

-- ── USER PROFILES ─────────────────────────────────────────────
CREATE TABLE user_profiles (
    user_id                     UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

    -- Etapa 1: Informações básicas
    birth_date                  DATE,
    occupation                  VARCHAR(255),
    income_range                VARCHAR(20),

    -- Etapa 2: Renda e moradia
    pays_rent                   BOOLEAN,
    monthly_rent                NUMERIC(15, 2),

    -- Etapa 3: Situação financeira
    has_debts                   BOOLEAN,
    total_debt                  NUMERIC(15, 2),
    has_emergency_fund          BOOLEAN,
    emergency_fund_months       INTEGER,

    -- Etapa 4: Investimentos
    has_investments             BOOLEAN,
    monthly_investment_goal     NUMERIC(15, 2),

    -- Etapa 5: Objetivos e preferências
    primary_goal                VARCHAR(255),
    primary_goal_timeframe      VARCHAR(50),
    expense_control_preference  VARCHAR(20),
    knowledge_level             VARCHAR(20),
    report_frequency            VARCHAR(20),
    communication_style         VARCHAR(20),

    -- Metadata
    current_step                INTEGER      NOT NULL DEFAULT 0,
    onboarding_completed        BOOLEAN      NOT NULL DEFAULT FALSE,
    onboarding_completed_at     TIMESTAMP,
    created_at                  TIMESTAMP    NOT NULL,
    updated_at                  TIMESTAMP    NOT NULL
);

-- Coleções do perfil (ElementCollection)
CREATE TABLE user_debt_types (
    user_profile_user_id UUID NOT NULL REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    debt_types           VARCHAR(50)
);

CREATE TABLE user_investment_types (
    user_profile_user_id UUID NOT NULL REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    investment_types     VARCHAR(50)
);

CREATE TABLE user_short_term_goals (
    user_profile_user_id UUID NOT NULL REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    short_term_goals     VARCHAR(100)
);

CREATE TABLE user_long_term_goals (
    user_profile_user_id UUID NOT NULL REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    long_term_goals      VARCHAR(100)
);

CREATE TABLE user_main_categories (
    user_profile_user_id  UUID NOT NULL REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    main_expense_categories VARCHAR(50)
);

CREATE TABLE user_notification_preferences (
    user_profile_user_id    UUID NOT NULL REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    notification_preferences VARCHAR(100)
);

-- ── CATEGORIES ────────────────────────────────────────────────
CREATE TABLE categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID REFERENCES users(id) ON DELETE CASCADE, -- NULL = categoria padrão
    name       VARCHAR(100) NOT NULL,
    icon       VARCHAR(50),
    color      VARCHAR(20),
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_categories_user_id ON categories(user_id);
CREATE INDEX idx_categories_is_default ON categories(is_default);

-- ── TRANSACTIONS ──────────────────────────────────────────────
CREATE TABLE transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount           NUMERIC(15, 2) NOT NULL,
    type             VARCHAR(20)    NOT NULL,
    category_id      UUID           NOT NULL REFERENCES categories(id),
    description      VARCHAR(200),
    transaction_date DATE           NOT NULL,
    source           VARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
    ocr_image_url    VARCHAR(500),
    raw_ocr_data     TEXT,
    deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP      NOT NULL,
    updated_at       TIMESTAMP      NOT NULL
);

CREATE INDEX idx_transactions_user_id   ON transactions(user_id);
CREATE INDEX idx_transactions_date      ON transactions(transaction_date);
CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date);
CREATE INDEX idx_transactions_deleted   ON transactions(deleted);

-- ── GOALS ────────────────────────────────────────────────────
CREATE TABLE goals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(50)    NOT NULL,
    target_amount       NUMERIC(15, 2) NOT NULL,
    saved_amount        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    deadline            DATE           NOT NULL,
    related_category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    icon                VARCHAR(50),
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP      NOT NULL,
    completed_at        TIMESTAMP
);

CREATE INDEX idx_goals_user_id ON goals(user_id);
CREATE INDEX idx_goals_status  ON goals(status);

-- ── CHAT MESSAGES ─────────────────────────────────────────────
CREATE TABLE chat_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,
    content          TEXT        NOT NULL,
    n8n_workflow_id  VARCHAR(100),
    tokens_used      INTEGER,
    created_at       TIMESTAMP   NOT NULL
);

CREATE INDEX idx_chat_messages_user_id      ON chat_messages(user_id);
CREATE INDEX idx_chat_messages_user_created ON chat_messages(user_id, created_at);

-- ── OCR PROCESSING QUEUE ──────────────────────────────────────
CREATE TABLE ocr_processing_queue (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_storage_path     VARCHAR(500) NOT NULL,
    callback_token         VARCHAR(100) NOT NULL UNIQUE,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_message          TEXT,
    created_transaction_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    created_at             TIMESTAMP    NOT NULL,
    processed_at           TIMESTAMP
);

CREATE INDEX idx_ocr_queue_user_id       ON ocr_processing_queue(user_id);
CREATE INDEX idx_ocr_queue_status        ON ocr_processing_queue(status);
CREATE INDEX idx_ocr_queue_callback_token ON ocr_processing_queue(callback_token);

-- ── CATEGORIAS PADRÃO ─────────────────────────────────────────
INSERT INTO categories (id, user_id, name, icon, color, is_default, created_at) VALUES
    (gen_random_uuid(), NULL, 'Alimentação',      'utensils',       '#FF6B6B', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Transporte',       'car',            '#4ECDC4', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Moradia',          'home',           '#45B7D1', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Saúde',            'heart-pulse',    '#96CEB4', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Educação',         'book',           '#FFEAA7', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Lazer',            'gamepad',        '#DDA0DD', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Vestuário',        'shirt',          '#F0A500', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Investimentos',    'trending-up',    '#00B894', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Salário',          'banknote',       '#6C5CE7', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Freelance',        'laptop',         '#A29BFE', TRUE, NOW()),
    (gen_random_uuid(), NULL, 'Outros',           'ellipsis',       '#B2BEC3', TRUE, NOW());
