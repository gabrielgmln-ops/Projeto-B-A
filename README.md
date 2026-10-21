# Ledger API: o nÃºcleo bancÃ¡rio

API REST que funciona como o livro-razÃ£o de um banco digital: contas, transferÃªncias em **partidas dobradas**, saldo **sempre derivado** dos lanÃ§amentos, **idempotÃªncia**, bloqueio de saldo negativo sob concorrÃªncia, extrato paginado e **auditoria append-only**.

> Projeto de estudo. Todos os dados sÃ£o sintÃ©ticos.

**Stack:** Java 21 Â· Spring Boot 3.5 Â· PostgreSQL 16 Â· Spring Data JPA Â· Flyway Â· Bean Validation Â· springdoc-openapi Â· JUnit 5 Â· Testcontainers Â· JaCoCo Â· Docker Compose Â· GitHub Actions

## Arquitetura

```mermaid
flowchart LR
    Cliente([Cliente HTTP]) --> C

    subgraph API [Spring Boot]
        C[Controllers<br/>Conta Â· Transferencia Â· Auditoria]
        S[Services<br/>ContaService Â· TransferenciaService]
        I[IdempotenciaService]
        A[AuditoriaService]
        R[Repositories<br/>Spring Data JPA]
        E[GlobalExceptionHandler<br/>RFC 9457]
        C --> S
        S --> I
        S --> A
        S --> R
        I --> R
        A --> R
        C -.erros.-> E
    end

    R --> DB[(PostgreSQL<br/>conta Â· transacao Â· lancamento<br/>chave_idempotencia Â· evento_auditoria)]
    F[Flyway] -->|migraÃ§Ãµes versionadas| DB
```

### Fluxo de uma transferÃªncia

```mermaid
sequenceDiagram
    participant Cli as Cliente
    participant T as TransferenciaService
    participant DB as PostgreSQL
    Cli->>T: POST /transferencias (Idempotency-Key)
    T->>DB: BEGIN
    T->>DB: INSERT chave ON CONFLICT DO NOTHING
    alt chave jÃ¡ existia
        T-->>Cli: resposta original (Idempotent-Replayed: true)
    else chave nova
        T->>DB: SELECT conta FOR UPDATE (menor id primeiro)
        T->>DB: SELECT conta FOR UPDATE (maior id)
        T->>DB: SUM(lanÃ§amentos) da origem
        T->>DB: INSERT transacao + DEBITO + CREDITO + auditoria
        T->>DB: UPDATE chave com a resposta
        T->>DB: COMMIT
        T-->>Cli: 201 Created
    end
```

## DecisÃµes principais

| Tema | DecisÃ£o | Por quÃª |
|---|---|---|
| Dinheiro | `BigDecimal` / `NUMERIC(19,2)` | `double` Ã© binÃ¡rio e nÃ£o representa 0,10 exatamente |
| Saldo | Nunca armazenado; `SUM` dos lanÃ§amentos | NÃ£o existe nÃºmero editÃ¡vel que possa divergir do histÃ³rico |
| Partidas dobradas | Todo depÃ³sito sai da conta interna **Tesouraria** | A soma de todos os saldos do banco Ã© sempre **zero** |
| ConcorrÃªncia | Lock pessimista (`SELECT ... FOR UPDATE`) em ordem fixa de id | Impede gasto duplo e evita deadlock Aâ†’B / Bâ†’A |
| IdempotÃªncia | `INSERT ... ON CONFLICT DO NOTHING` na mesma transaÃ§Ã£o | AtÃ´mico mesmo com repetiÃ§Ãµes simultÃ¢neas; falhas nÃ£o ficam gravadas |
| Append-only | Triggers no banco bloqueiam `UPDATE`/`DELETE` | Vale atÃ© para SQL manual, nÃ£o sÃ³ para a aplicaÃ§Ã£o |
| Erros | Problem Details (RFC 9457) centralizados | Formato padrÃ£o; nunca vaza stack trace ou SQL |

## Como rodar

