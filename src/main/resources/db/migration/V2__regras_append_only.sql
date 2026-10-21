-- Caminho: src/main/resources/db/migration/V2__regras_append_only.sql
-- IMPORTANTE: lançamentos, transações e auditoria são append-only.
-- O bloqueio é feito por trigger NO BANCO, não só por convenção no Java:
-- nem a aplicação, nem um DBA distraído consegue editar ou apagar o histórico.
-- Para corrigir um erro contábil, faz-se um ESTORNO (novo lançamento), nunca um UPDATE.

CREATE FUNCTION impedir_alteracao() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Tabela % e append-only: operacao % nao permitida', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lancamento_append_only
    BEFORE UPDATE OR DELETE ON lancamento
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao();

CREATE TRIGGER trg_transacao_append_only
    BEFORE UPDATE OR DELETE ON transacao
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao();

CREATE TRIGGER trg_auditoria_append_only
    BEFORE UPDATE OR DELETE ON evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao();
