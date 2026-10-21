// Caminho: src/main/java/br/com/ledger/auditoria/AuditoriaController.java
package br.com.ledger.auditoria;

import br.com.ledger.comum.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    @Operation(summary = "Lista eventos de auditoria (mais recentes primeiro)")
    public PaginaResponse<EventoAuditoriaResponse> listar(
            @RequestParam(required = false) String entidadeId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return auditoriaService.listar(entidadeId, pageable);
    }
}
