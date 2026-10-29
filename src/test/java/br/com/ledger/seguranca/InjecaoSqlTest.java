// Caminho: src/test/java/br/com/ledger/seguranca/InjecaoSqlTest.java
package br.com.ledger.seguranca;

import br.com.ledger.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Etapa 1 - Injeção de SQL.
 *
 * A API usa exclusivamente consultas parametrizadas (Spring Data e @Query com
 * :parâmetros) e identificadores tipados como UUID. Este teste PROVA que cargas
 * clássicas de injeção são tratadas como DADO literal, não como SQL:
 *
 *  - no filtro de texto livre do extrato de auditoria (único parâmetro String
 *    que chega a uma consulta), um payload de injeção não derruba tabela nem
 *    vaza linhas: volta uma página vazia e as tabelas continuam de pé;
 *  - num path que espera UUID, o texto malicioso nem chega ao banco: é barrado
 *    com 400 (Requisicao invalida) pela conversão de tipo.
 */
class InjecaoSqlTest extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void payloadDeInjecaoNoFiltroEhTratadoComoTextoLiteral() throws Exception {
        // Payload que, se concatenado no SQL, tentaria apagar a tabela de usuários.
        String payload = "x'; DROP TABLE usuario; --";

        mockMvc.perform(get("/api/v1/auditoria").param("entidadeId", payload))
                .andExpect(status().isOk())
                // Nenhum evento tem esse entidadeId: a consulta parametrizada só devolve vazio.
                .andExpect(jsonPath("$.conteudo").isEmpty());

        // Prova definitiva: a tabela 'usuario' continua existindo (nada foi dropado).
        Integer tabelas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'usuario'",
                Integer.class);
        assertThat(tabelas).isEqualTo(1);
    }

    @Test
    void payloadDeInjecaoTautologicaNaoVazaOutrosRegistros() throws Exception {
        // "' OR '1'='1" só retornaria tudo se houvesse concatenação de string no SQL.
        mockMvc.perform(get("/api/v1/auditoria").param("entidadeId", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").isEmpty());
    }

    @Test
    void injecaoEmPathDeUuidEhBarradaAntesDoBanco() throws Exception {
        // O path espera UUID; texto com SQL vira 400 na conversão, sem tocar o banco.
        mockMvc.perform(get("/api/v1/contas/{id}", "1 OR 1=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"));
    }
}
