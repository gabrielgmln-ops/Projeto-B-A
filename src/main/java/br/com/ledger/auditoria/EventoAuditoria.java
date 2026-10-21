// Caminho: src/main/java/br/com/ledger/auditoria/EventoAuditoria.java
package br.com.ledger.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Registro de auditoria: só se acrescenta, nunca se altera ou apaga. */
@Entity
@Immutable
@Table(name = "evento_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false, length = 50)
    private String entidade;

    @Column(name = "entidade_id", nullable = false, length = 64)
    private String entidadeId;

    /** Retrato dos dados no momento do evento, em JSON. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String dados;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

    protected EventoAuditoria() {
    }

    public EventoAuditoria(String tipo, String entidade, String entidadeId, String dados) {
        this.tipo = tipo;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.dados = dados;
        this.ocorridoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public String getDados() {
        return dados;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }
}
