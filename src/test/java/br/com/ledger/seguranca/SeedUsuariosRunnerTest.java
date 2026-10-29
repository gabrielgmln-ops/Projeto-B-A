// Caminho: src/test/java/br/com/ledger/seguranca/SeedUsuariosRunnerTest.java
package br.com.ledger.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Testes unitários da conta de serviço do Gateway de Segurança no seed.
 * Sem Spring nem banco: o repositório e o encoder são simulados com Mockito.
 */
class SeedUsuariosRunnerTest {

    private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SeedUsuariosRunner runner = new SeedUsuariosRunner(repositorio, encoder);

    @Test
    void criaContaDoGatewayComPapelMinimoESenhaComHash() {
        ReflectionTestUtils.setField(runner, "gatewayUsername", "svc-gateway");
        ReflectionTestUtils.setField(runner, "gatewaySenha", "senha-sintetica");
        when(encoder.encode("senha-sintetica")).thenReturn("hash-bcrypt");

        runner.run();

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).save(salvo.capture());
        assertThat(salvo.getValue().getUsername()).isEqualTo("svc-gateway");
        // Privilégio mínimo: nunca OPERADOR, que passaria em todas as contas.
        assertThat(salvo.getValue().getPapel()).isEqualTo(Papel.CLIENTE);
    }

    @Test
    void naoDuplicaContaDoGatewayExistente() {
        ReflectionTestUtils.setField(runner, "gatewayUsername", "svc-gateway");
        ReflectionTestUtils.setField(runner, "gatewaySenha", "senha-sintetica");
        when(repositorio.existsByUsername("svc-gateway")).thenReturn(true);

        runner.run();

        verify(repositorio, never()).save(any());
    }

    @Test
    void semVariaveisNaoCriaNada() {
        runner.run();

        verify(repositorio, never()).save(any());
        verify(encoder, never()).encode(anyString());
    }
}
