// Caminho: src/test/java/br/com/ledger/idempotencia/IdempotenciaIntegracaoTest.java
package br.com.ledger.idempotencia;

import br.com.ledger.IntegracaoBase;
import br.com.ledger.transferencia.DepositoRequest;
import br.com.ledger.transferencia.ResultadoOperacao;
import br.com.ledger.transferencia.TransferenciaController;
import br.com.ledger.transferencia.TransferenciaService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotenciaIntegracaoTest extends IntegracaoBase {

    @Autowired
    private TransferenciaService transferenciaService;

    @Test
    void requisicaoRepetidaNaoDuplicaLancamentos() throws Exception {
        UUID origem = criarConta("Origem");
        UUID destino = criarConta("Destino");
        depositar(origem, "100.00");
        String chave = UUID.randomUUID().toString();

        String primeira = transferir(origem, destino, "40.00", chave)
                .andExpect(status().isCreated())
                .andExpect(header().string(TransferenciaController.CABECALHO_REPETIDA, "false"))
                .andReturn().getResponse().getContentAsString();

        // Mesmo corpo, mesma chave: simula o app reenviando após timeout de rede.
        String segunda = transferir(origem, destino, "40.00", chave)
                .andExpect(status().isCreated())
                .andExpect(header().string(TransferenciaController.CABECALHO_REPETIDA, "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(segunda, "$.id")).isEqualTo(JsonPath.read(primeira, "$.id"));
        // O dinheiro saiu UMA vez só.
        assertThat(saldo(origem)).isEqualByComparingTo("60.00");
        assertThat(saldo(destino)).isEqualByComparingTo("40.00");
    }

    @Test
    void mesmaChaveComCorpoDiferenteEhRecusada() throws Exception {
        UUID origem = criarConta("Origem");
        UUID destino = criarConta("Destino");
        depositar(origem, "100.00");
        String chave = UUID.randomUUID().toString();

        transferir(origem, destino, "10.00", chave).andExpect(status().isCreated());
        transferir(origem, destino, "99.00", chave)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Conflito de idempotencia"));

        assertThat(saldo(origem)).isEqualByComparingTo("90.00");
    }

    @Test
    void chaveVaziaOuLongaDemaisEhRecusada() throws Exception {
        UUID conta = criarConta("Conta");
        depositar(conta, "1.00", " ").andExpect(status().isBadRequest());
        depositar(conta, "1.00", "x".repeat(IdempotenciaService.TAMANHO_MAXIMO_CHAVE + 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void falhaNaoFicaGravadaEPodeSerRepetidaComSucesso() throws Exception {
        UUID origem = criarConta("Origem");
        UUID destino = criarConta("Destino");
        String chave = UUID.randomUUID().toString();

        // Sem saldo: falha e o rollback remove também a reserva da chave.
        transferir(origem, destino, "10.00", chave).andExpect(status().isUnprocessableEntity());

        depositar(origem, "10.00");
        transferir(origem, destino, "10.00", chave)
                .andExpect(status().isCreated())
                .andExpect(header().string(TransferenciaController.CABECALHO_REPETIDA, "false"));
    }

    /**
     * IMPORTANTE: o caso mais perigoso é a repetição SIMULTÂNEA (dois cliques
     * no mesmo milissegundo). 20 threads, mesma chave: só um depósito pode existir.
     */
    @Test
    void repeticoesSimultaneasGeramUmaUnicaTransacao() throws Exception {
        UUID conta = criarConta("Concorrente");
        String chave = UUID.randomUUID().toString();
        DepositoRequest request = new DepositoRequest(conta, new BigDecimal("25.00"), "simultaneo");

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<ResultadoOperacao>> futuros = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return transferenciaService.depositar(chave, request);
            }));
        }
        largada.countDown();

        List<ResultadoOperacao> resultados = new ArrayList<>();
        for (Future<ResultadoOperacao> futuro : futuros) {
            resultados.add(futuro.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        Set<UUID> ids = resultados.stream().map(r -> r.resposta().id()).collect(Collectors.toSet());
        assertThat(ids).hasSize(1);
        assertThat(resultados.stream().filter(r -> !r.repetida()).count()).isEqualTo(1);
        assertThat(saldo(conta)).isEqualByComparingTo("25.00");
    }
}
