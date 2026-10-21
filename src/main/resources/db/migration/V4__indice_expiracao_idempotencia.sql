-- Caminho: src/main/resources/db/migration/V4__indice_expiracao_idempotencia.sql
-- A limpeza periódica filtra por criada_em. Sem índice, cada execução varreria
-- a tabela inteira, que é justamente a tabela que cresce sem parar.
CREATE INDEX idx_chave_idempotencia_criada_em ON chave_idempotencia (criada_em);
