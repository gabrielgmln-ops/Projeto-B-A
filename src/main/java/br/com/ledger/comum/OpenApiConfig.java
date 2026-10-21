// Caminho: src/main/java/br/com/ledger/comum/OpenApiConfig.java
package br.com.ledger.comum;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados exibidos no topo do Swagger UI (/swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ledger API")
                .version("v1")
                .description("Nucleo bancario com contabilidade de partidas dobradas, "
                        + "saldo derivado, idempotencia e auditoria append-only. Dados sinteticos."));
    }
}
