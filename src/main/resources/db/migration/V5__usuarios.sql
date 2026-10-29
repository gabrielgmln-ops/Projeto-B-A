-- Caminho: src/main/resources/db/migration/V5__usuarios.sql
-- Tabela de usuários que se autenticam na API (Etapa 0 do endurecimento de segurança).
--
-- Regras de integridade ficam TAMBÉM no banco, como no resto do schema:
--  - username único (UNIQUE), para não haver dois logins iguais;
--  - papel restrito a valores conhecidos (CHECK), espelhando o enum Papel.java.
-- Não há senha em texto: apenas o hash (preenchido pela aplicação, nunca por este script).

CREATE TABLE usuario (
    id          UUID         PRIMARY KEY,
    username    VARCHAR(60)  NOT NULL UNIQUE,
    -- Hash BCrypt prefixado ({bcrypt}...): ~68 chars; 100 dá folga para troca de algoritmo.
    senha_hash  VARCHAR(100) NOT NULL,
    papel       VARCHAR(20)  NOT NULL CHECK (papel IN ('CLIENTE', 'OPERADOR')),
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ  NOT NULL
);

-- Busca por username acontece a cada login; o índice do UNIQUE já atende essa consulta.
