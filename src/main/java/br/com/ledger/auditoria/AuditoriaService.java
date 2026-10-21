// Caminho: src/main/java/br/com/ledger/auditoria/AuditoriaService.java
package br.com.ledger.auditoria;

import br.com.ledger.comum.PaginaResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuditoriaService {

    private final EventoAuditoriaRepository repository;
    private final ObjectMapper objectMapper;

    public AuditoriaService(EventoAuditoriaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * IMPORTANTE: Propagation.MANDATORY exige que já exista uma transação aberta.
     * O evento é gravado NA MESMA transação da operação de negócio: se a
     * transferência for desfeita, o evento também é; se for confirmada, o evento
     * existe com certeza. Nunca há operação sem auditoria nem auditoria de operação
     * que não aconteceu.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String tipo, String entidade, String entidadeId, Map<String, Object> dados) {
        repository.save(new EventoAuditoria(tipo, entidade, entidadeId, paraJson(dados)));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<EventoAuditoriaResponse> listar(String entidadeId, Pageable pageable) {
        var pagina = (entidadeId == null || entidadeId.isBlank())
                ? repository.findAllByOrderByIdDesc(pageable)
                : repository.findByEntidadeIdOrderByIdDesc(entidadeId, pageable);
        return PaginaResponse.de(pagina.map(EventoAuditoriaResponse::de));
    }

    private String paraJson(Map<String, Object> dados) {
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento de auditoria", e);
        }
    }
}
