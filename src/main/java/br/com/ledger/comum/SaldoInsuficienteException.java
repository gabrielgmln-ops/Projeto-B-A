// Caminho: src/main/java/br/com/ledger/comum/SaldoInsuficienteException.java
package br.com.ledger.comum;

public class SaldoInsuficienteException extends RegraNegocioException {

    public SaldoInsuficienteException() {
        // IMPORTANTE: a mensagem não revela o saldo atual. Sem autenticação na v1,
        // qualquer um pode chamar a API, então não vazamos dado financeiro em erro.
        super("Saldo insuficiente na conta de origem");
    }
}
