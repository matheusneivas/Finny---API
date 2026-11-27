-- Estado de cada cartão de confirmação do chat (sobrevive a reinstalar o app ou trocar de aparelho)
CREATE TABLE chat_actions (
    id          VARCHAR(64) PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_id  UUID        NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    status      VARCHAR(16) NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_chat_actions_message ON chat_actions(message_id);

-- Cartões já existentes começam como pendentes
INSERT INTO chat_actions (id, user_id, message_id, status, updated_at)
SELECT DISTINCT ON (a.elem->>'id') a.elem->>'id', m.user_id, m.id, 'PENDING', now()
FROM chat_messages m
CROSS JOIN LATERAL json_array_elements(m.proposed_actions::json) AS a(elem)
WHERE m.proposed_actions IS NOT NULL
  AND m.proposed_actions LIKE '[%'
  AND a.elem->>'id' IS NOT NULL
  AND length(a.elem->>'id') <= 64;

-- Idempotency-Key: repetir a mesma criação devolve o resultado da primeira vez
CREATE TABLE idempotency_keys (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idem_key      VARCHAR(64)  NOT NULL,
    scope         VARCHAR(120) NOT NULL,
    status_code   INTEGER      NOT NULL,
    response_body TEXT         NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_idempotency_user_key UNIQUE (user_id, idem_key)
);
CREATE INDEX idx_idempotency_created ON idempotency_keys(created_at);
