-- Caminho: src/main/resources/db/migration/V6__conta_dono.sql
-- Autorização por dono (combate a IDOR): cada conta de cliente passa a ter um dono.
--
-- Coluna NULLable de propósito: a Tesouraria (conta interna, criada na V3) não
-- pertence a nenhum cliente, e contas criadas antes desta migração não têm dono
-- conhecido. A aplicação passa a exigir dono em contas novas de CLIENTE.
ALTER TABLE conta
    ADD COLUMN usuario_id UUID REFERENCES usuario (id);

-- Índice para a consulta "contas deste usuário" e para a verificação de posse.
CREATE INDEX idx_conta_usuario ON conta (usuario_id);
