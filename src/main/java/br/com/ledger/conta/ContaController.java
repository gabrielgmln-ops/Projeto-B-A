// Caminho: src/main/java/br/com/ledger/conta/ContaController.java
package br.com.ledger.conta;

import br.com.ledger.comum.PaginaResponse;
import br.com.ledger.lancamento.LancamentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/** Controller fino: traduz HTTP <-> DTO e delega toda regra ao service. */
@RestController
@RequestMapping("/api/v1/contas")
@Tag(name = "Contas")
public class ContaController {

    private final ContaService contaService;

    public ContaController(ContaService contaService) {
        this.contaService = contaService;
    }

    @PostMapping
    @Operation(summary = "Cria uma conta de cliente")
    public ResponseEntity<ContaResponse> criar(@Valid @RequestBody CriarContaRequest request) {
        ContaResponse conta = contaService.criar(request);
        // 201 Created + cabeçalho Location apontando para o novo recurso (convenção REST).
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(conta.id()).toUri();
        return ResponseEntity.created(location).body(conta);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma conta e seu saldo (derivado dos lancamentos)")
    public ContaResponse buscar(@PathVariable UUID id) {
        return contaService.buscar(id);
    }

    @GetMapping("/{id}/extrato")
    @Operation(summary = "Extrato paginado da conta (mais recentes primeiro)")
    public PaginaResponse<LancamentoResponse> extrato(
            @PathVariable UUID id,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return contaService.extrato(id, pageable);
    }
}
