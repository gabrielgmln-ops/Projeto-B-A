// Caminho: src/main/java/br/com/ledger/idempotencia/ChaveIdempotenciaRepository.java
package br.com.ledger.idempotencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ChaveIdempotenciaRepository extends JpaRepository<ChaveIdempotencia, String> {

    /**
     * IMPORTANTE (concorrência): "ON CONFLICT DO NOTHING" é atômico no PostgreSQL.
     * Se duas requisições com a mesma chave chegam juntas, a segunda fica
     * BLOQUEADA nesta linha até a primeira terminar. Depois:
     * - se a primeira confirmou (commit), retorna 0: é uma repetição;
     * - se a primeira falhou (rollback), retorna 1: a segunda assume o processamento.
     * Uma verificação "SELECT e depois INSERT" no Java teria uma janela de corrida.
     *
     * @return 1 se a chave foi reservada agora, 0 se já existia.
     */
    @Modifying
    @Query(value = """
            INSERT INTO chave_idempotencia (chave, hash_requisicao, criada_em)
            VALUES (:chave, :hash, now())
            ON CONFLICT (chave) DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(@Param("chave") String chave, @Param("hash") String hash);

    // flushAutomatically: garante que a Transacao (gravada via JPA) já foi enviada
    // ao banco antes deste UPDATE, que a referencia por chave estrangeira.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE chave_idempotencia
            SET transacao_id = :transacaoId, status_http = :status, resposta_json = :resposta
            WHERE chave = :chave
            """, nativeQuery = true)
    int registrarResposta(@Param("chave") String chave,
                          @Param("transacaoId") UUID transacaoId,
                          @Param("status") int status,
                          @Param("resposta") String resposta);
}
