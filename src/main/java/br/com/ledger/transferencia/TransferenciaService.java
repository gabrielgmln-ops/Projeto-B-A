// Caminho: src/main/java/br/com/ledger/transferencia/TransferenciaService.java
package br.com.ledger.transferencia;

import br.com.ledger.auditoria.AuditoriaService;
import br.com.ledger.comum.RecursoNaoEncontradoException;
import br.com.ledger.comum.RegraNegocioException;
import br.com.ledger.comum.SaldoInsuficienteException;
import br.com.ledger.conta.Conta;
import br.com.ledger.conta.ContaRepository;
import br.com.ledger.conta.TipoConta;
import br.com.ledger.idempotencia.IdempotenciaService;
import br.com.ledger.lancamento.Lancamento;
import br.com.ledger.lancamento.LancamentoRepository;
import br.com.ledger.lancamento.TipoLancamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Coração do ledger: movimenta dinheiro em partidas dobradas.
 *
 * Estratégia de concorrência escolhida: LOCK PESSIMISTA (SELECT ... FOR UPDATE)
 * nas duas contas envolvidas, sempre na mesma ordem. Motivos:
 * - Em dinheiro, conflito é comum (várias operações na mesma conta) e o custo de
 *   errar é altíssimo; esperar na fila é melhor do que falhar e repetir.
 * - Lock otimista (@Version) exigiria um contador de versão na conta e retentativas
 *   em toda colisão. Como o saldo é derivado e não há coluna para versionar,
 *   o otimista nem protegeria a leitura do saldo.
 * - Isolamento SERIALIZABLE também resolveria, mas aborta transações em conflito
 *   e obriga o código a repetir; o lock deixa a fila explícita e previsível.
 */
@Service
public class TransferenciaService {

    private final ContaRepository contaRepository;
    private final TransacaoRepository transacaoRepository;
    private final LancamentoRepository lancamentoRepository;
    private final IdempotenciaService idempotenciaService;
    private final AuditoriaService auditoriaService;

