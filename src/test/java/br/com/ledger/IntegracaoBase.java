// Caminho: src/test/java/br/com/ledger/IntegracaoBase.java
package br.com.ledger;

import br.com.ledger.lancamento.LancamentoRepository;
import br.com.ledger.seguranca.Papel;
import br.com.ledger.seguranca.SegurancaTestSupport;
import br.com.ledger.seguranca.Usuario;
import br.com.ledger.seguranca.UsuarioAtual;
import br.com.ledger.seguranca.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
// Importa o arreio de autenticação: todas as requisições deste harness já saem
// autenticadas, mantendo a suíte de negócio verde depois de ligar o Spring Security.
@Import(SegurancaTestSupport.class)
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

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /**
     * Garante que o usuário autenticado padrão do harness (SegurancaTestSupport)
     * exista no banco como OPERADOR. É necessário porque, com a autorização por
     * dono (Etapa 2), criar conta passa a carimbar o dono = usuário autenticado,
     * e OPERADOR tem acesso amplo (mantendo a suíte de negócio verde).
     */
    @BeforeEach
    void garantirUsuarioDeTesteEContexto() {
        if (!usuarioRepository.existsByUsername(SegurancaTestSupport.USUARIO_TESTE)) {
            usuarioRepository.save(new Usuario(SegurancaTestSupport.USUARIO_TESTE,
                    passwordEncoder.encode("irrelevante-mock"), Papel.OPERADOR));
        }
        // Contexto de segurança para as chamadas feitas DIRETO no service (sem HTTP),
        // como os testes de concorrência. As chamadas via MockMvc têm o seu próprio
        // usuário por requisição (SegurancaTestSupport), que prevalece.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                SegurancaTestSupport.USUARIO_TESTE, "n/a",
                AuthorityUtils.createAuthorityList(UsuarioAtual.PAPEL_OPERADOR)));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Pool de threads que PROPAGA o contexto de segurança da thread atual para as
     * threads de trabalho. Sem isto, chamadas ao service dentro das threads
     * veriam um contexto vazio e a autorização por dono as rejeitaria.
     */
    protected ExecutorService poolSeguro(int threads) {
        // Contexto explícito de OPERADOR aplicado a TODAS as tarefas, independente do
        // estado da thread atual (as requisições MockMvc limpam o contexto ao terminar).
        Authentication auth = new UsernamePasswordAuthenticationToken(SegurancaTestSupport.USUARIO_TESTE,
                "n/a", AuthorityUtils.createAuthorityList(UsuarioAtual.PAPEL_OPERADOR));
        SecurityContext contexto = new SecurityContextImpl(auth);
        return new DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(threads), contexto);
    }

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
