// Caminho: src/main/java/br/com/ledger/seguranca/TratamentoErroSeguranca.java
package br.com.ledger.seguranca;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Respostas de erro do FILTRO de segurança (antes de chegar aos controllers),
 * padronizadas no formato RFC 9457 (Problem Details), igual ao resto da API.
 *
 * Por que isto existe (Etapa 5 - vazamento de informação): por padrão o Spring
 * Security responde 401 com corpo vazio e cabeçalho "WWW-Authenticate", e 403
 * com uma página de erro. Aqui devolvemos SEMPRE um JSON genérico, sem detalhes
 * técnicos, sem distinguir "usuário não existe" de "senha errada", sem stack trace.
 *
 * - 401 (não autenticado): AuthenticationEntryPoint.
 * - 403 (autenticado, mas sem permissão no nível do filtro): AccessDeniedHandler.
 */
@Component
public class TratamentoErroSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public TratamentoErroSeguranca(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // Mensagem fixa e genérica: não revela o motivo exato da falha de autenticação.
        escrever(response, HttpStatus.UNAUTHORIZED, "Nao autenticado", "Autenticacao necessaria");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, "Acesso negado", "Acesso negado");
    }

    private void escrever(HttpServletResponse response, HttpStatus status, String titulo, String detalhe)
            throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problema);
    }
}
