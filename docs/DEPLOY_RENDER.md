# Colocar a API no ar no Render

O arquivo `render.yaml` na raiz descreve tudo (API + banco PostgreSQL). O Render lê esse arquivo
e cria os serviços sozinho. Você só precisa fazer os passos abaixo.

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

## 2. Criar os serviços no Render

1. No painel do Render (https://dashboard.render.com), clique em **New > Blueprint**.
2. Conecte o GitHub, se pedir, e dê acesso ao repositório **Finny---API**.
3. Escolha o repositório e a branch **master**. O Render mostra dois itens:
   `fini-db` (PostgreSQL) e `fini-api` (Web Service).
4. Ele vai pedir o valor de **AI_API_KEY**: cole a chave da Anthropic do passo 1.
5. Clique em **Apply** (ou **Deploy Blueprint**).

As outras chaves (`JWT_SECRET`, `INTERNAL_API_KEY`, `CPF_SECRET`) são geradas pelo próprio
Render com valores aleatórios fortes. Ninguém precisa criar nem digitar.

## 3. Acompanhar o primeiro deploy

1. Abra o serviço **fini-api > Logs**. O primeiro build leva de 5 a 10 minutos.
2. Está pronto quando aparecerem as linhas:
   - `Successfully applied 11 migrations` (banco criado)
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

## Planos e custos

O `render.yaml` começa no plano **free**:

| | Free | Pago (recomendado para uso real) |
|---|---|---|
| API | "Dorme" após 15 min sem uso; a 1ª requisição depois disso leva ~1 min | Starter, US$ 7/mês, sempre ligada |
| Banco | **Expira em 30 dias** (os dados são apagados) | Basic 256 MB, cerca de US$ 6/mês, com backups |

Para trocar: no painel, abra o serviço ou o banco e use **Settings > Instance Type** (ou
**Upgrade**). Faça isso antes de ter usuários de verdade e antes dos 30 dias do banco free.

## Problemas comuns

| Sintoma no log | Causa e solução |
|---|---|
| `JWT_SECRET ausente ou curto demais` | Variável apagada. Em **Environment**, crie `JWT_SECRET` com **Generate** |
| `Connection refused` / erro de banco | O banco ainda está sendo criado; aguarde e clique em **Manual Deploy** |
| Chat responde 503 | `AI_API_KEY` errada ou sem créditos na Anthropic |
| `OutOfMemoryError` | Plano free tem 512 MB; suba para Starter |
