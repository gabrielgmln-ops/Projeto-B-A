// Caminho: src/main/java/br/com/ledger/transferencia/Transacao.java
package br.com.ledger.transferencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * O "fato" de negócio (fulano transferiu X para ciclano). Os efeitos no saldo
 * ficam nos dois Lancamentos ligados a ela. Imutável, como todo o histórico.
 */
@Entity
@Immutable
@Table(name = "transacao")
public class Transacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTransacao tipo;

    @Column(name = "conta_origem_id", nullable = false)
    private UUID contaOrigemId;

    @Column(name = "conta_destino_id", nullable = false)
    private UUID contaDestinoId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(length = 140)
    private String descricao;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected Transacao() {
    }

    public Transacao(TipoTransacao tipo, UUID contaOrigemId, UUID contaDestinoId,
                     BigDecimal valor, String descricao) {
        this.tipo = tipo;
        this.contaOrigemId = contaOrigemId;
        this.contaDestinoId = contaDestinoId;
        this.valor = valor;
        this.descricao = descricao;
        this.criadaEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public TipoTransacao getTipo() {
        return tipo;
    }

    public UUID getContaOrigemId() {
        return contaOrigemId;
    }

    public UUID getContaDestinoId() {
        return contaDestinoId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
