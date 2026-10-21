// Caminho: src/main/java/br/com/ledger/auditoria/EventoAuditoriaResponse.java
package br.com.ledger.auditoria;

import java.time.Instant;

public record EventoAuditoriaResponse(
        Long id,
        String tipo,
        String entidade,
        String entidadeId,
        String dados,
        Instant ocorridoEm) {

    public static EventoAuditoriaResponse de(EventoAuditoria e) {
        return new EventoAuditoriaResponse(e.getId(), e.getTipo(), e.getEntidade(),
                e.getEntidadeId(), e.getDados(), e.getOcorridoEm());
    }
}
