// Caminho: src/test/java/br/com/ledger/auditoria/AuditoriaExtratoIntegracaoTest.java
package br.com.ledger.auditoria;

import br.com.ledger.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditoriaExtratoIntegracaoTest extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void extratoEhPaginadoDoMaisRecenteParaOMaisAntigo() throws Exception {
        UUID conta = criarConta("Extrato");
        depositar(conta, "1.00");
        depositar(conta, "2.00");
        depositar(conta, "3.00");

        mockMvc.perform(get("/api/v1/contas/{id}/extrato", conta).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.conteudo[0].valor").value(3.00))
                .andExpect(jsonPath("$.conteudo[0].tipo").value("CREDITO"));

        mockMvc.perform(get("/api/v1/contas/{id}/extrato", conta).param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.conteudo.length()").value(1))
                .andExpect(jsonPath("$.conteudo[0].valor").value(1.00));
    }

    @Test
    void extratoDeContaInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/contas/{id}/extrato", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void operacoesGeramEventosDeAuditoria() throws Exception {
        UUID conta = criarConta("Auditada");

        mockMvc.perform(get("/api/v1/auditoria").param("entidadeId", conta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].tipo").value("CONTA_CRIADA"))
                .andExpect(jsonPath("$.conteudo[0].entidade").value("Conta"));

        depositar(conta, "9.99");

        mockMvc.perform(get("/api/v1/auditoria").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].tipo").value("DEPOSITO_REALIZADA"));
    }

    /** O banco recusa UPDATE e DELETE no histórico, mesmo vindo de SQL direto. */
    @Test
    void historicoEhAppendOnlyNoBanco() throws Exception {
        UUID conta = criarConta("Imutavel");
        depositar(conta, "5.00");

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE evento_auditoria SET tipo = 'X'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM lancamento WHERE conta_id = ?", conta))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE transacao SET valor = 1"))
                .hasMessageContaining("append-only");
    }
}
