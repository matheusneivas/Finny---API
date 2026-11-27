# Guia da API para o app

Base: `/api/v1`. Todas as rotas, exceto as de autenticação, exigem
`Authorization: Bearer <accessToken>`. A especificação completa está em [openapi.json](openapi.json).

## Erros

Todas as falhas seguem o mesmo formato:

```json
{
  "timestamp": "2026-11-26T18:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Dados inválidos",
  "errors": { "amount": "Valor deve ser maior que zero" }
}
```

| Status | Quando |
|---|---|
| 400 | Validação (`errors` traz o campo e a mensagem), JSON mal formatado, parâmetro inválido |
| 401 | Token ausente, inválido ou expirado. Tente `/auth/refresh` uma vez; se falhar, mande para o login |
| 403 | Acesso negado |
| 404 | Recurso não existe ou não pertence ao usuário |
| 409 | Conflito (ex.: e-mail já cadastrado) |
| 422 | Regra de negócio (ex.: limite mensal do chat atingido) |
| 429 | Muitas requisições. Header `Retry-After` diz em quantos segundos tentar de novo |
| 503 | Assistente de IA indisponível (a mensagem não é descontada do limite) |

## Autenticação

| Rota | Corpo | Resposta |
|---|---|---|
| `POST /auth/register` | `{email, password, fullName}` | `AuthResponse` (201) |
| `POST /auth/login` | `{email, password}` | `AuthResponse` |
| `POST /auth/refresh` | `{refreshToken}` | `AuthResponse` com **novo** refresh token |
| `POST /auth/logout` | `{refreshToken}` | encerra a sessão |

```json
{
  "accessToken": "…",
  "refreshToken": "…",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "user": { "id": "…", "email": "…", "name": "…", "plan": "FREE", "onboardingCompleted": false }
}
```

Regras importantes:

- O access token dura 30 minutos (`expiresIn`, em segundos).
- O refresh token dura 30 dias e é de **uso único**. A cada `/auth/refresh` guarde o novo valor
  (no SecureStore) e descarte o antigo.
- Faça um refresh por vez. Se o mesmo refresh token for usado duas vezes, a API entende como
  roubo de token e encerra a sessão inteira (todos os tokens daquele login).
- Senha: 8 a 72 caracteres, com maiúscula, minúscula e número. E-mail não diferencia maiúsculas.

### Verificação de e-mail

`user.emailVerified` diz se a pessoa já confirmou o e-mail. O cadastro já envia um código de 6
dígitos (assunto: "123456 é seu código do Fini"). Até confirmar, as rotas de dados respondem
**403** com `"code": "EMAIL_NOT_VERIFIED"`. Ficam livres: `/auth/**`, `/users/profile`,
`/users/me/avatar`, `/onboarding/status` e `/notifications/token`.

| Rota | Corpo | Resposta |
|---|---|---|
| `POST /auth/email/verify` (Bearer) | `{code}` | `User` atualizado. 400 se o código estiver errado ou vencido |
| `POST /auth/email/resend` (Bearer) | | 204. 429 com `Retry-After` se pedir antes de 60 s |
| `POST /auth/password/forgot` | `{email}` | sempre 204, exista ou não a conta. 429 com `Retry-After` antes de 60 s |
| `POST /auth/password/reset` | `{email, code, newPassword}` | 204. Encerra todas as sessões; depois faça login |

- O código vale 15 minutos e aceita 5 tentativas; depois, peça outro. Só o último código enviado vale.
- No máximo 10 códigos por dia por conta e finalidade.
- Login de quem ainda não verificou envia um código novo se não houver um válido.
- Sem provedor de e-mail configurado no servidor (`MAIL_PROVIDER=none`), a verificação fica
  desligada e `emailVerified` vem `true`.

## Chat com a IA

### Enviar mensagem

`POST /chat/messages` com `{ "message": "gastei 52,50 no mercado hoje" }` (até 2000 caracteres).

```json
{
  "messageId": "…",
  "reply": "Preparei o lançamento do mercado, é só confirmar no cartão abaixo.",
  "tokensUsed": 1840,
  "proposedActions": [
    {
      "id": "6f1c…",
      "type": "CREATE_TRANSACTION",
      "summary": "Adicionar despesa de R$ 52,50 em Alimentação (Mercado) em 26/11/2026",
      "payload": {
        "amount": 52.50,
        "type": "EXPENSE",
        "categoryId": "…",
        "description": "Mercado",
        "transactionDate": "2026-11-26",
        "frequency": "ONCE",
        "repeatCount": 1
      }
    }
  ],
  "messagesUsedThisMonth": 12,
  "monthlyLimit": 100
}
```

### Cartões de confirmação

A IA **nunca grava nada sozinha**. Ela devolve `proposedActions`; o app mostra cada uma como
cartão (use `summary` como texto) com os botões Confirmar e Descartar. Ao confirmar, o app chama
o endpoint normal com o `payload`:

