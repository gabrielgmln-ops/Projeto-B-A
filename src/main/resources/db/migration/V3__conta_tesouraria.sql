-- Caminho: src/main/resources/db/migration/V3__conta_tesouraria.sql
-- Em partidas dobradas, dinheiro não "surge do nada". Um depósito é um débito
-- na conta interna de Tesouraria e um crédito na conta do cliente.
-- Assim a soma de TODOS os saldos do banco continua sempre igual a zero.
INSERT INTO conta (id, titular, tipo, criada_em)
VALUES ('00000000-0000-0000-0000-000000000001', 'Tesouraria (conta interna)', 'INTERNA', now());
