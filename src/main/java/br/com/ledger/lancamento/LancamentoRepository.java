// Caminho: src/main/java/br/com/ledger/lancamento/LancamentoRepository.java
package br.com.ledger.lancamento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    /**
     * Saldo derivado: soma dos créditos menos soma dos débitos.
     * COALESCE devolve 0 para contas sem lançamentos (SUM de nada é NULL no SQL).
     */
    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN tipo = 'CREDITO' THEN valor ELSE -valor END), 0)
            FROM lancamento
            WHERE conta_id = :contaId
            """, nativeQuery = true)
    BigDecimal calcularSaldo(@Param("contaId") UUID contaId);

    /**
     * Soma de TODOS os lançamentos do banco. Em partidas dobradas deve ser
     * sempre zero; é a "prova real" usada nos testes.
     */
    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN tipo = 'CREDITO' THEN valor ELSE -valor END), 0)
            FROM lancamento
            """, nativeQuery = true)
    BigDecimal somarTodosOsLancamentos();

    /** Extrato: o Spring Data gera a consulta paginada a partir do nome do método. */
    Page<Lancamento> findByContaIdOrderByIdDesc(UUID contaId, Pageable pageable);

    long countByTransacaoId(UUID transacaoId);
}
