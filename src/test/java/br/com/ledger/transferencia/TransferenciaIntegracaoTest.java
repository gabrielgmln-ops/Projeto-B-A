// Caminho: src/test/java/br/com/ledger/transferencia/TransferenciaIntegracaoTest.java
package br.com.ledger.transferencia;

import br.com.ledger.IntegracaoBase;
import br.com.ledger.conta.Conta;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferenciaIntegracaoTest extends IntegracaoBase {

    @Test
    void deveTransferirEmPartidasDobradas() throws Exception {
        UUID origem = criarConta("Origem");
        UUID destino = criarConta("Destino");
        depositar(origem, "100.00");

        String json = transferir(origem, destino, "30.50", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(header().string(TransferenciaController.CABECALHO_REPETIDA, "false"))
                .andExpect(jsonPath("$.tipo").value("TRANSFERENCIA"))
                .andExpect(jsonPath("$.valor").value(30.50))
                .andReturn().getResponse().getContentAsString();

        assertThat(saldo(origem)).isEqualByComparingTo("69.50");
        assertThat(saldo(destino)).isEqualByComparingTo("30.50");

        // Cada transação gera exatamente dois lançamentos (débito + crédito).
        UUID transacaoId = UUID.fromString(JsonPath.read(json, "$.id"));
        assertThat(lancamentoRepository.countByTransacaoId(transacaoId)).isEqualTo(2);

        // A prova contábil: a soma de tudo no banco é zero.
        assertThat(lancamentoRepository.somarTodosOsLancamentos()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void depositoDebitaATesouraria() throws Exception {
        UUID conta = criarConta("Depositante");
        BigDecimal tesourariaAntes = saldo(Conta.TESOURARIA_ID);

        depositar(conta, "500.00", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$.contaOrigemId").value(Conta.TESOURARIA_ID.toString()));

        assertThat(saldo(Conta.TESOURARIA_ID)).isEqualByComparingTo(tesourariaAntes.subtract(new BigDecimal("500.00")));
    }

    @Test
    void deveBloquearSaldoNegativo() throws Exception {
        UUID origem = criarConta("Sem dinheiro");
        UUID destino = criarConta("Destino");
        depositar(origem, "10.00");

        transferir(origem, destino, "10.01", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Saldo insuficiente na conta de origem"));

        assertThat(saldo(origem)).isEqualByComparingTo("10.00");
        assertThat(saldo(destino)).isEqualByComparingTo("0");
    }

    @Test
    void devePermitirZerarOSaldoExatamente() throws Exception {
        UUID origem = criarConta("Zera");
        UUID destino = criarConta("Destino");
        depositar(origem, "10.00");

        transferir(origem, destino, "10", UUID.randomUUID().toString()).andExpect(status().isCreated());

        assertThat(saldo(origem)).isEqualByComparingTo("0");
    }

    @Test
    void deveRecusarTransferenciaParaAMesmaConta() throws Exception {
        UUID conta = criarConta("Mesma");
        depositar(conta, "10.00");

        transferir(conta, conta, "1.00", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deveRecusarTransferenciaEnvolvendoContaInterna() throws Exception {
        UUID conta = criarConta("Cliente");
        depositar(conta, "10.00");

        transferir(conta, Conta.TESOURARIA_ID, "1.00", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Transferencias so sao permitidas entre contas de cliente"));
    }

    @Test
    void deveRetornar404QuandoContaNaoExiste() throws Exception {
        UUID origem = criarConta("Origem");
        transferir(origem, UUID.randomUUID(), "1.00", UUID.randomUUID().toString())
                .andExpect(status().isNotFound());
    }

    @Test
    void deveValidarValor() throws Exception {
        UUID origem = criarConta("Origem");
        UUID destino = criarConta("Destino");

        transferir(origem, destino, "-5.00", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.valor").exists());
        // Fração de centavo não existe.
        transferir(origem, destino, "1.001", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.valor").exists());
    }

    @Test
    void deveExigirCabecalhoIdempotencyKey() throws Exception {
        UUID origem = criarConta("Origem");
        UUID destino = criarConta("Destino");

        mockMvc.perform(post("/api/v1/transferencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contaOrigemId\":\"" + origem + "\",\"contaDestinoId\":\"" + destino
                                + "\",\"valor\":1.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveConsultarTransacaoPorId() throws Exception {
        UUID conta = criarConta("Consulta");
        String json = depositar(conta, "5.00", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");

        mockMvc.perform(get("/api/v1/transferencias/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.valor").value(5.00));

        mockMvc.perform(get("/api/v1/transferencias/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
