// Caminho: src/test/java/br/com/ledger/idempotencia/LimpezaIdempotenciaIntegracaoTest.java
package br.com.ledger.idempotencia;

import br.com.ledger.IntegracaoBase;
import br.com.ledger.transferencia.TransferenciaController;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LimpezaIdempotenciaIntegracaoTest extends IntegracaoBase {

    @Autowired
    private LimpezaIdempotenciaJob limpeza;

    @Autowired
    private ChaveIdempotenciaRepository chaveRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void removeSoAsChavesMaisAntigasQueAValidade() throws Exception {
        UUID conta = criarConta("Limpeza");
        String chaveAntiga = UUID.randomUUID().toString();
        String chaveRecente = UUID.randomUUID().toString();
        depositar(conta, "1.00", chaveAntiga).andExpect(status().isCreated());
        depositar(conta, "1.00", chaveRecente).andExpect(status().isCreated());

        // Simula a passagem do tempo: a chave "antiga" passa a ter 2 dias (validade padrão: 24h).
        envelhecer(chaveAntiga);

        assertThat(limpeza.executar()).isGreaterThanOrEqualTo(1);

        assertThat(chaveRepository.existsById(chaveAntiga)).isFalse();
        assertThat(chaveRepository.existsById(chaveRecente)).isTrue();
        // Expirar a chave NÃO apaga dinheiro: os dois depósitos continuam no saldo.
        assertThat(saldo(conta)).isEqualByComparingTo("2.00");
    }

    @Test
    void depoisDeExpiradaAChaveVoltaASerUmPedidoNovo() throws Exception {
        UUID conta = criarConta("Reuso");
        String chave = UUID.randomUUID().toString();

        String primeiro = depositar(conta, "5.00", chave)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        envelhecer(chave);
        limpeza.executar();

        String segundo = depositar(conta, "5.00", chave)
                .andExpect(status().isCreated())
                .andExpect(header().string(TransferenciaController.CABECALHO_REPETIDA, "false"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(segundo, "$.id")).isNotEqualTo(JsonPath.read(primeiro, "$.id"));
        assertThat(saldo(conta)).isEqualByComparingTo("10.00");
    }

    private void envelhecer(String chave) {
        // chave_idempotencia não é append-only (só o histórico contábil é), então o UPDATE é permitido.
        jdbcTemplate.update("UPDATE chave_idempotencia SET criada_em = now() - interval '2 days' WHERE chave = ?", chave);
    }
}
