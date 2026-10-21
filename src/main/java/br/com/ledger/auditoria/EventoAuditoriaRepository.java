// Caminho: src/main/java/br/com/ledger/auditoria/EventoAuditoriaRepository.java
package br.com.ledger.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {

    Page<EventoAuditoria> findAllByOrderByIdDesc(Pageable pageable);

    Page<EventoAuditoria> findByEntidadeIdOrderByIdDesc(String entidadeId, Pageable pageable);
}
