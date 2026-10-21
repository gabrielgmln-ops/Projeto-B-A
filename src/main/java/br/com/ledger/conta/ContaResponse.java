// Caminho: src/main/java/br/com/ledger/conta/ContaResponse.java
package br.com.ledger.conta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ContaResponse(
        UUID id,
        String titular,
        TipoConta tipo,
        BigDecimal saldo,
        Instant criadaEm) {

    public static ContaResponse de(Conta conta, BigDecimal saldo) {
        return new ContaResponse(conta.getId(), conta.getTitular(), conta.getTipo(), saldo, conta.getCriadaEm());
    }
}
