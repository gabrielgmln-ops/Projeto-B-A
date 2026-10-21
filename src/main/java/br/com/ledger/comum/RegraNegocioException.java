// Caminho: src/main/java/br/com/ledger/comum/RegraNegocioException.java
package br.com.ledger.comum;

/**
 * Requisição bem formada, mas proibida pelas regras do banco. Vira HTTP 422.
 * É RuntimeException de propósito: o @Transactional do Spring só faz rollback
 * automático para exceções não checadas.
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
