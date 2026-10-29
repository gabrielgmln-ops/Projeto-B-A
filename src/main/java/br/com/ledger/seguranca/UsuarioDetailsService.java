// Caminho: src/main/java/br/com/ledger/seguranca/UsuarioDetailsService.java
package br.com.ledger.seguranca;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Ponte entre o nosso modelo (Usuario) e o Spring Security.
 *
 * O Spring chama loadUserByUsername durante a autenticação; devolvemos um
 * UserDetails com o hash da senha e as autoridades. Quem compara a senha
 * enviada com o hash é o AuthenticationManager, usando o PasswordEncoder.
 *
 * Detalhe de segurança importante (reforçado na Etapa 5): lançamos sempre a
 * MESMA exceção (UsernameNotFoundException) para usuário inexistente. Não
 * revelamos "usuário não existe" vs "senha errada" — isso evita que um atacante
 * descubra quais usernames são válidos (enumeração de usuários).
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final ControleTentativasLogin controleTentativas;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository,
                                 ControleTentativasLogin controleTentativas) {
        this.usuarioRepository = usuarioRepository;
        this.controleTentativas = controleTentativas;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais invalidas"));

        // Defesa contra força bruta: se o usuário está em janela de bloqueio, marcamos
        // a conta como "locked". O Spring então recusa o login ANTES de conferir a
        // senha — mesmo a senha correta é rejeitada enquanto durar o bloqueio.
        boolean bloqueado = controleTentativas.estaBloqueado(usuario.getUsername());

        // A autoridade recebe o prefixo "ROLE_" exigido pela convenção do Spring Security
        // (hasRole("CLIENTE") procura a autoridade "ROLE_CLIENTE").
        return User.withUsername(usuario.getUsername())
                .password(usuario.getSenhaHash())
                .authorities("ROLE_" + usuario.getPapel().name())
                .accountLocked(bloqueado)
                .disabled(!usuario.isAtivo())
                .build();
    }
}
