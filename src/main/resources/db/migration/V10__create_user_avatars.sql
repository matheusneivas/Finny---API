-- Foto de perfil: guardada no banco (o disco do Render free é efêmero).
-- A imagem já chega re-encodada pelo servidor (JPEG, até 512x512, sem metadados).
ALTER TABLE users ADD COLUMN avatar_version VARCHAR(16);

CREATE TABLE user_avatars (
    user_id      UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    content      BYTEA       NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
