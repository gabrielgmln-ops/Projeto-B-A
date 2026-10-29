// Caminho: src/main/java/br/com/ledger/seguranca/SegurancaConfig.java
package br.com.ledger.seguranca;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração central do Spring Security.
 *
 * Postura adotada nesta Etapa 0:
 * - "Secure by default": qualquer rota não listada explicitamente exige autenticação.
 * - Autenticação HTTP Basic, SEM sessão (stateless): cada requisição carrega as
 *   credenciais; não há cookie de sessão para roubar nem CSRF via navegador.
 * - CSRF desabilitado: só faz sentido quando há sessão baseada em cookie, que não é
 *   o nosso caso. Mantê-lo ligado aqui só quebraria chamadas de API sem ganho.
 *
 * @EnableMethodSecurity liga o uso de @PreAuthorize nos métodos — ainda não usamos,
 * mas deixa o terreno pronto para a autorização por dono do recurso (Etapa 2).
 */
@Configuration
@EnableMethodSecurity
public class SegurancaConfig {

    // Rotas públicas: verificação de saúde (orquestradores) e documentação.
    private static final String[] ROTAS_PUBLICAS = {
            "/actuator/health",
            "/actuator/health/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           TratamentoErroSeguranca tratamentoErro) throws Exception {
        http
                // API stateless: desliga CSRF (não há sessão/cookie) de forma consciente.
                .csrf(csrf -> csrf.disable())
                // Não cria sessão HTTP: toda requisição se autentica do zero.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Pré-flight CORS do navegador não deve exigir credenciais.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        // Tudo o mais exige um usuário autenticado.
                        .anyRequest().authenticated())
                // HTTP Basic: o cliente envia "Authorization: Basic base64(user:senha)".
                .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                // Respostas de erro padronizadas (RFC 9457), sem vazar detalhes (Etapa 5).
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(tratamentoErro) // 401
                        .accessDeniedHandler(tratamentoErro));    // 403

        return http.build();
    }

    /**
     * Publica eventos de autenticação (sucesso/falha). É o que permite ao
     * OuvinteEventosLogin contar falhas e alimentar o bloqueio progressivo
     * contra força bruta (Etapa 4). Sem este bean, o ProviderManager não publica.
     */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publicador) {
        return new DefaultAuthenticationEventPublisher(publicador);
    }

    /**
     * Encoder "delegante": grava o hash com um prefixo indicando o algoritmo
     * ({bcrypt}...). Isso permite migrar de algoritmo no futuro sem quebrar os
     * hashes antigos. O padrão é BCrypt, adequado para senhas (lento e com sal).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
