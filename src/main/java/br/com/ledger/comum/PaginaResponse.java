// Caminho: src/main/java/br/com/ledger/comum/PaginaResponse.java
package br.com.ledger.comum;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Formato estável de página na API. Serializar o Page do Spring direto expõe
 * detalhes internos que mudam entre versões (o próprio Spring alerta sobre isso).
 */
public record PaginaResponse<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
