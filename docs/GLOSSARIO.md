# Glossário

Conceitos usados no Ledger API, com o lugar do código onde cada um aparece.

### ACID
Propriedades de uma transação de banco de dados:
- **Atomicidade:** tudo ou nada.
- **Consistência:** as regras do banco continuam valendo depois da transação.
- **Isolamento:** transações simultâneas não enxergam o trabalho pela metade uma da outra.
- **Durabilidade:** depois do commit, o dado sobrevive a uma queda.

No projeto, o `@Transactional` do `TransferenciaService` garante que débito, crédito, auditoria e chave de idempotência sejam gravados juntos ou não sejam gravados.

### Append-only
Tabela em que só se acrescentam linhas, sem alterar nem apagar. Lançamentos, transações e auditoria são append-only, e quem garante isso são triggers no próprio PostgreSQL (`V2__regras_append_only.sql`). Para corrigir um erro, faz-se um estorno: uma nova transação no sentido contrário.

### Auditoria
Registro de quem fez o quê e quando. Cada operação grava um `EventoAuditoria` na **mesma** transação (`Propagation.MANDATORY`). Assim, não existe operação sem registro nem registro de operação que não aconteceu.

### BigDecimal
Tipo decimal exato do Java. O `double` é binário, então `0.1 + 0.2` dá `0.30000000000000004`. Em dinheiro, esse erro vira diferença contábil. No banco, o equivalente é `NUMERIC(19,2)`.

### Condição de corrida (race condition)
Erro que aparece quando duas operações simultâneas leem o mesmo dado antes de qualquer uma gravar. Exemplo: dois saques de R$ 100 numa conta com R$ 100; os dois leem "saldo 100" e os dois passam. O `ConcorrenciaIntegracaoTest` reproduz esse cenário.

### Deadlock
Duas transações esperando uma pela outra para sempre: A trava a conta 1 e quer a 2, enquanto B trava a 2 e quer a 1. O projeto evita isso travando sempre na ordem crescente de id.

### DTO (Data Transfer Object)
Objeto que só carrega os dados da API (por exemplo, `TransferenciaRequest` e `ContaResponse`). Fica separado da entidade, para que o formato da API não fique preso ao formato do banco.

### Flyway
Ferramenta de migração. Cada mudança no schema é um script numerado (`V1__...sql`, `V2__...sql`), aplicado uma única vez e registrado. O Hibernate só valida o schema (`ddl-auto: validate`), nunca o altera.

### Idempotência
Repetir a mesma operação produz o mesmo efeito de executá-la uma vez. O cliente envia o cabeçalho `Idempotency-Key`; se a mesma chave chegar de novo, a API devolve a resposta original, sem mover dinheiro outra vez. As chaves expiram em 24h.

### Isolamento (níveis)
Quanto uma transação enxerga das outras que estão em andamento:
- **READ COMMITTED** (padrão do PostgreSQL): só enxerga o que já sofreu commit.
- **REPEATABLE READ**: enxerga o mesmo retrato do banco do começo ao fim da transação.
- **SERIALIZABLE**: equivale a rodar as transações uma depois da outra. Para isso, aborta transações em conflito, e o código precisa repeti-las.

O projeto usa READ COMMITTED combinado com lock explícito.

### Lançamento
Uma linha do livro-razão: "R$ X a débito ou a crédito na conta Y, por causa da transação Z". O saldo é a soma dos lançamentos.

### Ledger (livro-razão)
O registro de todos os lançamentos do banco. É a fonte da verdade: o saldo é calculado a partir dele.

### Lock otimista
Não trava nada. Na hora de gravar, confere se o dado mudou desde a leitura (por uma coluna `@Version`). Se mudou, falha, e a operação precisa ser repetida. Funciona bem quando conflitos são raros.

### Lock pessimista
Trava a linha antes de ler (`SELECT ... FOR UPDATE`); quem chega depois espera na fila. É o escolhido aqui, porque em dinheiro o conflito é comum e errar custa caro. A Tesouraria não é travada, porque não tem saldo a validar.

### Partidas dobradas
Todo movimento tem um débito e um crédito de mesmo valor. Por isso a soma de todos os lançamentos do banco é sempre zero, e qualquer centavo fora do lugar pode ser detectado.

### Problem Details (RFC 9457)
Formato padrão para erros em JSON: `type`, `title`, `status`, `detail`. É centralizado no `GlobalExceptionHandler`.

### Saldo derivado
O saldo não é uma coluna editável: é sempre `SUM(créditos) - SUM(débitos)`. Assim, não existe um número que possa divergir do histórico.

### Snapshot de saldo
Próximo passo, ainda não implementado. Guarda o saldo de uma conta até o lançamento N, e o saldo atual passa a ser o snapshot mais o que veio depois. Evita somar milhões de linhas.

### Tesouraria
Conta interna do banco, que serve de contrapartida dos depósitos. É ela que mantém a soma geral em zero quando dinheiro "entra" no sistema.

### Testcontainers
Biblioteca que sobe um PostgreSQL real em Docker durante os testes. É melhor que um banco em memória (H2), que simula mal os locks e o isolamento.
