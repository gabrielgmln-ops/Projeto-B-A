// Caminho: src/main/java/br/com/ledger/comum/AcessoNegadoException.java
package br.com.ledger.comum;

/**
 * Operação bem formada e autenticada, porém o usuário não tem permissão sobre
 * o recurso (ex.: tentar movimentar uma conta que não é sua). Vira HTTP 403.
 *
 * Observação de segurança: usamos 403 apenas para OPERAÇÕES sobre um recurso que
 * o usuário sabe existir. Para LEITURA de contas de terceiros preferimos 404
 * (RecursoNaoEncontradoException), para não revelar que a conta existe.
 */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
