# 🚀 Guia Completo: Instalação do Java e Testes da API Finny

## 📦 PASSO 1: Instalar o Java 17

### Opção A: Amazon Corretto (Recomendado - Gratuito)

1. **Baixar instalador:**
   - Acesse: https://corretto.aws/downloads/latest/amazon-corretto-17-x64-windows-jdk.msi
   - Clique em "Download" e aguarde

2. **Instalar:**
   - Execute o arquivo `.msi` baixado
   - Marque a opção **"Add to PATH"** (importante!)
   - Clique em "Next" até concluir
   - Clique em "Install" → "Finish"

3. **Verificar instalação:**
   - Abra o **PowerShell** ou **CMD**
   - Digite:
   ```bash
   java -version
   ```
   - Deve aparecer algo como:
   ```
   openjdk version "17.0.x" 2024-xx-xx LTS
   OpenJDK Runtime Environment Corretto-17.0.x.x
   ```

### Opção B: Oracle JDK

1. Acesse: https://www.oracle.com/java/technologies/downloads/#java17
2. Baixe "Windows x64 Installer"
3. Execute e siga os passos (igual ao Corretto)

---

## 🗄️ PASSO 2: Configurar o PostgreSQL

### Se você NÃO tem PostgreSQL instalado:

1. **Baixar PostgreSQL:**
   - Acesse: https://www.postgresql.org/download/windows/
   - Baixe a versão 14 ou superior
   - Execute o instalador

2. **Durante instalação:**
   - Senha do postgres: **anote essa senha!**
   - Porta: deixe **5432** (padrão)
   - Locale: Portuguese, Brazil

3. **Criar banco de dados:**
   - Abra **pgAdmin** (instalado junto com PostgreSQL)
   - Conecte com a senha que você criou
   - Clique com botão direito em "Databases" → "Create" → "Database"
   - Nome: `finny_db`
   - Clique em "Save"

### Se você JÁ tem PostgreSQL:

Apenas crie o banco `finny_db` usando pgAdmin ou SQL:

```sql
CREATE DATABASE finny_db;
```

---

## ⚙️ PASSO 3: Configurar Variáveis de Ambiente da API

Crie um arquivo chamado `.env` na **raiz do projeto** (ou configure no sistema):

```bash
# Banco de dados
DB_HOST=localhost
DB_PORT=5432
DB_NAME=finny_db
DB_USERNAME=postgres
DB_PASSWORD=SUA_SENHA_AQUI

# JWT (pode deixar esses valores padrão por enquanto)
JWT_SECRET=finny-jwt-secret-key-change-in-production-min-256-bits-long
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

# n8n (pode deixar padrão por enquanto)
N8N_BASE_URL=http://localhost:5678
N8N_API_KEY=n8n-api-key

# Callback interno
INTERNAL_API_KEY=internal-callback-api-key

# Storage local
STORAGE_TYPE=local
STORAGE_LOCAL_PATH=C:/temp/finny-images

# CORS (React)
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8081,https://finny.app
```

**IMPORTANTE:** Substitua `SUA_SENHA_AQUI` pela senha do PostgreSQL!

---

## 🏃‍♂️ PASSO 4: Executar a API pela primeira vez

### Opção A: Usando Gradle Wrapper (Recomendado)

1. **Abra PowerShell** na pasta do projeto:
   ```powershell
   cd "C:\Users\mathe\OneDrive\Área de Trabalho\Finny - API"
   ```

2. **Execute:**
   ```powershell
   .\gradlew.bat bootRun
   ```

3. **Aguarde** (primeira execução demora ~2 minutos - baixa dependências)

4. **Quando aparecer:**
   ```
   Started FinnyApplication in X.XXX seconds
   ```
   ✅ **API está rodando!**

### Opção B: Usando IntelliJ IDEA / Eclipse

1. Abra o projeto na IDE
2. Espere indexar
3. Clique com botão direito em `FinnyApplication.java`
4. Clique em "Run 'FinnyApplication'"

---

## 🧪 PASSO 5: Testar se a API está funcionando

### Teste 1: Swagger UI (Interface Visual)

1. **Abra o navegador** e acesse:
   ```
   http://localhost:8080/swagger-ui.html
   ```

2. **Você deve ver** a documentação interativa da API com todos os endpoints

3. **Teste o endpoint de health:**
   - Procure por `/actuator/health` (se existir)
   - OU vá direto para testes de autenticação ⬇️

---

### Teste 2: Registrar um Usuário (via Swagger)

1. No Swagger, procure **"Auth Controller"**
2. Clique em **POST /api/v1/auth/register**
3. Clique em **"Try it out"**
4. Cole este JSON no body:

```json
{
  "email": "teste@finny.com",
  "password": "Teste123!",
  "fullName": "Usuário Teste"
}
```

5. Clique em **"Execute"**
6. **Resposta esperada (200 OK):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1...",
  "refreshToken": "eyJhbGciOiJIUzI1...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

✅ **Se recebeu o token = API FUNCIONANDO!**

---

### Teste 3: Login (via Swagger)

1. Procure **POST /api/v1/auth/login**
2. Clique em **"Try it out"**
3. Cole:

```json
{
  "email": "teste@finny.com",
  "password": "Teste123!"
}
```

4. Clique em **"Execute"**
5. **Resposta esperada (200 OK):** tokens JWT

---

### Teste 4: Testar Endpoint Protegido

