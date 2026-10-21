// Caminho: src/main/java/br/com/ledger/transferencia/ResultadoOperacao.java
package br.com.ledger.transferencia;

/**
 * Resposta da operação mais a informação de que ela é uma repetição
 * (mesma chave de idempotência). O controller usa isso no cabeçalho HTTP.
 */
public record ResultadoOperacao(TransferenciaResponse resposta, boolean repetida) {
}