| `type` | Chamada ao confirmar |
|---|---|
| `CREATE_TRANSACTION` | `POST /transactions` com `payload` (é um `TransactionRequest`) |
| `UPDATE_TRANSACTION` | `PUT /transactions/{payload.transactionId}` com o restante do `payload` |
| `DELETE_TRANSACTION` | `DELETE /transactions/{payload.transactionId}` |
| `CREATE_GOAL` | `POST /goals` com `payload` (é um `GoalRequest`) |
| `CONTRIBUTE_GOAL` | `POST /goals/{payload.goalId}/contribute` com `{ "amount": payload.amount }` |

Ao confirmar, mande o cabeçalho `Idempotency-Key: <id da ação>` na chamada. Se o app repetir a
chamada (rede caiu, toque duplo, app reaberto), a API devolve o mesmo recurso criado da primeira
vez, com o mesmo status e o cabeçalho `Idempotent-Replayed: true`, sem criar outro. O cartão também
passa a `CONFIRMED` sozinho.

O servidor já validou o payload e resolveu a categoria e os ids (só do próprio usuário). Mesmo
assim, os endpoints validam de novo ao confirmar, então trate 400/404/422 normalmente.

Para `CREATE_TRANSACTION` com `frequency` diferente de `ONCE` e `repeatCount` maior que 1, a API
cria as repetições (parcelas) automaticamente.

### Estado dos cartões

Cada item de `proposedActions` traz `status`: `PENDING`, `CONFIRMED` ou `DISCARDED`.

`PUT /chat/actions/{actionId}` com `{ "status": "CONFIRMED" | "DISCARDED" }` responde 204. É
idempotente. Responde 404 para ação de outro usuário ou inexistente, 409 ao descartar algo já
confirmado (confirmado é definitivo) e 400 para `PENDING` ou valor inválido. Descartado pode virar
confirmado depois.

### Idempotency-Key

Aceito em `POST /transactions`, `POST /goals` e `POST /goals/{id}/contribute`: até 64 caracteres
(letras, números, `.`, `_`, `:`, `-`). A chave vale por usuário e fica guardada por 45 dias. A mesma
chave usada em outra operação recebe 422. Se a criação falhar (400, 404 etc.), a chave não é
gasta e pode ser reenviada.

### Histórico e uso

- `GET /chat/history?page=0&size=50`: mais recentes primeiro. Mensagens `ASSISTANT` trazem
  `proposedActions` (o app decide como mostrar cartões antigos, por exemplo já resolvidos).
- `DELETE /chat/history`: apaga a conversa. Não devolve mensagens ao limite do mês.
- `GET /chat/stats`:

```json
{
  "plan": "FREE",
  "messagesUsedThisMonth": 12,
  "monthlyLimit": 100,
  "remainingThisMonth": 88,
  "resetsAt": "2026-12-01T00:00:00-03:00",
  "totalTokensUsed": 51234
}
```

O limite conta só as mensagens enviadas pelo usuário no mês (fuso de São Paulo). Respostas da IA
não contam, e uma mensagem que falhou por indisponibilidade da IA (503) é devolvida.

## Foto de perfil

| Rota | Corpo | Resposta |
|---|---|---|
| `PUT /users/me/avatar` | `multipart/form-data`, campo `file` | `{ "avatarUrl": "/api/v1/users/me/avatar?v=…" }` |
| `GET /users/me/avatar` | | bytes da imagem (`image/jpeg`); 404 se não houver foto |
| `DELETE /users/me/avatar` | | 204 |

- Aceita JPEG, PNG e WEBP de até 2 MB (413 acima disso). O tipo é conferido pelo conteúdo do arquivo,
  não pelo `Content-Type`. O servidor re-encoda para JPEG de até 512x512 e descarta EXIF/GPS.
- `avatarUrl` também vem em `user` (login, cadastro, refresh) e no perfil (`GET /users/profile`).
  É um caminho relativo: junte com a URL base. O `?v=` muda a cada troca de foto, então pode usar
  como chave de cache.
- O `GET` exige `Authorization: Bearer`, como as outras rotas. Mande o header junto ao carregar a
  imagem (ex.: `source={{ uri, headers: { Authorization } }}`).
- No cadastro: chame `POST /auth/register` e, com o token recebido, `PUT /users/me/avatar`.
- Limite: 10 envios por hora por usuário.

## Push

- `POST /notifications/token` com `{ "token": "<expo/fcm token>", "platform": "IOS" | "ANDROID" | "WEB" }`
- `DELETE /notifications/token?token=…` (só remove tokens da própria conta)

## Onboarding

`POST /onboarding/step/1` aceita `cpf` **opcional**. Se enviado, é validado e guardado apenas como hash.

## Limites de requisição

| Rota | Limite |
|---|---|
| `POST /auth/login`, `POST /auth/register` | 10 por minuto por IP |
| `POST /auth/refresh` | 30 por minuto por IP |
| `POST /auth/email/*`, `POST /auth/password/*` | 10 por minuto por IP (além do intervalo de 60 s por código) |
| `POST /chat/messages` | 15 por minuto por usuário |
| `POST /ocr/*` | 10 por minuto por usuário |
| `PUT /users/me/avatar` | 10 por hora por usuário |
| Demais | 300 por minuto por usuário |
