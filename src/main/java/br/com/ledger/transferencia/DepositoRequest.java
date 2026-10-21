// Caminho: src/main/java/br/com/ledger/transferencia/DepositoRequest.java
package br.com.ledger.transferencia;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositoRequest(
        @NotNull UUID contaId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal valor,
        @Size(max = 140) String descricao) {
}
