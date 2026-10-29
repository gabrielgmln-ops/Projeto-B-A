// Caminho: src/main/java/br/com/ledger/seguranca/ControleAcesso.java
package br.com.ledger.seguranca;

import br.com.ledger.comum.AcessoNegadoException;
import br.com.ledger.comum.RecursoNaoEncontradoException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Regras de autorização por DONO do recurso (combate a IDOR / Broken Access Control).
 *
 * Princípio: um CLIENTE só enxerga e movimenta as próprias contas. O OPERADOR
 * (perfil interno) tem acesso amplo. A verificação é feita no servidor, SEMPRE —
 * nunca confiamos no cliente para enviar "só o próprio id".
 *
 * Dois níveis, com códigos HTTP escolhidos de propósito:
 * - exigirVisibilidade (LEITURA): se negar, lança 404 (RecursoNaoEncontrado).
 *   Devolver 403 aqui revelaria que a conta existe e pertence a outro — o que
 *   permitiria enumerar contas alheias. 404 torna "conta de outro" indistinguível
 *   de "conta inexistente".
 * - exigirPosse (OPERAÇÃO/escrita): se negar, lança 403 (AcessoNegado). Aqui o
 *   usuário referencia uma conta no corpo da requisição; 403 é semanticamente
 *   correto e não agrega informação nova.
 */
@Component
public class ControleAcesso {

    private final UsuarioAtual usuarioAtual;

    public ControleAcesso(UsuarioAtual usuarioAtual) {
        this.usuarioAtual = usuarioAtual;
    }

    /** Leitura de uma conta/recurso cujo dono é donoDaConta. Nega com 404. */
    public void exigirVisibilidade(UUID donoDaConta) {
        if (usuarioAtual.ehOperador()) {
            return;
        }
        UUID atual = usuarioAtual.obrigatorio().getId();
        if (donoDaConta == null || !atual.equals(donoDaConta)) {
            throw new RecursoNaoEncontradoException("Conta nao encontrada");
        }
    }

    /** Operação (escrita) sobre uma conta cujo dono é donoDaConta. Nega com 403. */
    public void exigirPosse(UUID donoDaConta) {
        if (usuarioAtual.ehOperador()) {
            return;
        }
        UUID atual = usuarioAtual.obrigatorio().getId();
        if (donoDaConta == null || !atual.equals(donoDaConta)) {
            throw new AcessoNegadoException("Operacao nao permitida nesta conta");
        }
    }

    /** Acesso restrito a OPERADOR (ex.: trilha de auditoria). Nega com 403. */
    public void exigirOperador() {
        if (!usuarioAtual.ehOperador()) {
            throw new AcessoNegadoException("Acesso restrito a operadores");
        }
    }

    /** Dono da conta a ser usado como "proprietário" de um recurso recém-criado. */
    public UUID idDoUsuarioAtual() {
        return usuarioAtual.obrigatorio().getId();
    }

    /** Leitura de uma transação: visível se o usuário é dono da origem OU do destino. */
    public void exigirVisibilidadeTransacao(UUID donoOrigem, UUID donoDestino) {
        if (usuarioAtual.ehOperador()) {
            return;
        }
        UUID atual = usuarioAtual.obrigatorio().getId();
        if (!atual.equals(donoOrigem) && !atual.equals(donoDestino)) {
            throw new RecursoNaoEncontradoException("Transacao nao encontrada");
        }
    }
}
