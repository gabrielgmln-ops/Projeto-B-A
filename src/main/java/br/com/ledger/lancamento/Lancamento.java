// Caminho: src/main/java/br/com/ledger/lancamento/Lancamento.java
package br.com.ledger.lancamento;

import br.com.ledger.conta.Conta;
import br.com.ledger.transferencia.Transacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma linha do livro-razão. Cada transação gera exatamente dois lançamentos:
 * um DEBITO na origem e um CREDITO no destino, com o mesmo valor.
 *
 * @Immutable: o Hibernate nunca gera UPDATE para esta entidade. No banco,
 * a trigger da V2 reforça a mesma regra.
 */
@Entity
@Immutable
@Table(name = "lancamento")
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: carregar a transação inteira só quando alguém realmente pedir.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transacao_id", nullable = false)
    private Transacao transacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoLancamento tipo;

    // IMPORTANTE (dinheiro): BigDecimal, nunca double. double é binário e não
    // representa 0,10 exatamente (0.1 + 0.2 = 0.30000000000000004). Em milhões
    // de operações esses centavos "fantasmas" viram diferença contábil real.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Lancamento() {
    }

    public Lancamento(Transacao transacao, Conta conta, TipoLancamento tipo, BigDecimal valor) {
        this.transacao = transacao;
        this.conta = conta;
        this.tipo = tipo;
        this.valor = valor;
        this.criadoEm = transacao.getCriadaEm();
    }

    public Long getId() {
        return id;
    }

    public Transacao getTransacao() {
        return transacao;
    }

    public Conta getConta() {
        return conta;
    }

    public TipoLancamento getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
