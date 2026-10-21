// Caminho: src/main/java/br/com/ledger/lancamento/TipoLancamento.java
package br.com.ledger.lancamento;

public enum TipoLancamento {
    /** Saída de dinheiro da conta (subtrai do saldo). */
    DEBITO,
    /** Entrada de dinheiro na conta (soma ao saldo). */
    CREDITO
}
