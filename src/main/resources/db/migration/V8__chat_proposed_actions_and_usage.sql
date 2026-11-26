-- Ações propostas pela IA (cartões de confirmação) guardadas junto da resposta
ALTER TABLE chat_messages ADD COLUMN proposed_actions TEXT;

-- Contador mensal de mensagens enviadas pelo usuário (independe do histórico)
CREATE TABLE chat_usage (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period        VARCHAR(7)  NOT NULL,
    message_count INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uk_chat_usage_user_period UNIQUE (user_id, period)
);

-- Preenche o mês atual com as mensagens já enviadas pelos usuários (não conta respostas da IA)
INSERT INTO chat_usage (user_id, period, message_count)
SELECT user_id, to_char(created_at, 'YYYY-MM'), COUNT(*)
FROM chat_messages
WHERE role = 'USER'
  AND created_at >= date_trunc('month', now())
GROUP BY user_id, to_char(created_at, 'YYYY-MM');
