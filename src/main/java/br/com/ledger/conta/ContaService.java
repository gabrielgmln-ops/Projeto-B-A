// Caminho: src/main/java/br/com/ledger/conta/ContaService.java
package br.com.ledger.conta;

import br.com.ledger.auditoria.AuditoriaService;
import br.com.ledger.comum.PaginaResponse;
import br.com.ledger.comum.RecursoNaoEncontradoException;
import br.com.ledger.lancamento.LancamentoRepository;
import br.com.ledger.lancamento.LancamentoResponse;
import br.com.ledger.seguranca.ControleAcesso;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class ContaService {

    private final ContaRepository contaRepository;
    private final LancamentoRepository lancamentoRepository;
    private final AuditoriaService auditoriaService;
    private final ControleAcesso controleAcesso;

    public ContaService(ContaRepository contaRepository,
                        LancamentoRepository lancamentoRepository,
                        AuditoriaService auditoriaService,
                        ControleAcesso controleAcesso) {
        this.contaRepository = contaRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.auditoriaService = auditoriaService;
        this.controleAcesso = controleAcesso;
    }

    @Transactional
    public ContaResponse criar(CriarContaRequest request) {
        Conta conta = new Conta(request.titular().trim(), TipoConta.CLIENTE);
        // A conta nasce pertencendo ao usuário autenticado que a criou. É isso que
        // dá sentido à verificação de dono nas leituras e operações seguintes.
        conta.definirDono(controleAcesso.idDoUsuarioAtual());
        conta = contaRepository.save(conta);
        auditoriaService.registrar("CONTA_CRIADA", "Conta", conta.getId().toString(),
                Map.of("titular", conta.getTitular(), "tipo", conta.getTipo()));
        // Conta nova não tem lançamentos: saldo é zero por definição.
        return ContaResponse.de(conta, BigDecimal.ZERO.setScale(2));
    }

    @Transactional(readOnly = true)
    public ContaResponse buscar(UUID id) {
        Conta conta = buscarEntidade(id);
        // Autorização por dono: um cliente só vê a própria conta (senão, 404).
        controleAcesso.exigirVisibilidade(conta.getUsuarioId());
        return ContaResponse.de(conta, lancamentoRepository.calcularSaldo(id));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<LancamentoResponse> extrato(UUID contaId, Pageable pageable) {
        Conta conta = buscarEntidade(contaId);
        controleAcesso.exigirVisibilidade(conta.getUsuarioId());
        return PaginaResponse.de(lancamentoRepository
                .findByContaIdOrderByIdDesc(contaId, pageable)
                .map(LancamentoResponse::de));
    }

    private Conta buscarEntidade(UUID id) {
        return contaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta nao encontrada: " + id));
    }
}
