# Finny API

API REST para o **Finny** - Assistente Financeiro Pessoal com IA 🦉

## Stack Técnica

- **Java**: 17 LTS
- **Spring Boot**: 3.2.0
- **PostgreSQL**: 14+
- **Gradle**: Build tool
- **JWT**: Autenticação
- **MapStruct**: Mapeamento Entity ↔ DTO
- **Flyway**: Migrations
- **Lombok**: Redução de boilerplate
- **SpringDoc OpenAPI**: Documentação Swagger

## Arquitetura

**Layered Architecture** com 3 camadas principais:

```
Presentation Layer (Controllers)
         ↓
Application Layer (Services + DTOs)
         ↓
Domain Layer (Entities + Repositories)
```

## Pré-requisitos

- JDK 17 ou superior
- PostgreSQL 14+ rodando
- Gradle (incluído via wrapper)

## Configuração

### 1. Banco de Dados

Crie um banco de dados PostgreSQL:

```sql
CREATE DATABASE finny_db;
```

### 2. Variáveis de Ambiente

Configure as seguintes variáveis ou edite `src/main/resources/application.yml`:

```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=finny_db
DB_USERNAME=postgres
DB_PASSWORD=sua_senha

JWT_SECRET=finny-jwt-secret-key-change-in-production-min-256-bits-long
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

N8N_BASE_URL=http://localhost:5678
N8N_API_KEY=n8n-api-key

INTERNAL_API_KEY=internal-callback-api-key

STORAGE_TYPE=local
STORAGE_LOCAL_PATH=/tmp/finny-images
```

### 3. Migrations Flyway

**IMPORTANTE**: As migrations do Flyway ainda não foram criadas. Você precisará criar os scripts SQL em:

```
src/main/resources/db/migration/
```

Por enquanto, você pode desabilitar o Flyway temporariamente editando `application.yml`:

```yaml
spring:
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: create # OU update (apenas para desenvolvimento!)
```

## Executar a Aplicação

### Usando Gradle Wrapper (Recomendado)

```bash
# Windows
.\gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

### Build

```bash
# Windows
.\gradlew.bat build

# Linux/Mac
./gradlew build
```

A aplicação iniciará em: `http://localhost:8080`

## Documentação da API

Após iniciar a aplicação, acesse:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## Endpoints Principais

### Autenticação
- `POST /api/v1/auth/register` - Registrar novo usuário
- `POST /api/v1/auth/login` - Login
- `POST /api/v1/auth/refresh` - Renovar token
- `POST /api/v1/auth/logout` - Logout

### Onboarding
- `POST /api/v1/onboarding/complete` - Completar onboarding

### Perfil
- `GET /api/v1/profile` - Obter perfil
- `PUT /api/v1/profile` - Atualizar perfil

### Chat
- `POST /api/v1/chat/messages` - Enviar mensagem
- `GET /api/v1/chat/history` - Histórico
- `DELETE /api/v1/chat/history` - Deletar histórico

### Transações
- `GET /api/v1/transactions` - Listar transações
- `POST /api/v1/transactions` - Criar transação
- `GET /api/v1/transactions/{id}` - Obter transação
- `PUT /api/v1/transactions/{id}` - Atualizar transação
- `DELETE /api/v1/transactions/{id}` - Deletar (soft delete)
- `GET /api/v1/transactions/summary` - Resumo

### Categorias
- `GET /api/v1/categories` - Listar categorias
- `POST /api/v1/categories` - Criar categoria
- `PUT /api/v1/categories/{id}` - Atualizar categoria
- `DELETE /api/v1/categories/{id}` - Deletar categoria

### Metas
- `GET /api/v1/goals` - Listar metas
- `POST /api/v1/goals` - Criar meta
- `GET /api/v1/goals/{id}` - Obter meta
- `PUT /api/v1/goals/{id}` - Atualizar meta
- `DELETE /api/v1/goals/{id}` - Deletar meta
- `POST /api/v1/goals/{id}/contribute` - Contribuir para meta

### OCR
- `POST /api/v1/ocr/process` - Processar nota fiscal
- `GET /api/v1/ocr/status/{processingId}` - Status do processamento

## Segurança

- **JWT Bearer Token**: Todos os endpoints (exceto `/api/v1/auth/**`) requerem autenticação
- **Tenant Isolation**: Todas as queries filtram por `userId` automaticamente
- **Password Encryption**: BCrypt com strength 10
- **Soft Delete**: Transações deletadas mantêm `deleted=true`

## TODOs Importantes

### Integrações Futuras

- [ ] **n8n**: Implementar integração real com webhooks do n8n
  - Arquivos: `N8nClient.java`, `ChatService.java`, `OcrService.java`

- [ ] **Storage S3/R2**: Substituir armazenamento local de imagens
  - Arquivos: `LocalImageStorageService.java`

- [ ] **E-mail**: Implementar verificação de e-mail e notificações
  - Criar `EmailService.java`

- [ ] **CORS**: Configurar origens permitidas em produção
  - Arquivo: `SecurityConfig.java`, `WebConfig.java`

- [ ] **Callback Security**: Validar X-Internal-API-Key no InternalController
  - Arquivo: `InternalController.java`

- [ ] **Flyway Migrations**: Criar scripts SQL de migração
  - Diretório: `src/main/resources/db/migration/`

### Melhorias

- [ ] Implementar testes unitários e de integração
- [ ] Adicionar paginação em mais endpoints
- [ ] Implementar rate limiting
- [ ] Adicionar cache (Redis)
- [ ] Implementar WebSocket para notificações em tempo real
- [ ] Adicionar métricas e monitoring (Actuator + Prometheus)

## Estrutura do Projeto

```
finny-api/
├── src/main/java/com/finny/api/
│   ├── FinnyApplication.java
│   ├── config/              # Configurações (Security, OpenAPI, etc)
│   ├── security/            # JWT, filtros, autenticação
│   ├── domain/              # Entidades, Enums, Repositories
│   ├── application/         # Services, DTOs, Mappers, Exceptions
│   ├── infrastructure/      # N8n Client, Storage
│   └── presentation/        # Controllers, Exception Handler
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/        # Flyway migrations (TODO)
└── build.gradle
```

## Princípios de Design

- **Clean Code**
- **SOLID**
- **DRY (Don't Repeat Yourself)**
- **Separation of Concerns**
- **Dependency Injection**

## Validações Importantes

- E-mail único no registro
- Senha: mínimo 8 caracteres, maiúsculas, minúsculas, números
- Idade mínima: 18 anos no onboarding
- Valor de transação > 0
- Data de transação não pode ser futura
- Data limite de meta deve ser no futuro

## Logs

Logs são salvos em: `logs/finny-api.log`

## Troubleshooting

### Erro de conexão com PostgreSQL

Verifique se o PostgreSQL está rodando e as credenciais estão corretas em `application.yml`.

### Erro de compilação relacionado ao Lombok ou MapStruct

Execute:
```bash
./gradlew clean build --refresh-dependencies
```

### Flyway migration failed

Se você ainda não criou as migrations, desabilite o Flyway temporariamente ou use `ddl-auto: create`.

## Licença

Proprietário - © Finny 2025

---

🦉 **Finny** - Seu amigo financeiro inteligente
