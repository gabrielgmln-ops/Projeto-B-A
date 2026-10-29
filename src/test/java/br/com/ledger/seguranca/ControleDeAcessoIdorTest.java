// Caminho: src/test/java/br/com/ledger/seguranca/ControleDeAcessoIdorTest.java
package br.com.ledger.seguranca;

import br.com.ledger.IntegracaoBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Etapa 2 - IDOR / Controle de acesso quebrado.
 *
 * Cada CLIENTE só pode ver e movimentar as PRÓPRIAS contas. Este teste cria dois
 * clientes (ana e bruno), cada um com sua conta, e prova que:
 *  - ler a conta do outro responde 404 (não 403): não confirmamos nem que a conta
 *    existe, impedindo a enumeração de contas alheias;
 *  - depositar na conta do outro é rejeitado com 403;
 *  - a trilha de auditoria (recurso interno) é negada a um CLIENTE com 403.
 *
 * Na versão SEM a correção (sem dono e sem ControleAcesso) estas três chamadas
 * respondiam 200/201 — o cérebro da vulnerabilidade.
 */
class ControleDeAcessoIdorTest extends IntegracaoBase {

    private UUID contaDaAna;
    private UUID contaDoBruno;

    @BeforeEach
    void preparar() throws Exception {
        criarUsuarioCliente("ana");
        criarUsuarioCliente("bruno");
        contaDaAna = criarContaComo("ana", "Ana");
        contaDoBruno = criarContaComo("bruno", "Bruno");
    }

    @Test
    void clienteLeASuaPropriaConta() throws Exception {
        mockMvc.perform(get("/api/v1/contas/{id}", contaDaAna).with(user("ana").roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contaDaAna.toString()));
    }

    @Test
    void clienteNaoLeAContaDeOutroERecebe404() throws Exception {
        mockMvc.perform(get("/api/v1/contas/{id}", contaDoBruno).with(user("ana").roles("CLIENTE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void clienteNaoLeOExtratoDeOutroERecebe404() throws Exception {
        mockMvc.perform(get("/api/v1/contas/{id}/extrato", contaDoBruno).with(user("ana").roles("CLIENTE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void clienteNaoDepositaNaContaDeOutroERecebe403() throws Exception {
        mockMvc.perform(post("/api/v1/depositos")
                        .with(user("ana").roles("CLIENTE"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"contaId\":\"" + contaDoBruno + "\",\"valor\":100.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void clienteNaoAcessaTrilhaDeAuditoria() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria").with(user("ana").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    // ----- apoio -----

    private void criarUsuarioCliente(String username) {
        if (!usuarioRepository.existsByUsername(username)) {
            usuarioRepository.save(new Usuario(username, passwordEncoder.encode("sintetica"), Papel.CLIENTE));
        }
    }

    private UUID criarContaComo(String username, String titular) throws Exception {
        String json = mockMvc.perform(post("/api/v1/contas")
                        .with(user(username).roles("CLIENTE"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"titular\":\"" + titular + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }
}
