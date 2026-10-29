// Caminho: src/main/java/br/com/ledger/seguranca/Papel.java
package br.com.ledger.seguranca;

/**
 * Papel (nível de acesso) de um usuário autenticado.
 *
 * Mantemos poucos papéis e bem definidos — o princípio do menor privilégio
 * diz que cada usuário deve ter apenas o acesso estritamente necessário.
 *
 * - CLIENTE: enxerga e movimenta apenas as próprias contas (regra aplicada na Etapa 2).
 * - OPERADOR: perfil interno do banco, com acesso ampliado (ex.: consultar auditoria).
 *
 * O nome da autoridade do Spring Security é derivado daqui com o prefixo "ROLE_"
 * (ver UsuarioDetailsService): CLIENTE -> ROLE_CLIENTE, OPERADOR -> ROLE_OPERADOR.
 */
public enum Papel {
    CLIENTE,
    OPERADOR
}
