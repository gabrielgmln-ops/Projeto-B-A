// Caminho: src/test/java/br/com/ledger/seguranca/SegurancaTestSupport.java
package br.com.ledger.seguranca;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * "Arreio" de autenticação para os testes de integração.
 *
 * Problema: ao ligar o Spring Security, toda requisição não autenticada passa a
 * receber 401. A suíte existente (criar conta, depositar, transferir...) testa
 * REGRAS DE NEGÓCIO, não login — não faria sentido reescrever todos os testes
 * para mandar credenciais.
 *
 * Solução: um MockMvcBuilderCustomizer injeta, por padrão, um usuário autenticado
 * em TODA requisição do MockMvc. Usamos o post-processor user(...) do
 * spring-security-test, que coloca a autenticação direto no contexto de segurança
 * (sem passar pelo banco nem pela senha). Assim a suíte de negócio continua verde
 * sem nenhuma alteração nos testes.
 *
 * Os testes específicos de SEGURANÇA (Etapas 1 a 5) vão sobrescrever isso quando
 * precisarem: mandando .with(anonymous()) para checar o 401, ou credenciais reais
 * via httpBasic(...) para exercitar o fluxo de autenticação de verdade.
 */
@TestConfiguration
public class SegurancaTestSupport {

    /** Username do usuário sintético usado por padrão nos testes de integração. */
    public static final String USUARIO_TESTE = "op-teste";

    @Bean
    MockMvcBuilderCustomizer autenticacaoPadraoDosTestes() {
        // defaultRequest funde esses atributos em toda requisição; método, path e
        // corpo reais de cada teste continuam valendo — só a autenticação é herdada.
        return builder -> builder.defaultRequest(
                get("/").with(user(USUARIO_TESTE).roles("OPERADOR", "CLIENTE")));
    }
}
