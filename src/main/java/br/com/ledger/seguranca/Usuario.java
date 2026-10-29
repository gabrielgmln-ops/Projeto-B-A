// Caminho: src/main/java/br/com/ledger/seguranca/Usuario.java
package br.com.ledger.seguranca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Usuário que se autentica na API.
 *
 * Decisões de segurança embutidas no modelo:
 * - Guardamos APENAS o hash da senha (senhaHash), nunca a senha em texto puro.
 *   O hash é BCrypt (ver SegurancaConfig); mesmo com o banco vazado, as senhas
 *   não são recuperáveis diretamente.
 * - O mapeamento espelha EXATAMENTE a migração V5 (ddl-auto: validate recusa
 *   a aplicação se a entidade divergir do schema).
 *
 * Campos de controle de força bruta (tentativas/bloqueio) serão adicionados na
 * Etapa 4, junto com a lógica de rate limiting.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    // UUID gerado pelo Hibernate, mesma convenção da entidade Conta: o id não
    // é sequencial, então não revela quantos usuários existem nem permite adivinhar.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identificador de login. UNIQUE no banco garante que não haja dois iguais.
    @Column(nullable = false, length = 60, unique = true)
    private String username;

    // Hash BCrypt da senha (prefixado com {bcrypt} pelo DelegatingPasswordEncoder).
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    // Permite desativar um usuário sem apagá-lo (preserva histórico e auditoria).
    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    /** Construtor exigido pelo JPA. */
    protected Usuario() {
    }

    public Usuario(String username, String senhaHash, Papel papel) {
        this.username = username;
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.ativo = true;
        this.criadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Papel getPapel() {
        return papel;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
