// Caminho: src/test/java/br/com/ledger/saude/SaudeControllerTest.java
package br.com.ledger.saude;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste "fatiado" da camada web: @WebMvcTest sobe apenas o Spring MVC e este
 * controller, sem banco de dados. Por isso roda em segundos e não precisa de Docker.
 * O banco é substituído por um mock (dublê controlado pelo teste).
 */
// addFilters = false: esta é uma fatia web focada no comportamento do endpoint
// de saúde (UP/DOWN), não na segurança. Desligar os filtros do Spring Security
// evita o 401 do contexto de teste fatiado; o controle de acesso real é coberto
// pelos testes de integração dedicados (ControleDeAcessoIdorTest, etc.).
@WebMvcTest(SaudeController.class)
@AutoConfigureMockMvc(addFilters = false)
class SaudeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveRetornar200QuandoBancoResponde() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        mockMvc.perform(get("/api/v1/saude"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.banco").value("UP"));
    }

    @Test
    void deveRetornar503QuandoBancoEstaFora() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new DataAccessResourceFailureException("conexão recusada"));

        mockMvc.perform(get("/api/v1/saude"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
}
