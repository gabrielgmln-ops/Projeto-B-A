// Caminho: src/main/java/br/com/ledger/transferencia/TransacaoRepository.java
package br.com.ledger.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransacaoRepository extends JpaRepository<Transacao, UUID> {
}
