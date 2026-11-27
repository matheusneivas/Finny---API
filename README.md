# Fini API

Backend do **Fini**, assistente financeiro com IA: planilha de receitas e despesas do mês,
metas de economia (carro, casa, viagem) e um chat com IA que sugere lançamentos e metas
para o usuário confirmar no app.

## Stack

- Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA
- PostgreSQL + Flyway (migrations em `src/main/resources/db/migration`)
- JWT com refresh token rotativo
- IA: Claude (Anthropic, SDK oficial Java), modelo padrão `claude-haiku-5-5`; OpenAI como alternativa
- Firebase Cloud Messaging (push), OCR via n8n (em construção)

## Rodando localmente

```bash
cp .env.example .env          # preencha AI_API_KEY para testar o chat
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

O perfil `dev` usa H2 em memória, liga o Swagger em http://localhost:8080/swagger-ui.html e
usa segredos de desenvolvimento. **Nunca use `dev` em produção.**

Testes:

```bash
./gradlew test
```

## Produção

A configuração base (`application.yml`) é a de produção. Ela não tem segredos padrão:
a aplicação não sobe sem `JWT_SECRET` forte. Variáveis principais (lista completa em `.env.example`):

| Variável | Para quê |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_URL_PARAMS` | PostgreSQL |
| `JWT_SECRET` | Assinatura dos tokens (mín. 32 bytes: `openssl rand -base64 48`) |
| `AI_PROVIDER`, `AI_MODEL`, `AI_API_KEY`, `AI_EFFORT` | Provedor e modelo da IA |
| `INTERNAL_API_KEY` | Callbacks do n8n (`X-Internal-API-Key`) |
| `CPF_SECRET` | Hash do CPF (opcional; sem ele o CPF não é aceito) |
| `CORS_ALLOWED_ORIGINS` | Só para a versão web |
| `FORWARD_HEADERS_STRATEGY=framework` | Atrás de proxy/load balancer (IP real no rate limit) |

Docker:

```bash
docker build -t fini-api .
docker run -p 8080:8080 --env-file .env fini-api
```

## Documentação

- [docs/API.md](docs/API.md): guia para o app (autenticação, chat e cartões de confirmação, erros)
- [docs/openapi.json](docs/openapi.json): especificação OpenAPI completa
- [SECURITY.md](SECURITY.md): medidas de segurança e checklist de produção
- [docs/DEPLOY_RENDER.md](docs/DEPLOY_RENDER.md): passo a passo para colocar no ar no Render (`render.yaml`)

## Arquitetura

```
presentation (controllers, tratamento de erros)
     ↓
application (services, DTOs, assistente de IA)
     ↓
domain (entidades, repositórios)        infrastructure (provedores de IA, Firebase)
```

O assistente fica em `application/service/assistant`:

- `AssistantPromptBuilder`: instruções fixas + contexto do usuário (marcado como dado, não instrução)
- `AssistantToolbox`: ferramentas de leitura (executadas no servidor, só com dados do próprio usuário)
  e de proposta (`propose_*`, que validam e devolvem cartões, nunca gravam)
- `ChatUsageService`: limite mensal de mensagens do usuário (contador atômico por mês)

Os provedores ficam em `infrastructure/ai` (`AnthropicChatProvider`, `OpenAiChatProvider`), atrás
da interface `AiChatProvider`. Trocar de modelo é só mudar `AI_MODEL`.
