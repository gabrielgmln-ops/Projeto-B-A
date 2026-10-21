// Caminho: src/main/java/br/com/ledger/idempotencia/ChaveIdempotencia.java
package br.com.ledger.idempotencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Chave enviada pelo cliente no cabeçalho Idempotency-Key, com o hash do corpo
 * e a resposta original. Inserção e atualização são feitas por SQL nativo no
 * repository (ver ChaveIdempotenciaRepository); esta entidade é usada só para leitura.
 */
@Entity
@Table(name = "chave_idempotencia")
public class ChaveIdempotencia {

    @Id
    @Column(length = 100)
    private String chave;

    @Column(name = "hash_requisicao", nullable = false, length = 64)
    private String hashRequisicao;

    @Column(name = "transacao_id")
    private UUID transacaoId;

    @Column(name = "status_http")
    private Integer statusHttp;

    @Column(name = "resposta_json", columnDefinition = "TEXT")
    private String respostaJson;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected ChaveIdempotencia() {
    }

    public String getChave() {
        return chave;
    }

    public String getHashRequisicao() {
        return hashRequisicao;
    }

    public UUID getTransacaoId() {
        return transacaoId;
    }

    public Integer getStatusHttp() {
        return statusHttp;
    }

    public String getRespostaJson() {
        return respostaJson;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
