# Colocar a API no ar no Render

A API roda no Render (arquivo `render.yaml` na raiz) e o banco PostgreSQL fica no Supabase
(plano grátis, não expira). Você só precisa fazer os passos abaixo.

> Nunca cole chaves ou senhas em chats, issues ou commits. Elas ficam só no painel do Render
> e no console da Anthropic.

## 1. Pegar a chave da Anthropic (Claude)

1. Acesse https://console.anthropic.com e crie a conta (ou entre).
2. Em **Settings > Billing**, adicione créditos (o Claude Haiku 5.5 é barato: US$ 0,10 por milhão
   de tokens de entrada; US$ 5 dura bastante em testes).
3. Ainda em **Billing**, defina um **limite de gasto mensal** (ex.: US$ 10). Isso protege contra
   surpresas.
4. Em **API Keys**, clique em **Create Key**, dê o nome `fini-render` e copie a chave
   (começa com `sk-ant-`). Ela aparece uma única vez. Guarde até o passo 2.

## Banco no Supabase

### Criar o projeto

1. Entre em https://supabase.com/dashboard e clique em **New project**.
2. Nome: `fini`. Em **Database Password**, clique em **Generate a password** e guarde a senha
   num lugar seguro (gerenciador de senhas). Ela não aparece de novo.
3. Em **Region**, escolha **East US (North Virginia)**. A API fica na Virgínia, e banco e API
   na mesma região deixam o app bem mais rápido.
4. Clique em **Create new project** e espere uns 2 minutos.

### Fechar a Data API (recomendado)

As tabelas da API ficam no schema `fini`, que a Data API do Supabase não expõe. Como a API do Fini
não usa a Data API, dá para desligar de vez: **Project Settings > Data API** e desative
**Enable Data API**.

### Pegar os dados de conexão

1. No topo do projeto, clique em **Connect**.
2. Escolha a aba **Connection String** e, em **Method**, selecione **Session pooler**
   (não use "Direct connection", que só funciona com IPv6, nem "Transaction pooler").
3. Você vai ver algo assim:
   `postgresql://postgres.abcdefghij:[YOUR-PASSWORD]@aws-1-us-east-1.pooler.supabase.com:5432/postgres`
   - **Host**: o trecho depois do `@` e antes de `:5432` (ex.: `aws-1-us-east-1.pooler.supabase.com`)
   - **Usuário**: o trecho depois de `postgresql://` e antes de `:` (ex.: `postgres.abcdefghij`)
   - **Senha**: a que você guardou no passo 2

### Colocar no Render

1. No Render, abra **fini-api > Environment**.
2. Edite (ou crie) estas três variáveis, colando os valores do Supabase:
   - `DB_HOST` = host
   - `DB_USERNAME` = usuário
   - `DB_PASSWORD` = senha
3. Confira que existem também `DB_PORT=5432`, `DB_NAME=postgres`, `DB_SCHEMA=fini` e
   `DB_URL_PARAMS=?sslmode=require` (o Blueprint cria essas sozinho).
4. Clique em **Save, rebuild, and deploy**.
5. Nos **Logs**, deve aparecer `Successfully applied ... migrations to schema "fini"` e
   `Started FinnyApplication`.

O plano grátis do Supabase **pausa o projeto depois de 7 dias sem nenhum acesso**. Os dados não
se perdem: basta abrir o projeto no painel e clicar em **Restore**.

## 2. Criar os serviços no Render

