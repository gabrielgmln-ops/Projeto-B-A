// Caminho: src/test/java/br/com/ledger/seguranca/RespostaErroSegurancaTest.java
package br.com.ledger.seguranca;

import br.com.ledger.IntegracaoBase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Etapa 5 - Vazamento de informação em mensagens de erro.
 *
 * Prova que as respostas de erro de segurança são padronizadas (RFC 9457,
 * application/problem+json), genéricas e sem detalhes técnicos:
 *  - requisição sem autenticação -> 401 com corpo JSON genérico;
 *  - cliente sem permissão -> 403 com corpo JSON genérico;
 *  - em nenhum caso o corpo contém stack trace, nome de classe ou SQL.
 */
class RespostaErroSegurancaTest extends IntegracaoBase {

    @Test
    void naoAutenticadoRecebe401PadronizadoEGenerico() throws Exception {
        String corpo = mockMvc.perform(get("/api/v1/contas/{id}", UUID.randomUUID()).with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Nao autenticado"))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("Exception", "java.", "org.springframework", "select", "SELECT");
    }

    @Test
    void semPermissaoRecebe403PadronizadoEGenerico() throws Exception {
        String corpo = mockMvc.perform(get("/api/v1/auditoria").with(user("intruso").roles("CLIENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acesso negado"))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("Exception", "java.", "org.springframework");
    }
}
