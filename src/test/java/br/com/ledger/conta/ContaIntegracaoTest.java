// Caminho: src/test/java/br/com/ledger/conta/ContaIntegracaoTest.java
package br.com.ledger.conta;

import br.com.ledger.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContaIntegracaoTest extends IntegracaoBase {

    @Test
    void deveCriarContaComSaldoZeroELocation() throws Exception {
        mockMvc.perform(post("/api/v1/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titular\":\"Cliente Sintetico Um\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/contas/")))
                .andExpect(jsonPath("$.titular").value("Cliente Sintetico Um"))
                .andExpect(jsonPath("$.tipo").value("CLIENTE"))
                .andExpect(jsonPath("$.saldo").value(0));
    }

    @Test
    void deveConsultarContaComSaldoDerivado() throws Exception {
        UUID conta = criarConta("Cliente Sintetico Dois");
        depositar(conta, "150.75");

        mockMvc.perform(get("/api/v1/contas/{id}", conta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conta.toString()))
                .andExpect(jsonPath("$.saldo").value(150.75));
    }

    @Test
    void deveRetornar404ParaContaInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/contas/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso nao encontrado"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deveRetornar400ComErrosDeCampoQuandoTitularVazio() throws Exception {
        mockMvc.perform(post("/api/v1/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titular\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.titular").exists());
    }

    @Test
    void deveRetornar400ParaJsonMalformado() throws Exception {
        mockMvc.perform(post("/api/v1/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{titular"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400ParaIdQueNaoEhUuid() throws Exception {
        mockMvc.perform(get("/api/v1/contas/nao-e-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", endsWith("'id'")));
    }
}