PrÃ©-requisitos: JDK 21 e Docker Desktop rodando.

```bash
copy .env.example .env
```
```bash
docker compose up -d
```
```bash
.\mvnw spring-boot:run
```

- Swagger: http://localhost:8081/swagger-ui.html
- SaÃºde: http://localhost:8081/api/v1/saude e http://localhost:8081/actuator/health

### Testes e cobertura

```bash
.\mvnw verify
```

Roda todos os testes contra um PostgreSQL real (Testcontainers) e falha se a cobertura de linhas for menor que 80%. RelatÃ³rio em `target/site/jacoco/index.html`.

Destaques da suÃ­te:
- `ConcorrenciaIntegracaoTest`: **1.000 transferÃªncias simultÃ¢neas** entre 10 contas (50 threads) e a soma dos saldos permanece constante. TambÃ©m dispara 500 saques simultÃ¢neos de R$ 1 numa conta com R$ 100: exatamente 100 passam.
- `IdempotenciaIntegracaoTest`: repetiÃ§Ã£o sequencial e **20 repetiÃ§Ãµes simultÃ¢neas** com a mesma chave geram uma Ãºnica transaÃ§Ã£o.
- `AuditoriaExtratoIntegracaoTest`: o banco recusa `UPDATE`/`DELETE` no histÃ³rico.

## Endpoints

| MÃ©todo | Rota | DescriÃ§Ã£o |
|---|---|---|
| POST | `/api/v1/contas` | Cria conta de cliente |
| GET | `/api/v1/contas/{id}` | Consulta conta e saldo |
| GET | `/api/v1/contas/{id}/extrato?page=0&size=20` | Extrato paginado |
| POST | `/api/v1/depositos` | DepÃ³sito (header `Idempotency-Key`) |
| POST | `/api/v1/transferencias` | TransferÃªncia (header `Idempotency-Key`) |
| GET | `/api/v1/transferencias/{id}` | Consulta transaÃ§Ã£o |
| GET | `/api/v1/auditoria?entidadeId=` | Eventos de auditoria |

Exemplo:

```bash
curl.exe -X POST http://localhost:8081/api/v1/contas -H "Content-Type: application/json" -d "{\"titular\":\"Cliente Sintetico\"}"
```

## Deploy (Render, plano gratuito)

1. Suba o repositÃ³rio no GitHub.
2. No Render: **New â†’ Blueprint** e selecione o repositÃ³rio. O `render.yaml` cria o PostgreSQL e o serviÃ§o web a partir do `Dockerfile`.
3. Acesse `https://<seu-servico>.onrender.com/swagger-ui.html`.

## LimitaÃ§Ãµes conhecidas

- Sem autenticaÃ§Ã£o: qualquer pessoa com a URL consulta qualquer conta (fora do escopo da v1).
- Moeda Ãºnica (BRL), sem juros nem tarifas.
- O saldo Ã© um `SUM` sobre todos os lanÃ§amentos da conta; com milhÃµes de lanÃ§amentos por conta seria preciso um snapshot periÃ³dico de saldo.
- Chaves de idempotÃªncia nÃ£o expiram.
- RequisiÃ§Ãµes que falham (ex.: saldo insuficiente) nÃ£o ficam gravadas na tabela de idempotÃªncia; reenviar a mesma chave reavalia o pedido.
- O lock pessimista serializa operaÃ§Ãµes numa mesma conta muito movimentada (ex.: a Tesouraria nos depÃ³sitos).

## PrÃ³ximos passos

- AutenticaÃ§Ã£o (OAuth2/JWT) e autorizaÃ§Ã£o por titular.
- Estorno como nova transaÃ§Ã£o compensatÃ³ria.
- Snapshots de saldo e particionamento da tabela de lanÃ§amentos.
- ExpiraÃ§Ã£o das chaves de idempotÃªncia.
- Outbox pattern para publicar eventos (Kafka) de forma confiÃ¡vel.
- MÃ©tricas e tracing (Micrometer, OpenTelemetry).
