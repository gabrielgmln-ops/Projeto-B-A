// Caminho: src/main/java/br/com/ledger/seguranca/OuvinteEventosLogin.java
package br.com.ledger.seguranca;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Liga o Spring Security ao ControleTentativasLogin ouvindo os eventos de
 * autenticação (publicados pelo AuthenticationEventPublisher configurado na
 * SegurancaConfig):
 *
 * - Qualquer falha de autenticação (senha errada, usuário inexistente, conta
 *   bloqueada) conta como tentativa falha.
 * - Um sucesso zera o contador do usuário.
 *
 * Ouvir eventos (em vez de interceptar o filtro) mantém a contagem desacoplada
 * do mecanismo de login — serviria igual para Basic, form login ou um futuro JWT.
 */
@Component
public class OuvinteEventosLogin {

    private final ControleTentativasLogin controle;

    public OuvinteEventosLogin(ControleTentativasLogin controle) {
        this.controle = controle;
    }

    @EventListener
    public void aoFalhar(AbstractAuthenticationFailureEvent evento) {
        controle.registrarFalha(evento.getAuthentication().getName());
    }

    @EventListener
    public void aoTerSucesso(AuthenticationSuccessEvent evento) {
        controle.registrarSucesso(evento.getAuthentication().getName());
    }
}
