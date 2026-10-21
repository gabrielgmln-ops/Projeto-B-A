// Caminho: src/main/java/br/com/ledger/LedgerApiApplication.java
package br.com.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicação.
 *
 * @SpringBootApplication liga três coisas: configuração automática (auto-configuration),
 * varredura de componentes a partir DESTE pacote (por isso a classe fica na raiz,
 * br.com.ledger) e a possibilidade de declarar configurações aqui.
 *
 * @EnableScheduling liga as tarefas agendadas (@Scheduled), como a limpeza
 * das chaves de idempotência expiradas.
 */
@EnableScheduling
@SpringBootApplication
public class LedgerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerApiApplication.class, args);
    }
}
