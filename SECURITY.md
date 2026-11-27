# Segurança

## Medidas implementadas

**Segredos e configuração**
- Nenhum segredo com valor padrão em `application.yml`. A API não sobe sem `JWT_SECRET` de pelo
  menos 32 bytes, e recusa valores de exemplo que já estiveram públicos no repositório.
- `.env` fora do git; `.env.example` documenta todas as variáveis.
- Configuração base é a de produção. O perfil `dev` (H2, Swagger, logs detalhados) só é ativado
  explicitamente.
- Swagger/OpenAPI desligado em produção (`API_DOCS_ENABLED=false`).

**Autenticação**
- Access token JWT curto (30 min) com `typ=access`, emissor validado. Refresh tokens não são
  aceitos como access token.
- Refresh token de uso único com rotação e detecção de reuso: reusar um token já trocado revoga
  toda a sessão. Só o `jti` é guardado no banco.
- Logout revoga a sessão. Tokens vencidos são limpos diariamente.
- BCrypt custo 12; senha limitada a 72 bytes (limite do BCrypt). E-mail normalizado.
- Mensagem genérica para e-mail inexistente e senha errada.
- Verificação de e-mail e recuperação de senha com código de 6 dígitos: guardado só como HMAC,
  vale 15 min, 5 tentativas, um código válido por vez, 60 s entre envios e 10 por dia. "Esqueci a
  senha" responde igual para e-mail existente ou não, e o envio acontece fora da requisição (sem
  diferença de tempo). Trocar a senha encerra todas as sessões.

**Autorização e isolamento**
- Todas as consultas filtram pelo usuário autenticado (transações, metas, OCR, tokens de push).
- Ferramentas da IA só leem dados do próprio usuário e nunca gravam: as propostas passam pelo
  usuário e depois pelos mesmos endpoints validados.
- Rotas `/api/internal/**` (callbacks do n8n) exigem `X-Internal-API-Key` (comparação em tempo
  constante). Sem chave configurada, ficam fechadas.
- Actuator: apenas `/actuator/health` é público, sem detalhes.

**IA**
- Dados do usuário vão para o modelo marcados como dados (`<dados_do_usuario>`), com instrução
  para ignorar comandos dentro deles (proteção contra prompt injection).
- Resumo dos cartões de confirmação é montado pelo servidor, não pelo modelo.
- Limites: até 5 idas e voltas de ferramentas e 10 propostas por mensagem; limite mensal de
  mensagens por plano com contador atômico; rate limit por minuto.
- Corpo das requisições à IA não é logado (contém dados financeiros).

**Entrada e saída**
- Bean Validation em todos os DTOs; tamanho máximo de página = 100.
- Upload de imagem (OCR): até 5 MB, tipos permitidos e conferência da assinatura do arquivo.
- Foto de perfil: até 2 MB, formato decidido pela assinatura (JPEG/PNG/WEBP), dimensões conferidas
  antes de decodificar, imagem redesenhada e salva como JPEG 512x512 (remove EXIF/GPS e conteúdo
  escondido). Servida só para o dono, com `nosniff` e `Content-Security-Policy: default-src 'none'`.
- Erros nunca expõem stack trace nem mensagens internas.
- Headers: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`,
  `Cache-Control: no-store`; HSTS quando servido via HTTPS.
- CORS fechado por padrão; libere só as origens da versão web.

**Privacidade (LGPD)**
- CPF opcional e guardado apenas como HMAC.
- Logs usam id do usuário, não e-mail.

**Cadeia de suprimentos**
- Spring Boot 3.5 e dependências atualizadas; Dependabot semanal; CI roda os testes em cada PR.

## Checklist antes de ir para produção

- [ ] Gerar `JWT_SECRET`, `INTERNAL_API_KEY` e `CPF_SECRET` novos (`openssl rand -base64 48`)
- [ ] Trocar `INTERNAL_API_KEY` também no n8n
- [ ] `AI_API_KEY` própria de produção, com limite de gastos configurado no console da Anthropic
- [ ] Banco com TLS (`DB_URL_PARAMS=?sslmode=require`) e usuário sem permissão de superusuário
- [ ] Servir apenas por HTTPS; `FORWARD_HEADERS_STRATEGY=framework` atrás do proxy
- [ ] `CORS_ALLOWED_ORIGINS` só com o domínio da versão web
- [ ] Com mais de uma instância, mover o rate limit para armazenamento compartilhado (Redis)
- [ ] Backups automáticos do banco

## Pendências conhecidas

- Exclusão de conta e exportação de dados (direitos do titular na LGPD) ainda não existem.
- OCR (`/ocr/process`) ainda é um esboço: a imagem não é armazenada nem enviada ao n8n.

## Reportar uma vulnerabilidade

Não abra issue pública. Fale diretamente com o mantenedor do repositório.
