// Caminho: src/main/java/br/com/ledger/conta/Conta.java
package br.com.ledger.conta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Conta bancária. Propositalmente NÃO tem campo de saldo: o saldo é sempre
 * calculado a partir dos lançamentos, então não existe um número que possa
 * ser editado e ficar fora de sincronia com o histórico.
 */
@Entity
@Table(name = "conta")
public class Conta {

    /** Id fixo da conta interna criada na migração V3. */
    public static final UUID TESOURARIA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // UUID em vez de número sequencial: não revela quantas contas o banco tem
    // e não permite "adivinhar" a conta vizinha incrementando o id.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String titular;

    // EnumType.STRING grava "CLIENTE" e não 0/1: reordenar o enum não corrompe dados.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoConta tipo;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    // Dono da conta (FK para usuario). Nulo para contas internas, como a Tesouraria,
    // que não pertencem a nenhum cliente. É a base da autorização por dono (anti-IDOR).
    @Column(name = "usuario_id")
    private UUID usuarioId;

    /** Construtor exigido pelo JPA; protected para o código de negócio não usá-lo. */
    protected Conta() {
    }

    public Conta(String titular, TipoConta tipo) {
        this.titular = titular;
        this.tipo = tipo;
        this.criadaEm = Instant.now();
    }

    public boolean permiteSaldoNegativo() {
        return tipo == TipoConta.INTERNA;
    }

    /** Define o dono (cliente) da conta. Usado na criação. */
    public void definirDono(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public UUID getId() {
        return id;
    }

    public String getTitular() {
        return titular;
    }

    public TipoConta getTipo() {
        return tipo;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }
}
