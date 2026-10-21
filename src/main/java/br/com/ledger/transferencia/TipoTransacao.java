// Caminho: src/main/java/br/com/ledger/transferencia/TipoTransacao.java
package br.com.ledger.transferencia;

public enum TipoTransacao {
    /** Entre duas contas de cliente. */
    TRANSFERENCIA,
    /** Da Tesouraria (conta interna) para uma conta de cliente. */
    DEPOSITO
}
