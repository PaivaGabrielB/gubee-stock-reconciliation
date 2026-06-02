# DECISIONS.md — Gubee Stock Reconciliation

## Interpretação do Problema

O serviço age como um **ledger de estoque**: recebe eventos de múltiplas fontes (marketplace, ERP do cliente, própria Gubee) e mantém uma visão consistente e auditável do saldo disponível por `(accountId, sku)`.

Os principais riscos identificados:
- **Duplicidade de eventos** (mesmo `eventId` recebido mais de uma vez)
- **Duplicidade lógica** (dois cancelamentos do mesmo pedido com eventIds distintos)
- **Eventos fora de ordem** (ORDER_CANCELLED antes de ORDER_CREATED)
- **Concorrência** (dois pedidos simultâneos no mesmo SKU)
- **Múltiplas fontes de restauração** (ORDER_CANCELLED e MARKETPLACE_STOCK_RESTORED para o mesmo pedido)

---

## Fonte da Verdade do Estoque

**STOCK_ADJUSTED é a fonte primária de verdade.** Esse evento carrega um valor absoluto (`available`) e representa a posição explícita declarada pelo cliente ou sistema. Pedidos e cancelamentos são delta-events aplicados sobre esse baseline.

Consequência: se o sistema recebe ORDER_CREATED sem um STOCK_ADJUSTED prévio, o saldo parte de zero. Isso é documentado como comportamento esperado — o cliente deve ajustar o estoque antes das vendas.

---

## Idempotência (5.1)

- A entidade `StockEvent` usa `event_id` como chave primária (unique constraint no banco).
- Antes de processar, o serviço verifica `existsByEventId`. Se o eventId já existe, retorna `IGNORED` imediatamente.
- Em caso de race condition entre dois threads com o mesmo eventId, o unique constraint garante que apenas um será persistido. A constraint violation retorna HTTP 200 com status `IGNORED`.

---

## Duplicidade Lógica (5.2)

Controlada via entidade `Order` com unique constraint em `(marketplace, account_id, external_order_id, sku)`:
- **ORDER_CREATED duplicado** (mesma chave): retorna `INCONSISTENT`.
- **ORDER_CANCELLED duplicado**: verificado pelo campo `order.status`. Se já está `CANCELLED`, retorna `INCONSISTENT` sem restaurar estoque.

---

## Eventos Fora de Ordem (5.3)

**Abordagem: compensação automática com fallback para PENDING.**

Quando ORDER_CANCELLED chega antes de ORDER_CREATED:
1. O evento é salvo como `PENDING`.
2. Uma inconsistência é registrada para rastreabilidade.

Quando ORDER_CREATED chega posteriormente, o serviço busca eventos `ORDER_CANCELLED` com status `PENDING` para o mesmo pedido e os reprocessa automaticamente dentro da mesma transação. Isso garante que o cancelamento atrasado não fique "preso" sem intervenção manual na maioria dos casos.

Se o ORDER_CREATED nunca chegar, o evento fica em estado `PENDING` e aparece na API `GET /events?status=PENDING` para análise.

---

## Concorrência (5.4)

**Pessimistic locking (SELECT FOR UPDATE)** via `@Lock(LockModeType.PESSIMISTIC_WRITE)` no JPA.

Ao processar ORDER_CREATED ou qualquer evento que modifica estoque, o serviço executa `findByAccountIdAndSkuWithLock` que gera `SELECT ... FOR UPDATE` no PostgreSQL. Isso serializa acessos concorrentes à mesma linha.

**Estoque negativo não é permitido.** Se ORDER_CREATED resultaria em saldo negativo, o evento é marcado como `INCONSISTENT` e nenhuma alteração é aplicada. A decisão de bloquear ao invés de permitir negativo é conservadora — preferimos rejeitar o evento e sinalizar o problema a ter um saldo inconsistente silenciosamente.

---

## Comportamento com Múltiplas Instâncias

O pessimistic locking no PostgreSQL garante corretude em múltiplas instâncias do serviço — apenas uma instância consegue o lock de uma linha por vez. O unique constraint em `event_id` garante idempotência mesmo com concorrência.

**Kafka:** o publisher é best-effort. Se o Kafka estiver indisponível, o processamento continua normalmente (logging do erro), garantindo que a indisponibilidade do broker não bloqueia o fluxo principal.

---

## Rastreabilidade (5.5)

Duas estruturas complementares:
- **`stock_events`**: registro de cada evento recebido com seu status de processamento (`PROCESSED`, `IGNORED`, `PENDING`, `INCONSISTENT`).
- **`stock_history`**: linha do tempo de alterações efetivas no estoque, com `quantity_before`, `quantity_after`, `delta`, `occurred_at` e `processed_at`.
- **`inconsistencies`**: log de situações problemáticas com descrição textual.

---

## Multi-account (5.7)

Chave natural do estoque: `(account_id, sku)`. Estoque de `account-001/ABC-123` é completamente isolado de `account-002/ABC-123` por unique constraint e queries sempre filtram por `accountId`.

---

## Controle de Estoque por Marketplace (5.8)

**Decisão: controle global por `(account_id, sku)`, sem segmentação por marketplace.**

Justificativa: o estoque físico é único — o mesmo produto que foi vendido no Mercado Livre reduziu a quantidade disponível para todos os outros canais. Segmentar por marketplace requereria políticas de reserva de estoque por canal, que não fazem parte do escopo deste desafio.

O `marketplace` é capturado nos eventos e no histórico para fins de rastreabilidade e auditoria, mas não segmenta o saldo.

---

## Arquitetura Hexagonal

Domínio em `domain/model` e `domain/exception` — sem dependências externas (exceção: anotações JPA como trade-off pragmático, documentado abaixo).

Portas de entrada (`application/port/in`): interfaces que definem os casos de uso.
Portas de saída (`application/port/out`): interfaces para persistência e mensageria.
Adaptadores (`infrastructure/adapter`): implementações concretas (JPA, Kafka, REST).

**Trade-off pragmático:** os modelos de domínio carregam anotações JPA diretamente. Em produção com mais tempo, usaríamos entidades JPA separadas no pacote de infraestrutura e mapeadores explícitos para manter o domínio completamente puro. Para este desafio, o overhead de duplicação não se justifica.

---

## O que Faria Diferente em Produção

1. **Separação de entidades JPA e modelos de domínio** com mapeadores explícitos.
2. **Outbox pattern** para garantir entrega de mensagens Kafka de forma atômica com o estado do banco.
3. **Circuit breaker** (Resilience4j) para chamadas externas.
4. **Observabilidade**: métricas Prometheus (contador de eventos por tipo/status), tracing distribuído (OpenTelemetry), dashboards Grafana.
5. **Dead Letter Queue** no Kafka para eventos que falham repetidamente.
6. **Retry automático** com backoff exponencial para eventos PENDING.
7. **Particionamento Kafka** por `accountId:sku` para garantir ordering por chave natural.
8. **Testes de contrato** com Pact para as integrações de API.

---

## Simplificações por Causa do Prazo

- Sem autenticação/autorização nos endpoints.
- Sem paginação nos endpoints de histórico e inconsistências.
- Sem expiração automática de eventos PENDING.
- Kafka publisher é fire-and-forget (sem confirmação de entrega).
- Sem métricas de negócio expostas.
