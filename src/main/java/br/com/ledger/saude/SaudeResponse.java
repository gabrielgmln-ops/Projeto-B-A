// Caminho: src/main/java/br/com/ledger/saude/SaudeResponse.java
package br.com.ledger.saude;

import java.time.Instant;

/**
 * DTO de resposta do endpoint de saúde.
 *
 * "record" é uma classe imutável e enxuta: o Java gera construtor, getters,
 * equals, hashCode e toString. Ideal para DTOs, que só transportam dados.
 */
public record SaudeResponse(String status, String banco, Instant verificadoEm) {
}
