# Ligar os pagamentos (Mercado Pago)

O Finny cobra **R$ 9,90 por mês** pelo Premium, no cartão (cobrança automática todo mês) ou no Pix
(cada Pix libera 30 dias). Enquanto `BILLING_PROVIDER=none`, ninguém consegue pagar, mas o resto do app
funciona normal e o teste grátis de 7 dias já pode ser usado.

Taxas do Mercado Pago (consultadas em 11/2026, confira no painel): Pix 0,99% (≈ R$ 0,10 por mensalidade) e
cartão de 3,98% a 4,98% conforme o prazo de recebimento escolhido.

## 1. Criar a conta e pegar as chaves de teste

1. Entre em https://www.mercadopago.com.br/developers com a sua conta do Mercado Pago (pode ser de pessoa física).
2. Clique em **Suas integrações** > **Criar aplicação**. Nome: `Finny`. Tipo de pagamento: **Pagamentos on-line**,
   produto: **Checkout API** e marque também **Assinaturas**.
3. Abra a aplicação > **Credenciais de teste**. Copie o **Access Token** (começa com `TEST-`).

## 2. Configurar o webhook (aviso de pagamento)

1. Na aplicação, vá em **Webhooks** > **Configurar notificações**.
2. Em **URL de produção** e de teste, coloque: `https://fini-api-rosi.onrender.com/api/webhooks/mercadopago`
3. Marque os eventos **Pagamentos**, **Planos e assinaturas** (os dois: assinaturas e pagamentos autorizados).
4. Salve e copie a **Assinatura secreta** que aparece na tela.

## 3. Colocar no Render

No painel do Render, serviço `fini-api` > **Environment** > adicione:

| Variável | Valor |
|---|---|
| `BILLING_PROVIDER` | `mercadopago` |
| `MERCADOPAGO_ACCESS_TOKEN` | o Access Token (`TEST-...` para testar) |
| `MERCADOPAGO_WEBHOOK_SECRET` | a assinatura secreta do passo 2 |
| `BILLING_RETURN_URL` | endereço da página do site para onde a pessoa volta depois de pagar (ex.: `https://finny.app/premium`) |

Clique em **Save Changes**. O Render reinicia a API sozinho.

## 4. Testar e ir para produção

1. No site, crie uma conta e assine. Para pagar no teste, o Mercado Pago tem cartões e usuários de teste em
   **Suas integrações** > **Contas de teste** (use um comprador de teste, não a sua conta).
2. Funcionou? Troque `MERCADOPAGO_ACCESS_TOKEN` pelo **Access Token de produção** (começa com `APP_USR-`) e
   salve. Pronto: os pagamentos são reais.

## Mudar preço e limites

Tudo por variável no Render, sem mexer no código:

| Variável | Padrão |
|---|---|
| `PREMIUM_MONTHLY_PRICE` | `9.90` |
| `PREMIUM_TRIAL_DAYS` | `7` (0 desliga o teste grátis) |
| `PREMIUM_PIX_DAYS` | `30` |
| `FREE_TRANSACTIONS_PER_MONTH` | `15` |
| `FREE_ACTIVE_GOALS` | `1` |
| `CHAT_FREE_MONTHLY_LIMIT` | `5` |

Mudar o preço vale para assinaturas novas; quem já assina no cartão continua com o valor antigo.
