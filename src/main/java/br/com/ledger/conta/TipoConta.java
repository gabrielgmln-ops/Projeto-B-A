// Caminho: src/main/java/br/com/ledger/conta/TipoConta.java
package br.com.ledger.conta;

public enum TipoConta {
    /** Conta de cliente: nunca pode ficar com saldo negativo. */
    CLIENTE,
    /** Conta interna do banco (ex.: Tesouraria), contrapartida dos depósitos. */
    INTERNA
}
