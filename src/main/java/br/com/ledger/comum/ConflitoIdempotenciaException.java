// Caminho: src/main/java/br/com/ledger/comum/ConflitoIdempotenciaException.java
package br.com.ledger.comum;

/**
 * A mesma chave de idempotência foi reenviada com um corpo DIFERENTE.
 * Provavelmente bug no cliente; recusamos em vez de adivinhar qual pedido vale.
 */
public class ConflitoIdempotenciaException extends RuntimeException {

    public ConflitoIdempotenciaException() {
        super("Chave de idempotencia ja utilizada com uma requisicao diferente");
    }
}
