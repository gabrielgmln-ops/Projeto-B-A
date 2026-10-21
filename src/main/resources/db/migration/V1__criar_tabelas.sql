-- Caminho: src/main/resources/db/migration/V1__criar_tabelas.sql
-- Schema do ledger. As regras de integridade ficam TAMBÉM no banco (CHECK, FK),
-- não só no Java: se um bug ou um script manual tentar gravar dado inválido,
-- o próprio PostgreSQL recusa.

CREATE TABLE conta (
    id         UUID PRIMARY KEY,
    titular    VARCHAR(120) NOT NULL,
    -- CLIENTE não pode ficar negativa; INTERNA (tesouraria) é a contrapartida dos depósitos.
    tipo       VARCHAR(20)  NOT NULL CHECK (tipo IN ('CLIENTE', 'INTERNA')),
    criada_em  TIMESTAMPTZ  NOT NULL
);
-- Repare: NÃO existe coluna "saldo". O saldo é sempre derivado dos lançamentos.

CREATE TABLE transacao (
    id                UUID PRIMARY KEY,
    tipo              VARCHAR(20)   NOT NULL CHECK (tipo IN ('TRANSFERENCIA', 'DEPOSITO')),
    conta_origem_id   UUID          NOT NULL REFERENCES conta (id),
    conta_destino_id  UUID          NOT NULL REFERENCES conta (id),
    -- NUMERIC é decimal exato (como BigDecimal). Nunca usar REAL/DOUBLE para dinheiro.
    valor             NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    descricao         VARCHAR(140),
    criada_em         TIMESTAMPTZ   NOT NULL,
    CHECK (conta_origem_id <> conta_destino_id)
);

CREATE TABLE lancamento (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transacao_id  UUID           NOT NULL REFERENCES transacao (id),
    conta_id      UUID           NOT NULL REFERENCES conta (id),
    tipo          VARCHAR(10)    NOT NULL CHECK (tipo IN ('DEBITO', 'CREDITO')),
    -- Valor sempre positivo; o sinal vem do tipo (DEBITO subtrai, CREDITO soma).
    valor         NUMERIC(19, 2) NOT NULL CHECK (valor > 0),
    criado_em     TIMESTAMPTZ    NOT NULL
);
-- Índice que atende o cálculo de saldo e o extrato paginado (mais recentes primeiro).
CREATE INDEX idx_lancamento_conta ON lancamento (conta_id, id DESC);
CREATE INDEX idx_lancamento_transacao ON lancamento (transacao_id);

CREATE TABLE chave_idempotencia (
    -- A PRIMARY KEY é o que garante, no nível do banco, que a mesma chave
    -- só pode ser processada uma vez, mesmo com requisições simultâneas.
    chave            VARCHAR(100) PRIMARY KEY,
    hash_requisicao  VARCHAR(64)  NOT NULL,
    transacao_id     UUID REFERENCES transacao (id),
    status_http      INT,
    resposta_json    TEXT,
    criada_em        TIMESTAMPTZ  NOT NULL
);

CREATE TABLE evento_auditoria (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo         VARCHAR(50) NOT NULL,
    entidade     VARCHAR(50) NOT NULL,
    entidade_id  VARCHAR(64) NOT NULL,
    dados        TEXT        NOT NULL,
    ocorrido_em  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_auditoria_entidade ON evento_auditoria (entidade, entidade_id);
