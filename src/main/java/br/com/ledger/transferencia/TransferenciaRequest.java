// Caminho: src/main/java/br/com/ledger/transferencia/TransferenciaRequest.java
package br.com.ledger.transferencia;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferenciaRequest(
        @NotNull UUID contaOrigemId,
        @NotNull UUID contaDestinoId,
        // @Digits(fraction = 2): recusa 10.001, pois não existe fração de centavo.
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal valor,
        @Size(max = 140) String descricao) {
}
