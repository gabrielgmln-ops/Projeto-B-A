// Caminho: src/main/java/br/com/ledger/comum/GlobalExceptionHandler.java
package br.com.ledger.comum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento de erros centralizado. Todas as respostas de erro seguem o
 * padrão RFC 9457 (Problem Details): {type, title, status, detail, instance}.
 * Herdar de ResponseEntityExceptionHandler já cobre os erros padrão do
 * Spring MVC (JSON malformado, cabeçalho ausente, método errado...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail tratarNaoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Recurso nao encontrado", e.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail tratarRegraNegocio(RegraNegocioException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negocio violada", e.getMessage());
    }

    @ExceptionHandler(ConflitoIdempotenciaException.class)
    public ProblemDetail tratarConflitoIdempotencia(ConflitoIdempotenciaException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Conflito de idempotencia", e.getMessage());
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ProblemDetail tratarRequisicaoInvalida(RequisicaoInvalidaException e) {
        return problema(HttpStatus.BAD_REQUEST, "Requisicao invalida", e.getMessage());
    }

    /** Ex.: "/contas/abc" quando o esperado é um UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tratarTipoInvalido(MethodArgumentTypeMismatchException e) {
        return problema(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "Valor invalido para o parametro '" + e.getName() + "'");
    }

    /**
     * Rede de segurança para qualquer erro inesperado.
     * IMPORTANTE (segurança): o detalhe técnico vai só para o log; o cliente
     * recebe mensagem genérica, sem stack trace nem SQL.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail tratarInesperado(Exception e) {
        log.error("Erro inesperado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Erro inesperado no servidor");
    }

    /** Erros da Bean Validation: devolve um mapa campo -> mensagem. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> erros = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(erro -> erros.putIfAbsent(erro.getField(), erro.getDefaultMessage()));

        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Dados invalidos",
                "Um ou mais campos estao invalidos");
        problema.setProperty("erros", erros);
        return ResponseEntity.badRequest().body(problema);
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
