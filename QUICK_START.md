# 🚀 QUICK START - Finny API

## Passos para Rodar o Projeto

### 1. Instalar Java 17

Baixe e instale o JDK 17: https://adoptium.net/

Verifique a instalação:
```bash
java -version
```

### 2. Configurar PostgreSQL

Instale PostgreSQL 14+ e crie o banco:

```sql
CREATE DATABASE finny_db;
CREATE USER finny_user WITH ENCRYPTED PASSWORD 'finny_password';
GRANT ALL PRIVILEGES ON DATABASE finny_db TO finny_user;
```

### 3. Configurar Variáveis de Ambiente (Opcional)

Edite `src/main/resources/application.yml` ou configure variáveis de ambiente:

```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=finny_db
DB_USERNAME=finny_user
DB_PASSWORD=finny_password
```

### 4. IMPORTANTE: Desabilitar Flyway Temporariamente

Como as migrations ainda não foram criadas, edite `application.yml`:

```yaml
spring:
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: update # Hibernate criará as tabelas automaticamente
```

### 5. Executar o Projeto

```bash
# Windows
.\gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

### 6. Testar a API

Acesse o Swagger UI:
```
http://localhost:8080/swagger-ui.html
```

## Primeiro Teste

1. **Registrar usuário**:
   - Endpoint: `POST /api/v1/auth/register`
   - Body:
   ```json
   {
     "email": "teste@finny.app",
     "password": "Senha123",
     "fullName": "João da Silva"
   }
   ```

2. **Copiar o `accessToken` da resposta**

3. **Autorizar no Swagger**:
   - Clique em "Authorize" no topo
   - Cole o token (sem o prefixo "Bearer")

4. **Testar endpoints autenticados**:
   - Exemplo: `GET /api/v1/profile`

## Troubleshooting Rápido

### Erro: "Connection refused" (PostgreSQL)
- Verifique se o PostgreSQL está rodando
- Confirme host, porta e credenciais

### Erro: "Flyway migration failed"
- Certifique-se de que `flyway.enabled: false` em `application.yml`

### Erro: "Port 8080 already in use"
- Altere a porta em `application.yml`:
  ```yaml
  server:
    port: 8081
  ```

## Próximos Passos

1. ✅ Criar migrations Flyway em `src/main/resources/db/migration/`
2. ✅ Integrar n8n real (remover mocks)
3. ✅ Implementar storage S3/R2
4. ✅ Adicionar testes unitários

---

🦉 Pronto! Sua API Finny está rodando!
