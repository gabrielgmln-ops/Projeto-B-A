// Caminho: src/main/java/br/com/ledger/seguranca/UsuarioRepository.java
package br.com.ledger.seguranca;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a usuários. O Spring Data gera a implementação a partir do nome do método.
 *
 * Nota de segurança (prevê a Etapa 1): mesmo "findByUsername" gerado pelo Spring Data
 * usa consulta PARAMETRIZADA por baixo dos panos — o valor nunca é concatenado no SQL,
 * então o login já nasce imune a injeção de SQL.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);
}
