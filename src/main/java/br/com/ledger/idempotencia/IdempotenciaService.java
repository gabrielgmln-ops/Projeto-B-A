// Caminho: src/main/java/br/com/ledger/idempotencia/IdempotenciaService.java
package br.com.ledger.idempotencia;

import br.com.ledger.comum.ConflitoIdempotenciaException;
import br.com.ledger.comum.RequisicaoInvalidaException;
import br.com.ledger.transferencia.TransferenciaResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Garante que reenviar a mesma requisição (mesma Idempotency-Key) não movimenta
 * dinheiro duas vezes. Cenário real: o app do cliente envia o Pix, a rede cai
 * antes da resposta chegar, e o app tenta de novo automaticamente.
 */
@Service
public class IdempotenciaService {

    public static final int TAMANHO_MAXIMO_CHAVE = 100;

    private final ChaveIdempotenciaRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotenciaService(ChaveIdempotenciaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void validarChave(String chave) {
        if (chave == null || chave.isBlank() || chave.length() > TAMANHO_MAXIMO_CHAVE) {
            throw new RequisicaoInvalidaException(
                    "Cabecalho Idempotency-Key deve ter entre 1 e " + TAMANHO_MAXIMO_CHAVE + " caracteres");
        }
    }

    /**
     * Impressão digital do corpo da requisição. Serve para detectar a mesma chave
     * reutilizada com outro pedido (ex.: outro valor).
     */
    public String calcularHash(Object... partes) {
        StringBuilder texto = new StringBuilder();
        for (Object parte : partes) {
            texto.append(parte == null ? "" : parte.toString()).append('|');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(texto.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    /**
     * Tenta reservar a chave. Roda dentro da transação da transferência
     * (MANDATORY), então a reserva e a movimentação são confirmadas ou
     * desfeitas juntas.
     *
     * @return vazio se esta requisição deve ser processada agora; ou a resposta
     *         original, se a chave já foi processada com o mesmo corpo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TransferenciaResponse> reservar(String chave, String hash) {
        if (repository.inserirSeAusente(chave, hash) == 1) {
            return Optional.empty();
        }
        ChaveIdempotencia existente = repository.findById(chave)
                .orElseThrow(() -> new IllegalStateException("Chave sumiu apos conflito: " + chave));

        if (!existente.getHashRequisicao().equals(hash)) {
            throw new ConflitoIdempotenciaException();
        }
        return Optional.of(desserializar(existente.getRespostaJson()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarResposta(String chave, TransferenciaResponse resposta) {
        repository.registrarResposta(chave, resposta.id(), HttpStatus.CREATED.value(), serializar(resposta));
    }

    private String serializar(TransferenciaResponse resposta) {
        try {
            return objectMapper.writeValueAsString(resposta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar resposta", e);
        }
    }

    private TransferenciaResponse desserializar(String json) {
        try {
            return objectMapper.readValue(json, TransferenciaResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler resposta armazenada", e);
        }
    }
}
