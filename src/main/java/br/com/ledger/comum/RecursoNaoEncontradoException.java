// Caminho: src/main/java/br/com/ledger/comum/RecursoNaoEncontradoException.java
package br.com.ledger.comum;

/** Vira HTTP 404 no GlobalExceptionHandler. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
