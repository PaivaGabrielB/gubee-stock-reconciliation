# gubee-stock-reconciliation

Serviço de reconciliação de estoque para integração com marketplaces.

## Tecnologias

| Camada       | Tecnologia                     |
|--------------|-------------------------------|
| Linguagem    | Java 17                        |
| Framework    | Spring Boot 3.2.5              |
| Banco        | PostgreSQL 15 + Flyway         |
| Mensageria   | Apache Kafka                   |
| ORM          | Spring Data JPA / Hibernate    |
| Docs API     | OpenAPI 3 / Swagger UI         |
| Testes       | JUnit 5 + Mockito + Testcontainers |
| Containers   | Docker Compose                 |
| Logging      | Logstash JSON encoder          |

---

## Como Rodar

### Pré-requisitos
- Docker + Docker Compose
- Java 17 + Maven 3.9+ (para rodar localmente sem Docker)

### Com Docker Compose (recomendado)
```bash
docker compose up --build
```
A aplicação estará disponível em `http://localhost:8080`.

### Localmente (sem Docker para a app)
```bash
# Sobe apenas as dependências
docker compose up postgres kafka -d

# Roda a aplicação
mvn spring-boot:run
```

### Documentação da API (Swagger)

```bash
 http://localhost:8080/swagger-ui.html
```

---

## Endpoints Disponíveis

| Método | Endpoint                              | Descrição                               |
|--------|---------------------------------------|-----------------------------------------|
| POST   | `/events`                             | Submete um evento de estoque/pedido     |
| GET    | `/events?status=PENDING`              | Lista eventos por status                |
| GET    | `/stocks/{accountId}/{sku}`           | Estoque atual                           |
| GET    | `/stocks/{accountId}/{sku}/history`   | Histórico de alterações                 |
| GET    | `/inconsistencies`                    | Lista inconsistências detectadas        |
| GET    | `/swagger-ui.html`                    | Documentação interativa da API          |

---

## Exemplos de Requisição

### Ajuste inicial de estoque
```bash
  {
    "eventId": "evt-001",
    "type": "STOCK_ADJUSTED",
    "occurredAt": "2026-05-28T10:00:00Z",
    "accountId": "account-001",
    "sku": "ABC-123",
    "available": 10,
    "reason": "manual_adjustment"
  }
```

### Pedido criado
```bash
  {
    "eventId": "evt-002",
    "type": "ORDER_CREATED",
    "occurredAt": "2026-05-28T10:01:00Z",
    "marketplace": "MERCADO_LIVRE",
    "accountId": "account-001",
    "externalOrderId": "ML-123456",
    "sku": "ABC-123",
    "quantity": 2
  }
```

### Pedido cancelado
```bash
  {
    "eventId": "evt-003",
    "type": "ORDER_CANCELLED",
    "occurredAt": "2026-05-28T10:05:00Z",
    "marketplace": "MERCADO_LIVRE",
    "accountId": "account-001",
    "externalOrderId": "ML-123456",
    "sku": "ABC-123",
    "quantity": 2
  }
```

### Consultar estoque atual
```bash
 http://localhost:8080/stocks/account-001/ABC-123
```

### Consultar histórico
```bash
 http://localhost:8080/stocks/account-001/ABC-123/history
```

### Listar eventos pendentes
```bash
 http://localhost:8080/events?status=PENDING
```

---

## Como Rodar os Testes

```bash
# Todos os testes (requer Docker para Testcontainers)
mvn test

# Apenas testes unitários (sem Docker)
mvn test -pl . -Dtest="*ServiceTest"

# Relatório de cobertura (gerado em target/site/jacoco/index.html)
mvn test jacoco:report
```

---

## Limitações Conhecidas

- Sem autenticação nos endpoints (sem scope para este desafio).
- Sem paginação em listagens de histórico e inconsistências.
- Eventos `PENDING` não expiram automaticamente — requerem intervenção manual ou re-envio do `ORDER_CREATED`.
- Kafka publishing é best-effort: falhas no broker não bloqueiam o processamento, mas o evento pode não ser entregue ao tópico.
- Uma única instância do serviço; em múltiplas instâncias o comportamento é correto por causa do locking no PostgreSQL.
