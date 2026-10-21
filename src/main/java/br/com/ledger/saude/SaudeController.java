// Caminho: src/main/java/br/com/ledger/saude/SaudeController.java
package br.com.ledger.saude;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Endpoint de saúde próprio da API.
 *
 * Por que existir além do /actuator/health? O Actuator é o padrão operacional
 * (Kubernetes, load balancers). Este endpoint é didático: mostra o fluxo
 * HTTP -> controller -> banco em poucas linhas, e serve de primeiro teste.
 *
 * Nesta etapa não há camada service porque não há regra de negócio; a partir
 * da Etapa 2 seguiremos estritamente controller -> service -> repository.
 */
@RestController
@RequestMapping("/api/v1/saude")
public class SaudeController {

    private final JdbcTemplate jdbcTemplate;

    // Injeção por construtor: a dependência é obrigatória e explícita,
    // e o teste pode passar um "dublê" (mock) sem nenhuma mágica.
    public SaudeController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<SaudeResponse> verificar() {
        try {
            // "SELECT 1" é a consulta mais barata possível: só prova que há conexão.
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new SaudeResponse("UP", "UP", Instant.now()));
        } catch (Exception e) {
            // IMPORTANTE: não devolvemos e.getMessage() ao cliente. Mensagens de erro
            // de banco podem vazar host, usuário ou estrutura interna.
            // 503 = "serviço indisponível", o código certo para dependência fora do ar.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SaudeResponse("DOWN", "DOWN", Instant.now()));
        }
    }
}
