// Caminho: src/main/java/br/com/ledger/seguranca/UsuarioAtual.java
package br.com.ledger.seguranca;

import br.com.ledger.comum.AcessoNegadoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Descobre QUEM é o usuário autenticado na requisição atual.
 *
 * Lê o SecurityContext (preenchido pelo Spring Security a cada requisição) e
 * resolve o Usuario correspondente no banco. Centralizar isto aqui evita que
 * cada service mexa no SecurityContextHolder — e dá um único ponto para testar.
 */
@Component
public class UsuarioAtual {

    public static final String PAPEL_OPERADOR = "ROLE_OPERADOR";

    private final UsuarioRepository usuarioRepository;

    public UsuarioAtual(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** O Usuario autenticado, ou AcessoNegadoException se não houver um válido. */
    public Usuario obrigatorio() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AcessoNegadoException("Autenticacao necessaria");
        }
        return usuarioRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AcessoNegadoException("Usuario autenticado sem cadastro"));
    }

    /** True se o usuário atual tem o papel OPERADOR (perfil interno do banco). */
    public boolean ehOperador() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> PAPEL_OPERADOR.equals(a.getAuthority()));
    }
}
