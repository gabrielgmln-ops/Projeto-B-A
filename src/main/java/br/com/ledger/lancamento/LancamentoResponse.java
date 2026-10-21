// Caminho: src/main/java/br/com/ledger/lancamento/LancamentoResponse.java
package br.com.ledger.lancamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Uma linha do extrato. DTO separado da entidade para não expor o modelo interno. */
public record LancamentoResponse(
        Long id,
        UUID transacaoId,
        TipoLancamento tipo,
        BigDecimal valor,
        Instant criadoEm) {

    public static LancamentoResponse de(Lancamento lancamento) {
        return new LancamentoResponse(
                lancamento.getId(),
                // getId() de um proxy LAZY não dispara consulta ao banco.
                lancamento.getTransacao().getId(),
                lancamento.getTipo(),
                lancamento.getValor(),
                lancamento.getCriadoEm());
    }
}
