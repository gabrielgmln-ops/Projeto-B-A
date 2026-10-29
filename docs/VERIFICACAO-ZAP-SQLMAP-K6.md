<!-- Caminho: docs/VERIFICACAO-ZAP-SQLMAP-K6.md -->
# Etapa 6 — Roteiro de verificação ofensiva (ZAP, sqlmap, k6)

> Demonstração ofensiva feita **apenas** com ferramentas prontas, contra a **sua
> própria instância local** da Ledger API. Nada aqui é um exploit escrito à mão:
> são varreduras e testes de carga padrão de mercado, que você roda, observa e
> anexa a saída ao relatório final. Só ambiente local, só dados sintéticos.

## 0. Preparar a instância local

```bash
# No diretório do projeto:
cd C:/SegundoCerebro/ledger-api
docker compose up -d        # sobe o PostgreSQL local (compose.yaml)
./mvnw spring-boot:run      # sobe a API em http://localhost:8081
```

Usuários sintéticos criados no startup (ver `.env`):
- `operador` / senha de `SEED_OPERADOR_SENHA` (papel OPERADOR)
- `cliente`  / senha de `SEED_CLIENTE_SENHA`  (papel CLIENTE)

Credenciais em HTTP Basic. Exemplo de chamada autenticada:

```bash
curl -u operador:$SEED_OPERADOR_SENHA http://localhost:8081/api/v1/auditoria
```

---

## 1. SQL Injection — sqlmap

Alvo: o único parâmetro de **texto livre** que chega a uma consulta é o filtro
`entidadeId` do extrato de auditoria. Os demais são UUID tipados.

```bash
sqlmap -u "http://localhost:8081/api/v1/auditoria?entidadeId=1" \
  --method GET \
  --headers="Authorization: Basic $(printf 'operador:%s' "$SEED_OPERADOR_SENHA" | base64)" \
  --level=5 --risk=3 --batch --dbms=postgresql
```

**Saída esperada:** `parameter 'entidadeId' does not appear to be injectable`.
A consulta é parametrizada (Spring Data), então o payload é tratado como dado.
Anexe o relatório do sqlmap ao item 1 do relatório.

---

## 2 e 5. Varredura geral — OWASP ZAP (IDOR, headers, vazamento)

### 2a. Baseline scan (passivo, rápido)

```bash
docker run --rm -t --network host ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py -t http://localhost:8081/v3/api-docs -r zap-baseline.html
```

Avalia cabeçalhos de segurança, cookies e exposições óbvias. Anexe `zap-baseline.html`.

### 2b. Teste de IDOR / controle de acesso (manual, com dois usuários)

Crie uma conta como `cliente` e tente lê-la como outro usuário:

```bash
# cria conta como cliente
CID=$(curl -s -u cliente:$SEED_CLIENTE_SENHA -X POST \
  http://localhost:8081/api/v1/contas \
  -H "Content-Type: application/json" -d '{"titular":"Cliente Um"}' | jq -r .id)

# tenta ler a MESMA conta como operador de outro contexto / sem posse:
curl -i -u cliente:$SEED_CLIENTE_SENHA http://localhost:8081/api/v1/contas/$CID   # 200 (dono)
# criar um 2o cliente e repetir a leitura da conta alheia deve dar 404.
```

**Esperado após a correção:** leitura de conta alheia → **404**; operação em
conta alheia → **403**. Antes da correção (Etapa 2) seria 200/201 — o IDOR.

---

## 3. Condição de corrida (gasto duplo) — k6

Dispara saques simultâneos da mesma conta; a defesa (lock pessimista) garante
que o saldo nunca fica negativo.

`k6/gasto-duplo.js`:
```javascript
import http from 'k6/http';
import { check } from 'k6';
const BASE = 'http://localhost:8081';
const AUTH = `Basic ${encoding.b64encode(`${__ENV.USER}:${__ENV.PASS}`)}`;
export const options = { vus: 50, iterations: 500 };
export default function () {
  const res = http.post(`${BASE}/api/v1/transferencias`,
    JSON.stringify({ contaOrigemId: __ENV.ORIGEM, contaDestinoId: __ENV.DESTINO, valor: 1.00, descricao: 'k6' }),
    { headers: { 'Content-Type': 'application/json', 'Authorization': AUTH, 'Idempotency-Key': `${__VU}-${__ITER}` } });
  check(res, { 'sem 5xx': (r) => r.status < 500 });
}
```

```bash
k6 run -e USER=operador -e PASS=$SEED_OPERADOR_SENHA \
       -e ORIGEM=<uuid-origem-com-100> -e DESTINO=<uuid-destino> k6/gasto-duplo.js
```

**Esperado:** nenhum erro 5xx; ao final, `saldo(origem) >= 0` e a soma de todos
os lançamentos continua **zero** (consulte `/api/v1/contas/<id>`).

---

## 4. Força bruta no login — k6

Martela o login com senha errada e comprova o bloqueio progressivo.

`k6/forca-bruta.js`:
```javascript
import http from 'k6/http';
import { check } from 'k6';
const BASE = 'http://localhost:8081';
export const options = { vus: 1, iterations: 10 };
export default function () {
  const res = http.get(`${BASE}/api/v1/contas/00000000-0000-0000-0000-000000000002`,
    { headers: { 'Authorization': `Basic ${encoding.b64encode('cliente:senha-errada')}` } });
  check(res, { 'bloqueado apos limite': (r) => r.status === 401 });
}
```

```bash
k6 run k6/forca-bruta.js
```

**Esperado:** as primeiras tentativas retornam 401 por credencial inválida; após
`ledger.seguranca.login.max-tentativas` falhas, o usuário entra em bloqueio
progressivo e **até a senha correta** passa a retornar 401 durante a janela.

---

## Checklist de anexos ao relatório final

- [ ] `sqlmap` — saída "not injectable" (item 1)
- [ ] `zap-baseline.html` (itens 2 e 5)
- [ ] Prova manual de IDOR 404/403 (item 2)
- [ ] `k6` gasto-duplo — resumo sem 5xx e soma zero (item 3)
- [ ] `k6` força-bruta — 401 após o limite (item 4)
