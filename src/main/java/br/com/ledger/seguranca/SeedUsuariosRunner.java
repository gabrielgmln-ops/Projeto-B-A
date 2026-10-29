// Caminho: src/main/java/br/com/ledger/seguranca/SeedUsuariosRunner.java
package br.com.ledger.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Cria usuários sintéticos de desenvolvimento ao subir a aplicação.
 *
 * Princípios aplicados:
 * - Segredos por variável de ambiente: as senhas vêm das propriedades
 *   ledger.seguranca.seed.* (alimentadas pelas variáveis SEED_* do .env).
 *   NENHUMA senha é escrita no código ou no Git.
 * - Hash no cadastro: a senha é passada pelo PasswordEncoder (BCrypt) antes de
 *   ir ao banco; o texto puro nunca é persistido.
 * - Idempotente: só cria se o username ainda não existir. Rodar de novo não duplica.
 *
 * Se as variáveis não estiverem definidas (ex.: em alguns ambientes de teste),
 * o runner simplesmente não cria nada e registra um aviso — a aplicação sobe
 * normalmente.
 */
@Component
public class SeedUsuariosRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedUsuariosRunner.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ledger.seguranca.seed.operador.username:}")
    private String operadorUsername;
    @Value("${ledger.seguranca.seed.operador.senha:}")
    private String operadorSenha;

    @Value("${ledger.seguranca.seed.cliente.username:}")
    private String clienteUsername;
    @Value("${ledger.seguranca.seed.cliente.senha:}")
    private String clienteSenha;

    public SeedUsuariosRunner(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        criarSeNaoExistir(operadorUsername, operadorSenha, Papel.OPERADOR);
        criarSeNaoExistir(clienteUsername, clienteSenha, Papel.CLIENTE);
    }

    private void criarSeNaoExistir(String username, String senha, Papel papel) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(senha)) {
            log.warn("Seed de usuario {} ignorado: defina as variaveis SEED_* no .env.", papel);
            return;
        }
        if (usuarioRepository.existsByUsername(username)) {
            return; // já existe: nada a fazer (idempotente).
        }
        Usuario usuario = new Usuario(username, passwordEncoder.encode(senha), papel);
        usuarioRepository.save(usuario);
        log.info("Usuario sintetico '{}' ({}) criado.", username, papel);
    }
}