    public TransferenciaService(ContaRepository contaRepository,
                                TransacaoRepository transacaoRepository,
                                LancamentoRepository lancamentoRepository,
                                IdempotenciaService idempotenciaService,
                                AuditoriaService auditoriaService) {
        this.contaRepository = contaRepository;
        this.transacaoRepository = transacaoRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.idempotenciaService = idempotenciaService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * IMPORTANTE: @Transactional faz tudo abaixo (reserva da chave, locks,
     * lançamentos, auditoria) ser uma única transação ACID: ou tudo é gravado,
     * ou nada é. Nunca existe débito sem o crédito correspondente.
     */
    @Transactional
    public ResultadoOperacao transferir(String chaveIdempotencia, TransferenciaRequest request) {
        return executarIdempotente(chaveIdempotencia, TipoTransacao.TRANSFERENCIA,
                request.contaOrigemId(), request.contaDestinoId(), request.valor(), request.descricao());
    }

    /** Depósito = transferência da Tesouraria (conta interna) para o cliente. */
    @Transactional
    public ResultadoOperacao depositar(String chaveIdempotencia, DepositoRequest request) {
        return executarIdempotente(chaveIdempotencia, TipoTransacao.DEPOSITO,
                Conta.TESOURARIA_ID, request.contaId(), request.valor(), request.descricao());
    }

    @Transactional(readOnly = true)
    public TransferenciaResponse buscar(UUID id) {
        return transacaoRepository.findById(id)
                .map(TransferenciaResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transacao nao encontrada: " + id));
    }

    private ResultadoOperacao executarIdempotente(String chave, TipoTransacao tipo, UUID origemId,
                                                  UUID destinoId, BigDecimal valorBruto, String descricao) {
        idempotenciaService.validarChave(chave);
        // Normaliza para 2 casas: 10, 10.0 e 10.00 são o mesmo pedido (mesmo hash).
        BigDecimal valor = valorBruto.setScale(2, RoundingMode.UNNECESSARY);
        String hash = idempotenciaService.calcularHash(tipo, origemId, destinoId, valor, descricao);

        Optional<TransferenciaResponse> anterior = idempotenciaService.reservar(chave, hash);
        if (anterior.isPresent()) {
            return new ResultadoOperacao(anterior.get(), true);
        }

        TransferenciaResponse resposta = movimentar(tipo, origemId, destinoId, valor, descricao);
        idempotenciaService.registrarResposta(chave, resposta);
        return new ResultadoOperacao(resposta, false);
    }

    private TransferenciaResponse movimentar(TipoTransacao tipo, UUID origemId, UUID destinoId,
                                             BigDecimal valor, String descricao) {
        if (origemId.equals(destinoId)) {
            throw new RegraNegocioException("Conta de origem e destino devem ser diferentes");
        }

        // IMPORTANTE (deadlock): travamos as duas contas SEMPRE em ordem crescente de id.
        // Sem isso: A->B trava A e espera B; ao mesmo tempo B->A trava B e espera A.
        // Cada uma espera a outra para sempre. Com ordem fixa, ambas tentam A primeiro.
        UUID primeiroId = origemId.compareTo(destinoId) < 0 ? origemId : destinoId;
        UUID segundoId = primeiroId.equals(origemId) ? destinoId : origemId;
        Conta primeira = bloquear(primeiroId);
        Conta segunda = bloquear(segundoId);
        Conta origem = primeira.getId().equals(origemId) ? primeira : segunda;
        Conta destino = origem == primeira ? segunda : primeira;

        if (tipo == TipoTransacao.TRANSFERENCIA
                && (origem.getTipo() != TipoConta.CLIENTE || destino.getTipo() != TipoConta.CLIENTE)) {
            throw new RegraNegocioException("Transferencias so sao permitidas entre contas de cliente");
        }

        // IMPORTANTE: o saldo é lido DEPOIS do lock. Nenhuma outra transação pode
        // lançar nesta conta até o commit, então o valor lido continua verdadeiro
        // até gravarmos o débito. É isso que impede saldo negativo sob concorrência.
        if (!origem.permiteSaldoNegativo()) {
            BigDecimal saldo = lancamentoRepository.calcularSaldo(origem.getId());
            if (saldo.compareTo(valor) < 0) {
                throw new SaldoInsuficienteException();
            }
        }

        Transacao transacao = transacaoRepository.save(
                new Transacao(tipo, origemId, destinoId, valor, descricao));

        // Partidas dobradas: um débito e um crédito de mesmo valor.
        lancamentoRepository.save(new Lancamento(transacao, origem, TipoLancamento.DEBITO, valor));
        lancamentoRepository.save(new Lancamento(transacao, destino, TipoLancamento.CREDITO, valor));

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contaOrigemId", origemId);
        dados.put("contaDestinoId", destinoId);
        dados.put("valor", valor);
        auditoriaService.registrar(tipo.name() + "_REALIZADA", "Transacao", transacao.getId().toString(), dados);

        return TransferenciaResponse.de(transacao);
    }

    /**
     * IMPORTANTE (contenção): o lock existe para proteger a validação de saldo.
     * A Tesouraria pode ficar negativa, então não há saldo dela a validar e
     * travá-la só criaria uma fila única por onde passariam TODOS os depósitos
     * do banco. Por isso ela é lida sem lock; os lançamentos dela são apenas
     * INSERTs, que o PostgreSQL executa em paralelo sem conflito.
     * As contas de cliente continuam travadas, na mesma ordem crescente de id.
     */
    private Conta bloquear(UUID id) {
        var conta = Conta.TESOURARIA_ID.equals(id)
                ? contaRepository.findById(id)
                : contaRepository.buscarParaAtualizacao(id);
        return conta.orElseThrow(() -> new RecursoNaoEncontradoException("Conta nao encontrada: " + id));
    }
}
