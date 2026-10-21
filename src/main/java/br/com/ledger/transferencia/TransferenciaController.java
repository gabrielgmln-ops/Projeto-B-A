// Caminho: src/main/java/br/com/ledger/transferencia/TransferenciaController.java
package br.com.ledger.transferencia;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transferencias")
public class TransferenciaController {

    /** Cabeçalho de resposta que avisa o cliente de que a resposta é uma repetição. */
    public static final String CABECALHO_REPETIDA = "Idempotent-Replayed";

    private final TransferenciaService transferenciaService;

    public TransferenciaController(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    @PostMapping("/transferencias")
    @Operation(summary = "Transfere entre contas de cliente (partidas dobradas, idempotente)")
    public ResponseEntity<TransferenciaResponse> transferir(
            @Parameter(description = "Chave unica gerada pelo cliente, ex.: um UUID")
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody TransferenciaRequest request) {
        return responder(transferenciaService.transferir(chave, request));
    }

    @PostMapping("/depositos")
    @Operation(summary = "Deposita em uma conta de cliente (contrapartida: Tesouraria)")
    public ResponseEntity<TransferenciaResponse> depositar(
            @Parameter(description = "Chave unica gerada pelo cliente, ex.: um UUID")
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody DepositoRequest request) {
        return responder(transferenciaService.depositar(chave, request));
    }

    @GetMapping("/transferencias/{id}")
    @Operation(summary = "Consulta uma transacao")
    public TransferenciaResponse buscar(@PathVariable UUID id) {
        return transferenciaService.buscar(id);
    }

    // A repetição devolve o MESMO status e corpo da primeira chamada: para o cliente,
    // é como se a primeira resposta tivesse finalmente chegado.
    private ResponseEntity<TransferenciaResponse> responder(ResultadoOperacao resultado) {
        TransferenciaResponse resposta = resultado.resposta();
        return ResponseEntity.created(URI.create("/api/v1/transferencias/" + resposta.id()))
                .header(CABECALHO_REPETIDA, String.valueOf(resultado.repetida()))
                .body(resposta);
    }
}
