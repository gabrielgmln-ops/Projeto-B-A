<!-- Caminho: docs/RELATORIO-APPSEC.md -->
# Relatório de AppSec — Ledger API

**Projeto:** Operação Cofre — endurecimento de segurança da Ledger API
**Escopo:** aplicação própria, ambiente local, dados sintéticos
**Stack:** Java 21, Spring Boot 3.5, Spring Security, PostgreSQL, JUnit 5, Testcontainers
**Data:** 2026-10-26

## Sumário executivo

Auditoria defensiva de cinco classes de vulnerabilidade comuns em sistemas
financeiros. Resultado honesto após a análise do código:

| # | Classe | Situação encontrada | Severidade | Status final |
|---|--------|--------------------|-----------|--------------|
| 1 | Injeção de SQL | Já imune (consultas parametrizadas) | — (seria 9.8) | Verificado ✔ |
| 2 | IDOR / controle de acesso quebrado | **Vulnerável** (sem dono de conta) | **Alta — 8.1** | Corrigido ✔ |
| 3 | Condição de corrida financeira | Já protegido (lock pessimista) | — (seria 8.1) | Verificado ✔ |
| 4 | Força bruta em autenticação | **Lacuna** (sem rate limiting) | **Alta — 7.5** | Corrigido ✔ |
| 5 | Vazamento em mensagens de erro | Parcial (401/403 sem padrão) | Baixa — 3.7 | Endurecido ✔ |

Base de autenticação adicionada na Etapa 0: Spring Security com HTTP Basic
stateless, senhas em BCrypt, segredos por variável de ambiente.

**Validação:** `./mvnw -B verify` → 44 testes, 0 falhas, cobertura ≥ 80%.

---

## 1. Injeção de SQL

- **Severidade:** não explorável (se existisse: Crítica, CVSS 9.8 — `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H`)
- **Descrição:** execução de SQL arbitrário via entrada concatenada em consulta.
- **Análise:** toda a persistência usa Spring Data (consultas derivadas) ou
  `@Query` com parâmetros nomeados (`:contaId`, `:id`); nenhum ponto concatena
  entrada no SQL. Identificadores de rota são `UUID` tipados — texto malicioso
  é barrado na conversão (400) antes de tocar o banco. O único parâmetro de
  texto livre (`entidadeId` da auditoria) também é parametrizado.
- **Impacto para um banco:** vazamento/alteração de toda a base, incluindo saldos.
- **Defesa aplicada:** confirmada por design; nenhuma mudança de código necessária.
- **Teste que valida:** `InjecaoSqlTest` — payloads `'; DROP TABLE usuario; --`
  e `' OR '1'='1` resultam em página vazia e a tabela continua de pé; injeção
  em path UUID retorna 400.
- **Verificação externa:** `sqlmap` (ver Etapa 6) → "not injectable".

## 2. IDOR / Controle de acesso quebrado  ⚠ corrigido

- **Severidade:** **Alta — CVSS 8.1** (`AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N`)
- **Descrição:** antes da correção, qualquer usuário autenticado podia **ler o
  saldo e o extrato de qualquer conta** (trocando o UUID), **movimentar contas
  alheias** e **consultar a trilha de auditoria** inteira. Não havia noção de
  dono do recurso — clássico *Broken Object Level Authorization*.
- **Impacto para um banco:** quebra total de confidencialidade e integridade
  entre clientes; um correntista enxergaria e esvaziaria a conta de outro.
- **Defesa aplicada:**
  - Cada `Conta` passou a ter um **dono** (`usuario_id`, migração `V6`).
  - `ControleAcesso` central: CLIENTE só vê/movimenta as próprias contas;
    OPERADOR (perfil interno) tem acesso amplo.
  - Leitura de recurso alheio → **404** (não 403), para não revelar a
    existência da conta (anti-enumeração). Operação sobre conta alheia → **403**.
  - Trilha de auditoria restrita a OPERADOR.
  - Verificação **no servidor, sempre** — nunca se confia no id enviado.
- **Teste que valida:** `ControleDeAcessoIdorTest` — Ana e Bruno (CLIENTE);
  Ana lê a própria conta (200), a de Bruno (404), o extrato de Bruno (404),
  depósito na conta de Bruno (403) e auditoria (403).

## 3. Condição de corrida financeira (gasto duplo)