1. **Copie o accessToken** do teste anterior (sem as aspas)
2. No topo do Swagger, clique no botão **🔒 Authorize**
3. Cole: `Bearer SEU_TOKEN_AQUI`
   - Exemplo: `Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
4. Clique em **"Authorize"** → **"Close"**

5. Agora teste **GET /api/v1/profile**
   - Clique em "Try it out"
   - Clique em "Execute"

6. **Resposta esperada (200 OK):**

```json
{
  "id": "uuid-aqui",
  "email": "teste@finny.com",
  "fullName": "Usuário Teste",
  "onboardingCompleted": false,
  "plan": "FREE",
  "createdAt": "2025-01-10T12:00:00"
}
```

✅ **Se funcionou = JWT e Autenticação OK!**

---

### Teste 5: CORS (testar com React)

Crie um arquivo HTML simples para testar CORS:

**teste-cors.html:**
```html
<!DOCTYPE html>
<html>
<head>
    <title>Teste CORS - Finny API</title>
</head>
<body>
    <h1>Teste CORS</h1>
    <button onclick="testarAPI()">Testar Login</button>
    <pre id="resultado"></pre>

    <script>
        async function testarAPI() {
            try {
                const response = await fetch('http://localhost:8080/api/v1/auth/login', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        email: 'teste@finny.com',
                        password: 'Teste123!'
                    })
                });

                const data = await response.json();
                document.getElementById('resultado').textContent = JSON.stringify(data, null, 2);
            } catch (error) {
                document.getElementById('resultado').textContent = 'ERRO: ' + error;
            }
        }
    </script>
</body>
</html>
```

1. Salve esse arquivo
2. Abra no navegador (duplo clique)
3. Clique no botão "Testar Login"
4. **Se aparecer o token = CORS FUNCIONANDO!**
5. **Se aparecer erro de CORS = problema na configuração**

---

## 🔍 PASSO 6: Verificar Logs

Os logs ficam em: `logs/finny-api.log`

Verifique se há erros:

```powershell
# No PowerShell:
Get-Content "logs/finny-api.log" -Tail 50
```

Ou abra o arquivo em qualquer editor de texto.

---

## ❌ Resolução de Problemas Comuns

### Erro: "Port 8080 already in use"

**Solução:**
```powershell
# Descobrir o que está usando a porta 8080:
netstat -ano | findstr :8080

# Matar o processo (substitua PID pelo número que apareceu):
taskkill /PID numero_do_pid /F
```

Ou altere a porta da API em `application.yml`:
```yaml
server:
  port: 8081
```

---

### Erro: "Connection to localhost:5432 refused"

**Causa:** PostgreSQL não está rodando

**Solução:**
1. Abra "Serviços" do Windows (Windows + R → `services.msc`)
2. Procure "postgresql-x64-14" (ou versão que você instalou)
3. Clique com botão direito → "Iniciar"

---

### Erro: "Flyway migration failed"

**Causa:** Migrations não estão corretas ou banco já tem tabelas

**Solução temporária** (apenas desenvolvimento):

Edite `application.yml`:
```yaml
spring:
  flyway:
    enabled: false  # DESABILITA FLYWAY
  jpa:
    hibernate:
      ddl-auto: create  # CRIA TABELAS AUTOMATICAMENTE
```

⚠️ **CUIDADO:** `ddl-auto: create` **apaga todas as tabelas** ao reiniciar!

Para produção, peça as migrations corretas ao DBA.

---

### Erro: "java is not recognized"

**Causa:** Java não foi adicionado ao PATH

**Solução:**
1. Desinstale o Java
2. Reinstale marcando **"Add to PATH"**
3. OU adicione manualmente:
   - Windows + R → `sysdm.cpl`
   - Aba "Avançado" → "Variáveis de Ambiente"
   - Em "Path" do sistema, adicione: `C:\Program Files\Amazon Corretto\jdk17.0.x_xx\bin`

---

## 📊 Checklist de Validação Completa

Use este checklist para garantir que tudo está funcionando:

- [ ] Java 17 instalado (`java -version` funciona)
- [ ] PostgreSQL instalado e rodando
- [ ] Banco `finny_db` criado
- [ ] Arquivo `.env` ou variáveis configuradas
- [ ] API inicia sem erros (`.\gradlew.bat bootRun`)
- [ ] Swagger acessível em http://localhost:8080/swagger-ui.html
- [ ] POST /api/v1/auth/register retorna token
- [ ] POST /api/v1/auth/login retorna token
- [ ] GET /api/v1/profile retorna dados do usuário (com token)
- [ ] CORS funcionando (teste HTML funciona)
- [ ] Logs em `logs/finny-api.log` sem erros críticos

---

## 🎯 Próximos Passos

Depois de validar que a API está funcionando:

1. **Integrar com React:**
   - Criar aplicação React (`npx create-react-app finny-web`)
   - Configurar Axios/Fetch para chamar `http://localhost:8080`
   - Implementar tela de login

2. **Completar business logic:**
   - Copiar código de `CODE_TO_COPY.md` para os Services

3. **Deploy:**
   - Configurar variáveis de produção
   - Deploy no Heroku/Railway/AWS

---

## 📞 Dúvidas?

Se algo não funcionar:

1. Verifique os **logs** em `logs/finny-api.log`
2. Verifique se PostgreSQL está **rodando**
3. Verifique as **variáveis de ambiente**
4. Teste endpoints no **Swagger primeiro**

---

🦉 **Finny API** - Seu assistente financeiro inteligente
