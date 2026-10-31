# Ledger API: o núcleo bancário

API REST que funciona como o livro-razão de um banco digital: contas, transferências em **partidas dobradas**, saldo **sempre derivado** dos lançamentos, **idempotência**, bloqueio de saldo negativo sob concorrência, extrato paginado e **auditoria append-only**.

> Projeto de estudo. Todos os dados são sintéticos.

**Demo ao vivo:** https://ledger-api-sk0d.onrender.com/swagger-ui.html
(plano gratuito do Render: depois de ~15 min parado, o primeiro acesso leva cerca de 50 s para acordar)

**Stack:** Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Spring Data JPA · Flyway · Bean Validation · springdoc-openapi · JUnit 5 · Testcontainers · JaCoCo · Docker Compose · GitHub Actions

## Arquitetura

```mermaid
flowchart LR
    Cliente([Cliente HTTP]) --> C

    subgraph API [Spring Boot]
        C[Controllers<br/>Conta · Transferencia · Auditoria]
        S[Services<br/>ContaService · TransferenciaService]
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

    R --> DB[(PostgreSQL<br/>conta · transacao · lancamento<br/>chave_idempotencia · evento_auditoria)]
    F[Flyway] -->|migrações versionadas| DB
```

### Fluxo de uma transferência

```mermaid
sequenceDiagram
    participant Cli as Cliente
    participant T as TransferenciaService
    participant DB as PostgreSQL
    Cli->>T: POST /transferencias (Idempotency-Key)
    T->>DB: BEGIN
    T->>DB: INSERT chave ON CONFLICT DO NOTHING
    alt chave já existia
        T-->>Cli: resposta original (Idempotent-Replayed: true)
    else chave nova
        T->>DB: SELECT conta FOR UPDATE (menor id primeiro)
        T->>DB: SELECT conta FOR UPDATE (maior id)
        T->>DB: SUM(lançamentos) da origem
        T->>DB: INSERT transacao + DEBITO + CREDITO + auditoria
        T->>DB: UPDATE chave com a resposta
        T->>DB: COMMIT
        T-->>Cli: 201 Created
    end
```

Conceitos usados (ACID, idempotência, lock, partidas dobradas...): [docs/GLOSSARIO.md](docs/GLOSSARIO.md).

## Decisões principais

| Tema | Decisão | Por quê |
|---|---|---|
| Dinheiro | `BigDecimal` / `NUMERIC(19,2)` | `double` é binário e não representa 0,10 exatamente |
| Saldo | Nunca armazenado; `SUM` dos lançamentos | Não existe número editável que possa divergir do histórico |
| Partidas dobradas | Todo depósito sai da conta interna **Tesouraria** | A soma de todos os saldos do banco é sempre **zero** |
| Concorrência | Lock pessimista (`SELECT ... FOR UPDATE`) nas contas de cliente, em ordem fixa de id | Impede gasto duplo e evita deadlock A→B / B→A |
| Tesouraria | Lida sem lock | Pode ficar negativa, então não há saldo a proteger; evita que todos os depósitos entrem numa fila única |
| Idempotência | `INSERT ... ON CONFLICT DO NOTHING` na mesma transação | Atômico mesmo com repetições simultâneas; falhas não ficam gravadas |
| Expiração | Job `@Scheduled` (a cada hora) apaga chaves com mais de 24h | A tabela não cresce para sempre; prazo e agenda por variável de ambiente |
| Append-only | Triggers no banco bloqueiam `UPDATE`/`DELETE` | Vale até para SQL manual, não só para a aplicação |
| Erros | Problem Details (RFC 9457) centralizados | Formato padrão; nunca vaza stack trace ou SQL |

## Como rodar

Pré-requisitos: JDK 21 e Docker Desktop rodando.

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
- Saúde: http://localhost:8081/api/v1/saude e http://localhost:8081/actuator/health

### Testes e cobertura

```bash
.\mvnw verify
```

Roda todos os testes contra um PostgreSQL real (Testcontainers) e falha se a cobertura de linhas for menor que 80%. Relatório em `target/site/jacoco/index.html`.

Destaques da suíte:
- `ConcorrenciaIntegracaoTest`: **1.000 transferências simultâneas** entre 10 contas (50 threads) e a soma dos saldos permanece constante. Também dispara 500 saques simultâneos de R$ 1 numa conta com R$ 100: exatamente 100 passam. Prova ainda que um depósito conclui mesmo com outra transação segurando lock na Tesouraria.
- `LimpezaIdempotenciaIntegracaoTest`: chaves com mais de 24h são removidas sem tocar no dinheiro, e a chave expirada volta a ser aceita.
- `IdempotenciaIntegracaoTest`: repetição sequencial e **20 repetições simultâneas** com a mesma chave geram uma única transação.
- `AuditoriaExtratoIntegracaoTest`: o banco recusa `UPDATE`/`DELETE` no histórico.

