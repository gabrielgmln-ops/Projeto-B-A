// Caminho: src/test/java/br/com/ledger/transferencia/ConcorrenciaIntegracaoTest.java
package br.com.ledger.transferencia;

import br.com.ledger.IntegracaoBase;
import br.com.ledger.comum.SaldoInsuficienteException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critério de sucesso nº 1: 1.000 transferências simultâneas e a soma dos
 * saldos permanece constante.
 *
 * Chamamos o service direto (sem HTTP) para que o gargalo seja só o banco e os
 * locks, que é exatamente o que queremos estressar.
 */
class ConcorrenciaIntegracaoTest extends IntegracaoBase {

    private static final int TRANSFERENCIAS = 1_000;
    private static final int THREADS = 50;

    @Autowired
    private TransferenciaService transferenciaService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void milTransferenciasSimultaneasPreservamASomaDosSaldos() throws Exception {
        int quantidadeContas = 10;
        List<UUID> contas = new ArrayList<>();
        for (int i = 0; i < quantidadeContas; i++) {
            UUID conta = criarConta("Conta concorrente " + i);
            depositar(conta, "1000.00");
            contas.add(conta);
        }
        BigDecimal somaAntes = somarSaldos(contas);

        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger semSaldo = new AtomicInteger();
        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < TRANSFERENCIAS; i++) {
            tarefas.add(() -> {
                ThreadLocalRandom aleatorio = ThreadLocalRandom.current();
                UUID origem = contas.get(aleatorio.nextInt(quantidadeContas));
                UUID destino;
                do {
                    destino = contas.get(aleatorio.nextInt(quantidadeContas));
                } while (destino.equals(origem));
                // Pares em ambos os sentidos (A->B e B->A) ao mesmo tempo: sem ordem
                // fixa de lock, isto geraria deadlock.
                BigDecimal valor = BigDecimal.valueOf(aleatorio.nextInt(1, 30001), 2);
                try {
                    transferenciaService.transferir(UUID.randomUUID().toString(),
                            new TransferenciaRequest(origem, destino, valor, "estresse"));
                    sucessos.incrementAndGet();
                } catch (SaldoInsuficienteException e) {
                    semSaldo.incrementAndGet();
                }
                return null;
            });
        }

        executarSimultaneamente(tarefas);

        assertThat(sucessos.get() + semSaldo.get()).isEqualTo(TRANSFERENCIAS);
        assertThat(sucessos.get()).isPositive();
        // A prova: nenhum centavo criado ou destruído.
        assertThat(somarSaldos(contas)).isEqualByComparingTo(somaAntes);
        for (UUID conta : contas) {
            assertThat(saldo(conta)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
        assertThat(lancamentoRepository.somarTodosOsLancamentos()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Ataque clássico de "gasto duplo": 500 saques simultâneos de R$ 1,00 numa
     * conta com R$ 100,00. Sem lock, várias threads leriam "saldo 100" ao mesmo
     * tempo e todas passariam. Com lock, exatamente 100 passam.
     */
    @Test
    void saquesSimultaneosNuncaDeixamSaldoNegativo() throws Exception {
        UUID origem = criarConta("Alvo");
        UUID destino = criarConta("Recebedor");
        depositar(origem, "100.00");

        AtomicInteger sucessos = new AtomicInteger();
        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            tarefas.add(() -> {
                try {
                    transferenciaService.transferir(UUID.randomUUID().toString(),
                            new TransferenciaRequest(origem, destino, new BigDecimal("1.00"), "saque"));
                    sucessos.incrementAndGet();
                } catch (SaldoInsuficienteException e) {
                    // esperado depois que o saldo acaba
                }
                return null;
            });
        }

        executarSimultaneamente(tarefas);

        assertThat(sucessos.get()).isEqualTo(100);
        assertThat(saldo(origem)).isEqualByComparingTo("0");
        assertThat(saldo(destino)).isEqualByComparingTo("100.00");
    }

    /**
     * Prova de que a Tesouraria não é mais um gargalo: outra transação segura um
     * lock na linha da Tesouraria e, mesmo assim, um depósito termina na hora.
     *
     * Por que FOR NO KEY UPDATE e não FOR UPDATE? Ao inserir um lançamento, a FK
     * para conta faz o PostgreSQL pegar sozinho um lock leve (FOR KEY SHARE) na
     * conta referenciada. FOR UPDATE bloquearia até esse lock leve; FOR NO KEY
     * UPDATE não. Mas FOR NO KEY UPDATE conflita com o FOR UPDATE que a versão
     * antiga do código pegava na Tesouraria: com a versão antiga, o depósito
     * ficaria esperando e o get(5s) estouraria com TimeoutException.
     */
    @Test
    void depositoNaoEsperaPeloLockDaTesouraria() throws Exception {
        UUID conta = criarConta("Deposito sem fila");
        CountDownLatch lockAdquirido = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService pool = poolSeguro(2);
        try {
            Future<?> bloqueador = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject("SELECT id FROM conta WHERE id = ? FOR NO KEY UPDATE",
                        UUID.class, br.com.ledger.conta.Conta.TESOURARIA_ID);
                lockAdquirido.countDown();
                try {
                    liberar.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(lockAdquirido.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResultadoOperacao> deposito = pool.submit(() -> transferenciaService.depositar(
                    UUID.randomUUID().toString(), new DepositoRequest(conta, new BigDecimal("7.00"), "sem fila")));

            assertThat(deposito.get(5, TimeUnit.SECONDS).repetida()).isFalse();
            assertThat(saldo(conta)).isEqualByComparingTo("7.00");

            liberar.countDown();
            bloqueador.get(10, TimeUnit.SECONDS);
        } finally {
            liberar.countDown();
            pool.shutdownNow();
        }
    }

    /** Muitos depósitos simultâneos (todos usam a Tesouraria) mantêm a contabilidade fechada. */
    @Test
    void depositosSimultaneosMantemASomaZero() throws Exception {
        UUID conta = criarConta("Muitos depositos");
        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            tarefas.add(() -> {
                transferenciaService.depositar(UUID.randomUUID().toString(),
                        new DepositoRequest(conta, new BigDecimal("1.00"), "lote"));
                return null;
            });
        }

        executarSimultaneamente(tarefas);

        assertThat(saldo(conta)).isEqualByComparingTo("300.00");
        assertThat(lancamentoRepository.somarTodosOsLancamentos()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Dispara todas as tarefas juntas e propaga qualquer erro inesperado (ex.: deadlock). */
    private void executarSimultaneamente(List<Callable<Void>> tarefas) throws Exception {
        ExecutorService pool = poolSeguro(THREADS);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Void>> futuros = new ArrayList<>();
        for (Callable<Void> tarefa : tarefas) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return tarefa.call();
            }));
        }
        largada.countDown();
        try {
            for (Future<Void> futuro : futuros) {
                futuro.get(120, TimeUnit.SECONDS);
            }
        } catch (ExecutionException e) {
            throw new AssertionError("Transferencia falhou com erro inesperado", e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    private BigDecimal somarSaldos(List<UUID> contas) {
        return contas.stream().map(this::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