- **Severidade:** não explorável (se existisse: Alta, CVSS 8.1 — `.../C:N/I:H/A:N`)
- **Descrição:** saques simultâneos lendo o mesmo saldo e todos passando
  (saldo negativo / dinheiro criado).
- **Análise:** `TransferenciaService` trava as contas de cliente com
  **lock pessimista** (`SELECT ... FOR UPDATE`, `ContaRepository.buscarParaAtualizacao`),
  sempre em ordem crescente de id (evita deadlock), dentro de uma transação ACID,
  e lê o saldo **depois** do lock. Partidas dobradas + idempotência fecham o modelo.
- **Impacto para um banco:** criação/destruição de dinheiro, saldo negativo.
- **Defesa aplicada:** confirmada; nenhuma mudança necessária.
- **Teste que valida:** `ConcorrenciaIntegracaoTest.saquesSimultaneosNuncaDeixamSaldoNegativo`
  (500 saques simultâneos de R$1 sobre R$100 → exatamente 100 passam) e
  `milTransferenciasSimultaneasPreservamASomaDosSaldos` (soma dos saldos constante).
- **Verificação externa:** `k6` gasto-duplo (ver Etapa 6).

## 4. Força bruta em autenticação  ⚠ corrigido

- **Severidade:** **Alta — CVSS 7.5** (`AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N`)
- **Descrição:** sem limite de tentativas, um atacante podia testar senhas
  ilimitadamente até tomar uma conta.
- **Impacto para um banco:** sequestro de conta de cliente.
- **Defesa aplicada:** **bloqueio progressivo por usuário** (`ControleTentativasLogin`):
  após `max-tentativas` falhas consecutivas, a conta é bloqueada por uma janela
  que **dobra** a cada nova falha, até um teto. `OuvinteEventosLogin` escuta os
  eventos de autenticação do Spring Security; `UsuarioDetailsService` marca a
  conta como *locked* durante a janela — mesmo a senha correta é recusada.
  Limitação registrada: estado em memória (uma instância); em produção
  multi-instância, usar store compartilhado (Redis).
- **Teste que valida:** `ForcaBrutaLoginTest` — senha correta funciona antes do
  bloqueio; após 3 falhas (limite reduzido no teste), a senha correta passa a
  retornar 401 durante a janela.
- **Verificação externa:** `k6` força-bruta (ver Etapa 6).

## 5. Vazamento de informação em mensagens de erro

- **Severidade:** Baixa — CVSS 3.7 (`AV:N/AC:H/PR:N/UI:N/S:U/C:L/I:N/A:N`)
- **Descrição:** respostas de erro que revelam stack trace, SQL, nomes de classe
  ou distinguem "usuário não existe" de "senha errada" (enumeração).
- **Análise e defesa:**
  - O `GlobalExceptionHandler` já devolvia erros padronizados (RFC 9457) e um
    500 genérico sem detalhes técnicos. Mantido.
  - **Lacuna corrigida:** o 401/403 do Spring Security vinha com corpo vazio/HTML.
    Agora `TratamentoErroSeguranca` devolve **sempre** JSON genérico
    (`application/problem+json`), sem detalhes.
  - Login não distingue usuário inexistente de senha errada (mesma exceção).
  - IDOR de leitura responde 404 genérico (ver item 2), reforçando o anti-vazamento.
- **Teste que valida:** `RespostaErroSegurancaTest` — 401 e 403 em
  `application/problem+json`, genéricos, e o corpo não contém `Exception`,
  `java.`, `org.springframework` nem SQL.

---

## Como reproduzir a validação

```bash
cd C:/SegundoCerebro/ledger-api
./mvnw -B verify
```

Esperado: `BUILD SUCCESS`, `Tests run: 44, Failures: 0, Errors: 0`, cobertura ≥ 80%.

## Arquivos de segurança (resumo)

- `src/main/java/br/com/ledger/seguranca/` — `SegurancaConfig`, `Usuario`,
  `UsuarioDetailsService`, `ControleAcesso`, `UsuarioAtual`,
  `ControleTentativasLogin`, `OuvinteEventosLogin`, `TratamentoErroSeguranca`,
  `SeedUsuariosRunner`.
- Migrações `V5__usuarios.sql`, `V6__conta_dono.sql`.
- Testes de regressão em `src/test/java/br/com/ledger/seguranca/` e
  `transferencia/ConcorrenciaIntegracaoTest`.