## Segurança (Operação Cofre)

A API foi auditada atacando a si mesma; cada falha encontrada virou um teste que a reproduz.
Relatório completo com CVSS: [docs/RELATORIO-APPSEC.md](docs/RELATORIO-APPSEC.md). Verificação externa com ZAP, sqlmap e k6: [docs/VERIFICACAO-ZAP-SQLMAP-K6.md](docs/VERIFICACAO-ZAP-SQLMAP-K6.md).

| Defesa | Como |
|---|---|
| Autenticação | Spring Security, HTTP Basic stateless, senhas em BCrypt, usuários iniciais por variáveis `SEED_*` (nunca no Git) |
| Autorização | Papéis `CLIENTE` e `OPERADOR`; cada conta tem dono e o cliente só enxerga e movimenta as próprias (corrige um IDOR, CVSS 8.1) |
| Força bruta | Bloqueio progressivo de login: o tempo de bloqueio dobra a cada falha excedente (CVSS 7.5, corrigido) |
| Injeção de SQL | Consultas parametrizadas, verificadas por teste e por sqlmap |
| Vazamento de erro | 401/403 no mesmo formato Problem Details, sem stack trace nem SQL |

Testes de segurança: `ControleDeAcessoIdorTest`, `ForcaBrutaLoginTest`, `InjecaoSqlTest`, entre outros.

## Endpoints

Todas as rotas abaixo exigem autenticação (HTTP Basic); o cliente só acessa as próprias contas.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/v1/contas` | Cria conta de cliente |
| GET | `/api/v1/contas/{id}` | Consulta conta e saldo |
| GET | `/api/v1/contas/{id}/extrato?page=0&size=20` | Extrato paginado |
| POST | `/api/v1/depositos` | Depósito (header `Idempotency-Key`) |
| POST | `/api/v1/transferencias` | Transferência (header `Idempotency-Key`) |
| GET | `/api/v1/transferencias/{id}` | Consulta transação |
| GET | `/api/v1/auditoria?entidadeId=` | Eventos de auditoria |

Exemplo:

```bash
curl.exe -u <usuario>:<senha> -X POST http://localhost:8081/api/v1/contas -H "Content-Type: application/json" -d "{\"titular\":\"Cliente Sintetico\"}"
```

## Deploy (Render, plano gratuito)

1. Suba o repositório no GitHub.
2. No Render: **New → Blueprint** e selecione o repositório. O `render.yaml` cria o PostgreSQL e o serviço web a partir do `Dockerfile`.
3. Acesse `https://<seu-servico>.onrender.com/swagger-ui.html`.

Instância publicada: https://ledger-api-sk0d.onrender.com. O banco do plano gratuito expira após 30 dias e precisa ser recriado.

## Limitações conhecidas

- Autenticação por HTTP Basic: simples e stateless, mas sem expiração de credencial. Para clientes externos, o acesso por OAuth2/JWT fica no [Gateway de Segurança](https://github.com/gabrielgmln-ops/gateway-seguranca), que fica na frente desta API.
- Moeda única (BRL), sem juros nem tarifas.
- O saldo é um `SUM` sobre todos os lançamentos da conta; com milhões de lançamentos por conta seria preciso um snapshot periódico de saldo.
- Chaves de idempotência valem 24h (`IDEMPOTENCIA_VALIDADE`); depois disso, a mesma chave é aceita como pedido novo.
- Requisições que falham (ex.: saldo insuficiente) não ficam gravadas na tabela de idempotência; reenviar a mesma chave reavalia o pedido.
- O lock pessimista serializa operações numa mesma conta de cliente muito movimentada. A Tesouraria não é travada (não tem saldo a validar), então depósitos não formam fila única.

## Próximos passos

- Validar JWT emitido pelo Gateway diretamente na Ledger (resource server), em vez de HTTP Basic.
- DAST (ZAP baseline) automatizado no CI.
- Estorno como nova transação compensatória.
- Snapshots de saldo e particionamento da tabela de lançamentos.
- Outbox pattern para publicar eventos (Kafka) de forma confiável.
- Métricas e tracing (Micrometer, OpenTelemetry).
