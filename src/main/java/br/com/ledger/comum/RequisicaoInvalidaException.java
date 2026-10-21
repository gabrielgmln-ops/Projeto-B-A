// Caminho: src/main/java/br/com/ledger/comum/RequisicaoInvalidaException.java
package br.com.ledger.comum;

/** Entrada malformada que a Bean Validation não cobre (ex.: cabeçalho). Vira HTTP 400. */
public class RequisicaoInvalidaException extends RuntimeException {

    public RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
