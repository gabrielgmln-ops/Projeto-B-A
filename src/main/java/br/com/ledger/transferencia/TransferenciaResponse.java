// Caminho: src/main/java/br/com/ledger/transferencia/TransferenciaResponse.java
package br.com.ledger.transferencia;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferenciaResponse(
        UUID id,
        TipoTransacao tipo,
        UUID contaOrigemId,
        UUID contaDestinoId,
        BigDecimal valor,
        String descricao,
        Instant criadaEm) {

    public static TransferenciaResponse de(Transacao t) {
        return new TransferenciaResponse(t.getId(), t.getTipo(), t.getContaOrigemId(),
                t.getContaDestinoId(), t.getValor(), t.getDescricao(), t.getCriadaEm());
    }
}
