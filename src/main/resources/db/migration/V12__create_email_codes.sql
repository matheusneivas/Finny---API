-- Códigos de verificação de e-mail e de recuperação de senha (guardamos só o HMAC do código)
CREATE TABLE email_codes (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose     VARCHAR(20) NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    attempts    INTEGER     NOT NULL DEFAULT 0,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_email_codes_user_purpose ON email_codes(user_id, purpose, created_at);

-- Contas criadas antes da verificação de e-mail não ficam presas na tela de código
UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;
