// Caminho: src/main/java/br/com/ledger/seguranca/ControleTentativasLogin.java
package br.com.ledger.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defesa contra força bruta em autenticação: conta falhas por usuário e aplica
 * BLOQUEIO PROGRESSIVO (cada bloqueio dura o dobro do anterior, até um teto).
 *
 * Por que progressivo: um usuário legítimo que errou a senha uma ou duas vezes
 * quase não é penalizado; já um atacante automatizado enfrenta uma espera que
 * cresce exponencialmente (1 min, 2, 4, 8... até o teto), tornando a varredura
 * de senhas inviável — sem travar a conta para sempre.
 *
 * Estado em memória (ConcurrentHashMap): suficiente e simples para uma única
 * instância local. Em produção multi-instância, trocaríamos por um store
 * compartilhado (ex.: Redis) — registrado como limitação no relatório.
 */
@Component
public class ControleTentativasLogin {

    /** Após quantas falhas CONSECUTIVAS o bloqueio começa a valer. */
    @Value("${ledger.seguranca.login.max-tentativas:5}")
    private int maxTentativas;

    /** Duração do primeiro bloqueio, em segundos (dobra a cada falha excedente). */
    @Value("${ledger.seguranca.login.bloqueio-base-segundos:60}")
    private long bloqueioBaseSegundos;

    /** Teto da duração do bloqueio, em segundos. */
    @Value("${ledger.seguranca.login.bloqueio-maximo-segundos:900}")
    private long bloqueioMaximoSegundos;

    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();

    /** Registra uma falha de login para o usuário e, se preciso, (re)calcula o bloqueio. */
    public void registrarFalha(String username) {
        if (username == null) {
            return;
        }
        registros.compute(username.toLowerCase(), (chave, atual) -> {
            Registro r = (atual == null) ? new Registro() : atual;
            r.falhas++;
            if (r.falhas >= maxTentativas) {
                long excedente = r.falhas - maxTentativas; // 0, 1, 2...
                long fator = 1L << Math.min(excedente, 20); // dobra, com guarda de overflow
                long segundos = Math.min(bloqueioBaseSegundos * fator, bloqueioMaximoSegundos);
                r.bloqueadoAte = Instant.now().plus(Duration.ofSeconds(segundos));
            }
            return r;
        });
    }

    /** Login bem-sucedido zera o histórico do usuário. */
    public void registrarSucesso(String username) {
        if (username != null) {
            registros.remove(username.toLowerCase());
        }
    }

    /** True se o usuário está dentro de uma janela de bloqueio vigente. */
    public boolean estaBloqueado(String username) {
        if (username == null) {
            return false;
        }
        Registro r = registros.get(username.toLowerCase());
        return r != null && r.bloqueadoAte != null && Instant.now().isBefore(r.bloqueadoAte);
    }

    private static final class Registro {
        private int falhas;
        private Instant bloqueadoAte;
    }
}
