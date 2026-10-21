// Caminho: src/main/java/br/com/ledger/idempotencia/LimpezaIdempotenciaJob.java
package br.com.ledger.idempotencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Expira chaves de idempotência para a tabela não crescer para sempre.
 *
 * A validade padrão de 24h segue o mercado (Stripe usa 24h): retentativas de
 * cliente acontecem em segundos ou minutos, nunca dias depois. Depois do prazo,
 * a mesma chave volta a ser aceita como um pedido novo.
 */
@Component
public class LimpezaIdempotenciaJob {

    private static final Logger log = LoggerFactory.getLogger(LimpezaIdempotenciaJob.class);

    private final ChaveIdempotenciaRepository repository;
    private final Duration validade;

    public LimpezaIdempotenciaJob(ChaveIdempotenciaRepository repository,
                                  @Value("${ledger.idempotencia.validade}") Duration validade) {
        this.repository = repository;
        this.validade = validade;
    }

    @Scheduled(cron = "${ledger.idempotencia.limpeza-cron}")
    @Transactional
    public int executar() {
        int removidas = repository.removerCriadasAntesDe(Instant.now().minus(validade));
        if (removidas > 0) {
            log.info("Limpeza de idempotencia: {} chave(s) com mais de {} removida(s)", removidas, validade);
        }
        return removidas;
    }
}