1. No painel do Render (https://dashboard.render.com), clique em **New > Blueprint**.
2. Conecte o GitHub, se pedir, e dê acesso ao repositório **Finny---API**.
3. Escolha o repositório e a branch **master**. O Render mostra o `fini-api` (Web Service).
4. Ele vai pedir **AI_API_KEY** (chave da Anthropic do passo 1) e os dados do banco
   (`DB_HOST`, `DB_USERNAME`, `DB_PASSWORD`, veja "Banco no Supabase" acima).
5. Clique em **Apply** (ou **Deploy Blueprint**).

As outras chaves (`JWT_SECRET`, `INTERNAL_API_KEY`, `CPF_SECRET`) são geradas pelo próprio
Render com valores aleatórios fortes. Ninguém precisa criar nem digitar.

## 3. Acompanhar o primeiro deploy

1. Abra o serviço **fini-api > Logs**. O primeiro build leva de 5 a 10 minutos.
2. Está pronto quando aparecerem as linhas:
   - `Successfully applied ... migrations to schema "fini"` (banco criado)
   - `Started FinnyApplication`
3. No topo da página do serviço aparece a URL pública, algo como
   `https://fini-api-xxxx.onrender.com`.
4. Abra `https://SUA-URL/actuator/health` no navegador. Deve mostrar `{"status":"UP"}`.

Mande só essa URL na conversa (ela não é segredo).

## 4. Depois do deploy

- **n8n (OCR)**: quando for ligar o OCR, copie o `INTERNAL_API_KEY` em
  **fini-api > Environment** (botão de olho para revelar) e cole no n8n, no header
  `X-Internal-API-Key`.
- **Versão web**: quando existir, coloque o domínio em `CORS_ALLOWED_ORIGINS`
  (ex.: `https://app.fini.com.br`). O app mobile não precisa disso.
- **Novos deploys**: todo merge na `master` faz deploy automático.

## E-mails (código de verificação e "esqueci a senha")

Enquanto nenhum provedor estiver configurado, a API não envia e-mails e a verificação fica
desligada. Para ligar com o Resend:

1. Crie a conta em https://resend.com. Em **Domains**, adicione seu domínio e crie os registros
   DNS que ele mostrar (sem domínio próprio, o Resend só entrega para o seu e-mail).
2. Em **API Keys > Create API Key**, permissão **Sending access**, copie a chave (`re_...`).
3. No Render, **fini-api > Environment**, crie:
   - `MAIL_PROVIDER` = `resend`
   - `RESEND_API_KEY` = a chave
   - `MAIL_FROM` = `Fini <codigo@seu-dominio.com.br>`
4. **Save, rebuild, and deploy**. Contas criadas antes disso continuam liberadas; quem se
   cadastrou com a verificação desligada recebe um código no próximo login.

Alternativa sem domínio: Brevo (`MAIL_PROVIDER=brevo`, `BREVO_API_KEY`, e `MAIL_FROM` com um
remetente verificado no Brevo).

## Planos e custos

O `render.yaml` começa no plano **free**:

| | Free | Pago (recomendado para uso real) |
|---|---|---|
| API | "Dorme" após 15 min sem uso; a 1ª requisição depois disso leva ~1 min | Starter, US$ 7/mês, sempre ligada |
| Banco (Supabase) | 500 MB, não expira; pausa após 7 dias sem acesso (é só reativar) | Pro, US$ 25/mês, com backups diários |

Para trocar a API: no painel do Render, abra o serviço e use **Settings > Instance Type**.

O banco antigo do Render (`fini-db`) não é mais usado. O Render não apaga nada sozinho por ter
saído do `render.yaml`: quando a API estiver funcionando com o Supabase, apague o `fini-db` no
painel (**fini-db > Settings > Delete Database**). Ele só tinha dados de teste.

## Problemas comuns

| Sintoma no log | Causa e solução |
|---|---|
| `JWT_SECRET ausente ou curto demais` | Variável apagada. Em **Environment**, crie `JWT_SECRET` com **Generate** |
| `Connection refused`, `password authentication failed` ou `Tenant or user not found` | Confira `DB_HOST`, `DB_USERNAME` (com o `.abcdefghij` no fim) e `DB_PASSWORD`; use o **Session pooler** |
| `Max client connections reached` | Diminua `DB_POOL_SIZE` (ex.: 3) |
| Projeto do Supabase pausado | Abra o projeto no painel do Supabase e clique em **Restore** |
| Chat responde 503 | `AI_API_KEY` errada ou sem créditos na Anthropic |
| `OutOfMemoryError` | Plano free tem 512 MB; suba para Starter |
