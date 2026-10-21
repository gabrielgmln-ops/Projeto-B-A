// Caminho: src/main/java/br/com/ledger/conta/ContaRepository.java
package br.com.ledger.conta;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContaRepository extends JpaRepository<Conta, UUID> {

    /**
     * IMPORTANTE (concorrência): gera "SELECT ... FOR UPDATE".
     * A linha da conta fica travada até o fim da transação; qualquer outra
     * transferência que mexa nesta conta espera na fila. Assim, ler o saldo,
     * validar e gravar os lançamentos vira uma operação indivisível.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conta c where c.id = :id")
    Optional<Conta> buscarParaAtualizacao(@Param("id") UUID id);
}
