// Caminho: src/test/java/br/com/ledger/IntegracaoBase.java
package br.com.ledger;

import br.com.ledger.lancamento.LancamentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: sobe a aplicação inteira contra um PostgreSQL
 * real em Docker (Testcontainers).
 *
 * O container é estático e iniciado uma única vez (padrão "singleton container"):
 * todas as classes de teste compartilham o mesmo banco e o mesmo contexto Spring,
 * o que deixa a suíte muito mais rápida. Como lançamentos são append-only,
 * os testes nunca apagam dados: cada teste cria as próprias contas.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegracaoBase {

    // @ServiceConnection: o Spring Boot lê host, porta, usuário e senha do container
    // e configura o DataSource sozinho. Nenhuma credencial escrita no teste.
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected LancamentoRepository lancamentoRepository;

    protected UUID criarConta(String titular) throws Exception {
        String json = mockMvc.perform(post("/api/v1/contas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("titular", titular))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    protected ResultActions depositar(UUID contaId, String valor, String chave) throws Exception {
        return mockMvc.perform(post("/api/v1/depositos")
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contaId\":\"" + contaId + "\",\"valor\":" + valor + "}"));
    }

    protected void depositar(UUID contaId, String valor) throws Exception {
        depositar(contaId, valor, UUID.randomUUID().toString()).andExpect(status().isCreated());
    }

    protected ResultActions transferir(UUID origem, UUID destino, String valor, String chave) throws Exception {
        return mockMvc.perform(post("/api/v1/transferencias")
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contaOrigemId\":\"" + origem + "\",\"contaDestinoId\":\"" + destino
                        + "\",\"valor\":" + valor + ",\"descricao\":\"teste\"}"));
    }

    protected BigDecimal saldo(UUID contaId) {
        return lancamentoRepository.calcularSaldo(contaId);
    }
}
