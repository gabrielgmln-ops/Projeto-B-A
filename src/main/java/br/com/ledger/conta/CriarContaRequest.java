// Caminho: src/main/java/br/com/ledger/conta/CriarContaRequest.java
package br.com.ledger.conta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo do POST /api/v1/contas. Só contas CLIENTE são criadas pela API. */
public record CriarContaRequest(
        @NotBlank @Size(max = 120) String titular) {
}
