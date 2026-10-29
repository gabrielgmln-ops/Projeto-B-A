// Caminho: src/test/java/br/com/ledger/seguranca/ForcaBrutaLoginTest.java
package br.com.ledger.seguranca;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Etapa 4 - Força bruta em autenticação.
 *
 * Este teste NÃO usa o arreio de autenticação (SegurancaTestSupport): ele manda
 * credenciais HTTP Basic de verdade, para exercitar o fluxo real de login e o
 * bloqueio progressivo.
 *
 * Limites reduzidos via @TestPropertySource (3 tentativas, bloqueio de 30s) para
 * o teste ser rápido e determinístico. Prova que:
 *  - com a senha correta e sem bloqueio, o login é aceito (resposta != 401);
 *  - após estourar o limite de tentativas erradas, a conta é bloqueada;
 *  - durante o bloqueio, até a SENHA CORRETA é recusada (401).
 *
 * Na versão sem a defesa, a senha correta passaria sempre — permitindo varredura
 * ilimitada de senhas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ledger.seguranca.login.max-tentativas=3",
        "ledger.seguranca.login.bloqueio-base-segundos=30",
        "ledger.seguranca.login.bloqueio-maximo-segundos=60"
})
class ForcaBrutaLoginTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private static final String VITIMA = "vitima-forca-bruta";
    private static final String SENHA_CORRETA = "SenhaCerta#2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedVitima() {
        if (!usuarioRepository.existsByUsername(VITIMA)) {
            usuarioRepository.save(new Usuario(VITIMA, passwordEncoder.encode(SENHA_CORRETA), Papel.CLIENTE));
        }
    }

    @Test
    void bloqueiaLoginAposEstourarOLimiteDeTentativas() throws Exception {
        // Endpoint qualquer que exige autenticação; o que importa é o resultado do login.
        var alvo = "/api/v1/contas/" + UUID.randomUUID();

        // 1) Senha correta, sem bloqueio: login aceito (cliente autenticado -> 404 no recurso,
        //    e nunca 401). Isto fixa a linha de base.
        mockMvc.perform(get(alvo).with(httpBasic(VITIMA, SENHA_CORRETA)))
                .andExpect(status().isNotFound());

        // 2) Três tentativas com senha errada: cada uma é 401 e alimenta o contador.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(alvo).with(httpBasic(VITIMA, "senha-errada-" + i)))
                    .andExpect(status().isUnauthorized());
        }

        // 3) Agora bloqueado: a SENHA CORRETA também é recusada (401) durante a janela.
        mockMvc.perform(get(alvo).with(httpBasic(VITIMA, SENHA_CORRETA)))
                .andExpect(status().isUnauthorized());
    }
}
