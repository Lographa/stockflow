# StockFlow — Plano de Implementação

> Mantido pelo Tech Lead. Revisão 2 — 2026-10-30: decisões D-1…D-10 aprovadas e incorporadas.
> Regra de ouro: **tarefa só é `[x]` depois de implementação revisada + testes executados com sucesso.** Teste não executado ≠ aprovado.

**Legenda:** `[ ]` Pendente · `[/]` Em andamento · `[x]` Concluído · `[!]` Bloqueado

**Fontes de regras:** `AGENTS.md` (prevalece) e as decisões da seção 3. `BACKEND_ROADMAP.md` é o roadmap de estudo do autor (itens futuros na seção 9).

---

## 1. Diagnóstico (resumo — detalhes da revisão 1 preservados nos IDs P-xx)

**Verificado:** código lido por completo; `mvnw compile` OK; `mvnw test` falha sem MySQL; app/HTTP não executados (sem banco). Frontend: `frontend/` **vazio**.

| ID | Sev | Problema | Tratado em |
|---|---|---|---|
| P-01 | A | `ProductMapper.toDtoAll` troca `name`↔`sku` e `quantity`↔`min_stock` | FIX-01 |
| P-02 | A | `PUT` altera `quantity` sem movimentação | MOV-03 |
| P-03 | A | Criar com `quantity=0` → movimentação 0 viola CHECK → HTTP 500; negativos → 500 | FIX-05, FIX-02 |
| P-04 | M | `@Positive` rejeita preço 0; nome/sku/descrição sem `@NotBlank`/`@Size` | FIX-02 |
| P-05 | M | Handler de erro restrito ao pacote `products` | FIX-03 |
| P-06 | M | Sem CORS | SEC-01 |
| P-07 | M | `@Data` em entidades bidirecionais | FIX-04 |
| P-08 | M | `GET /{id}`/POST/PUT devolvem só `id/sku/name`; timestamps nulos após `save` | FIX-05 |
| P-09 | M | `@CreationTimestamp` + `insertable=false` em `Movements.createdAt` (a verificar com MySQL real) | FIX-04, QA-03 |
| P-10 | M | `deleteProduct` apaga histórico; sem endpoint | PRD-01 (soft delete) |
| P-11 | M | Sem testes de negócio; testes atuais exigem MySQL manual | QA-01…QA-03 |
| P-12 | B | `MovimentsDtoAll` expõe entidade `Product`, sem uso; nomes inconsistentes | MOV-01 |
| P-13 | B | `jakarta.transaction.Transactional` em vez do Spring | FIX-05/MOV-01 |
| P-14 | B | Sem paginação | FUT-05 |
| P-15 | B | `show-sql=true` fixo, README vazio, roadmap desatualizado, PDFBox sem uso | SEC-01, QA-05, FUT-08 |
| P-16 | B | API toda pública | SEC-01 (perfis) |

---

## 2. Estado atual das funcionalidades

- **Funcionando (leitura de código):** conexão por variáveis de ambiente; cadastro com SKU/nome únicos (409); busca por id (404); busca com filtros (`ids`, período, `q`); lista vazia → `[]`; criação de produto + `ENTRY` inicial na mesma transação.
- **Parciais:** listagem (dados trocados), detalhe (3 campos), `PUT` (estoque sem movimentação), exclusão (sem endpoint, destrutiva), validação/erros, segurança (liberação total).
- **Ausentes:** entrada/saída, histórico, soft delete, CORS, frontend, testes de negócio e de integração.

---

## 3. Decisões aprovadas (registro)

| ID | Decisão |
|---|---|
| D-1 | `PUT` aceita `quantity`; diferença gera movimentação de ajuste (aumento → `ENTRY`, redução → `EXIT`, descrição `ATUALIZACAO`), **na mesma transação** da atualização. Igual ao atual → sem movimentação |
| D-2 | **Soft delete**: coluna `deleted_at` em `products`. Excluído não aparece em consultas normais nem aceita movimentações; **histórico continua acessível**. Nenhum registro é removido |
| D-3 | `POST` de criação → **201**; `DELETE` → **204** |
| D-4 | `quantity = 0` na criação é válido e **não** gera movimentação. Movimentação só em entrada/saída/ajuste efetivo |
| D-5 | Testes de integração com **Testcontainers + MySQL real** (concorrência, transação, consistência). Sem depender só de H2/mocks |
| D-6 | Nome único mantido, **inclusive contra produtos excluídos logicamente** |
| D-7 | Tailwind CSS + Vitest + Testing Library. Sem biblioteca de componentes visuais; componentes próprios |
| D-8 | Indicador de estoque baixo: `quantity < min_stock`, visível na interface |
| D-9 | API aberta apenas em perfis `dev`/`test`; **perfil padrão (produção) é fechado** (401 em tudo, até existir autenticação) |
| D-10 | Docker Desktop + Testcontainers. Verificar disponibilidade antes; **testes não executados não contam como aprovados** |

### 3.1 Decisões técnicas derivadas (Tech Lead — impacto das decisões)

| ID | Decisão e justificativa |
|---|---|
| T-01 | Manter pacotes por funcionalidade e rotas existentes (`/product`, `/product/create`) |
| T-02 | JSON em `snake_case` nos campos existentes; novos campos seguem o padrão (`low_stock`, `product_id`, `stock_after`, `product_deleted`, `field_errors`) |
| T-03 | **Um único `StockMovementService`** aplica qualquer mudança de saldo (entrada, saída, criação, ajuste de `PUT`). Evita duplicar regra |
| T-04 | Concorrência: **lock pessimista** (`@Lock(PESSIMISTIC_WRITE)`, `SELECT … FOR UPDATE`) via `ProductRepository.findActiveByIdForUpdate` em movimentação, ajuste de `PUT` e exclusão. MySQL é a única fonte da verdade; Redis não participa. A exclusão também trava a linha para que nenhuma movimentação entre depois do `deleted_at` |
| T-05 | **Soft delete sem `@SQLRestriction`/`@Where` na entidade.** Motivo: `Movements.product` (ManyToOne) precisa carregar produtos excluídos para o histórico continuar acessível. O filtro é explícito: métodos de repository (`…AndDeletedAtIsNull`) e predicado na `ProductSpecifications`. Checagens de unicidade (`sku`, `name`) **não filtram** `deleted_at` (D-6) |
| T-06 | **Alterações de schema (justificadas)** — ver DB-01: (a) `products.deleted_at TIMESTAMP NULL` (D-2); (b) `UNIQUE (name)` em `products` para tornar a regra D-6 segura contra corrida (hoje só o service a garante). Sem dados removidos; script de migração separado e **não idempotente** (executar uma vez). SKU reutilizado após exclusão **não** é permitido (UNIQUE mantido) |
| T-07 | Segurança por perfil (SEC-01): `dev`/`test` = acesso aberto + CORS; **sem perfil** = fechado (`authenticated()` + `HttpStatusEntryPoint(401)`), sem CORS, `show-sql=false`. Perfil ativado por `SPRING_PROFILES_ACTIVE=dev` (`.vscode/launch.json` e `.env.example`, ajustados pelo Tech Lead) |
| T-08 | Fonte única do schema para testes: o Testcontainers executa **`database/init.sql`** (mesmo DDL de produção, com CHECK/FK/UNIQUE) e a aplicação sobe com `ddl-auto=validate`, provando que entidades e schema coincidem |
| T-09 | `low_stock` é calculado **no backend** (mapper) com `quantity < min_stock` e entregue na API; o frontend só exibe (regra não duplicada). Filtro `lowStock=true` na busca |
| T-10 | Dependências novas: backend — Testcontainers (escopo test: `testcontainers-mysql`/`junit-jupiter` ou `spring-boot-testcontainers`, versão gerenciada pelo Boot; atenção à compatibilidade com Docker 29/API 1.52). Frontend — as do `create-next-app` + Tailwind + Vitest + Testing Library (+ `jsdom`). Nada além disso sem justificativa |
| T-11 | Erros: `@RestControllerAdvice` global + `ErrorResponseDTO` com `field_errors` opcional (aditivo) |

---

## 4. Contratos de API (congelados para implementação)

Convenções: JSON; datas ISO-8601 sem fuso (`LocalDateTime`); `price` número até 8 inteiros/2 decimais.

### 4.1 Erros (todas as rotas)
```json
{ "error": "Validação falhou", "message": "texto", "field_errors": { "price": "O preço não pode ser negativo" } }
```
`field_errors` só em 400 de validação.

| HTTP | Quando |
|---|---|
| 400 | Bean Validation, JSON malformado, enum/tipo inválido |
| 401 | Perfil fechado (produção) — qualquer rota sem autenticação |
| 404 | Produto inexistente **ou excluído** (exceto consulta de histórico, ver 4.3) |
| 409 | SKU/nome duplicado (inclusive de produto excluído); violação de integridade; estoque insuficiente |
| 500 | Inesperado — mensagem genérica |

### 4.2 Produtos

`ProductResponse` e `ProductResponseAll` (mesmos campos; usados em GET por id/POST/PUT e nas listas):
```json
{ "id": 1, "sku": "MOUSE-001", "name": "Mouse Gamer", "description": "…", "price": 129.90,
  "quantity": 25, "min_stock": 10, "low_stock": false,
  "created_at": "2026-10-30T10:00:00", "updated_at": "2026-10-30T10:00:00" }
```
`low_stock = quantity < min_stock`.

| Rota | Request | Resposta |
|---|---|---|
| `GET /product` | — | 200 `ProductResponseAll[]` (só ativos; `[]` se vazio) |
| `GET /product/search?ids=&createdFrom=&createdTo=&q=&lowStock=` | opcionais (`lowStock=true` → só `quantity < min_stock`) | 200 `ProductResponseAll[]` (só ativos) |
| `GET /product/{id}` | — | 200 `ProductResponse` / 404 (inexistente ou excluído) |
| `POST /product/create` | `{sku*≤50, name*≤150, description?≤500, price*≥0, quantity*≥0, min_stock*≥0}` | **201** `ProductResponse` / 400 / 409. `quantity>0` → 1 `ENTRY` (descrição "Entrada de estoque"); `quantity=0` → nenhuma movimentação |
| `PUT /product/{id}` | `{sku?, name?, description?, price?≥0, quantity?≥0, min_stock?≥0}` (campo informado não pode ser vazio/em branco) | 200 `ProductResponse` / 400 / 404 / 409. `quantity` diferente do atual → movimentação de ajuste (D-1) |
| `DELETE /product/{id}` | — | **204** (soft delete: `deleted_at = now`, sem movimentação, estoque preservado) / 404 (inexistente ou já excluído) |

### 4.3 Movimentações

`MovementResponse`:
```json
{ "id": 10, "product_id": 1, "product_sku": "MOUSE-001", "product_name": "Mouse Gamer", "product_deleted": false,
  "type": "EXIT", "quantity": 5, "description": "Separacao de pedidos", "stock_after": 20,
  "created_at": "2026-10-30T10:05:00" }
```
`stock_after` só é preenchido na resposta do `POST` (saldo após a operação, lido do MySQL); em `GET` vem `null` (o saldo histórico não é armazenado — não inventamos coluna). `product_deleted` = produto tem `deleted_at`.

| Rota | Request | Resposta |
|---|---|---|
| `POST /movements` | `{product_id*, type* (ENTRY\|EXIT), quantity*>0, description?≤255}` | **201** `MovementResponse` / 400 / 404 (produto inexistente **ou excluído**) / 409 (saída > saldo) |
| `GET /movements?product_id=&type=&from=&to=` | opcionais (`from`/`to` ISO date-time) | 200 `MovementResponse[]` ordem `created_at desc, id desc`; `[]` se vazio. Com `product_id` de produto **excluído** → 200 com o histórico; produto que nunca existiu → 404. Sem `product_id` → movimentações de todos os produtos, inclusive excluídos |

`description` ausente → "Entrada de estoque" / "Saída de estoque". Cliente nunca envia saldo.

---

## 5. Tarefas por área

### 5.1 Banco de dados

#### DB-01 — Soft delete e unicidade de nome `[/]`
- **Objetivo:** suportar D-2 e reforçar D-6 sem remover dados.
- **Alteração (justificada em T-06):** `ALTER TABLE products ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;` e `ALTER TABLE products ADD CONSTRAINT uk_products_name UNIQUE (name);` (pré-checagem de nomes duplicados documentada no script; se houver duplicados, a migração deve abortar sem alterar nada).
- **Arquivos:** `database/init.sql` (instalações novas: coluna + unique já na `CREATE TABLE`) e `database/migrations/001_soft_delete_and_unique_name.sql` (bancos existentes). Nenhum `DROP`/`DELETE`/`TRUNCATE`.
- **Aceite:** `init.sql` aplica em MySQL 8 limpo; migração aplica sobre o schema antigo (fixture em teste); app sobe com `ddl-auto=validate`.
- **Dependência:** nenhuma. **Executado em:** BE-1 (arquivos) e validado em BE-3 (Testcontainers).

### 5.2 Backend

**Fase 1 — correções, segurança, soft delete (onda BE-1)**

- **FIX-01 `[x]`** Corrigir `ProductMapper.toDtoAll`; `ProductResponse` completo + `low_stock` (T-09). Aceite: produto seed `MOUSE-001` mapeia `sku`, `name`, `quantity=25`, `min_stock=10`, `low_stock=false`; `KEYBOARD-001` (8<10) → `true`.
- **FIX-02 `[x]`** DTOs: `price` ≥0 com `@Digits(8,2)`; `sku` `@NotBlank @Size(50)`; `name` `@NotBlank @Size(150)`; `description` `@Size(500)`; `quantity`/`min_stock` `Integer @NotNull @PositiveOrZero` (create) ou `@PositiveOrZero` (update); no update, strings informadas não podem ser vazias. Aceite: preço 0 aceito; preço −1, `quantity` −1, `name=""`, `sku` 51 chars → 400 com `field_errors`.
- **FIX-03 `[x]`** Handler global (T-11): 404, 409 (`SameProductException`, `DataIntegrityViolationException`), 400 (validação com `field_errors`, JSON ilegível, tipo de parâmetro), 500 genérico com log. Mover o handler atual (sem duplicar) e remover `basePackages`.
- **FIX-04 `[/]`** `Product`/`Movements`: `@Data` → `@Getter @Setter` (+ `@ToString.Exclude`); mapear `Product.deletedAt` (`deleted_at`); revisar `Movements.createdAt` (P-09) sem mudar schema.
- **FIX-05 `[/]`** `createProduct`: retorna 201; timestamps preenchidos na resposta; movimentação inicial **só se `quantity > 0`** (D-4); usar `org.springframework.transaction.annotation.Transactional` (leituras `readOnly`).
- **SEC-01 `[x]`** Perfis (T-07): `dev`/`test` → `permitAll` em `/product/**`, `/movements/**` + CORS (`stockflow.cors.allowed-origins`, padrão `http://localhost:3000`); padrão → tudo `authenticated()`, 401 via `HttpStatusEntryPoint`, sem CORS; `application.properties` com `show-sql=false`, e `application-dev.properties` com `show-sql=true`. Aceite (testes de segurança): perfil `dev` → `GET /product` não retorna 401; sem perfil → 401; pré-flight CORS de `localhost:3000` OK em `dev` e **negado** sem perfil.
- **PRD-01 `[/]`** Soft delete: `ProductRepository` com `findByIdAndDeletedAtIsNull`, `findActiveByIdForUpdate` (lock, T-04), `existsBy…` de nome/SKU **sem** filtro de exclusão; `ProductSpecifications` com `deletedAt IS NULL` e filtro `lowStock`; `ProductService.findAll/search/findById/update/delete` só enxergam ativos; `DELETE /product/{id}` → 204/404; `deleteProduct` não remove movimentações nem linhas.
- **DB-01** — ver 5.1 (executado nesta onda).

**Fase 2 — movimentações (onda BE-2, depende de BE-1)**

- **MOV-01 `[/]`** `StockMovementService` (`@Transactional`), `MovementsController` (`POST /movements`), `CreateMovementRequest`, `MovementResponse`, `InsufficientStockException` (409), `MovementsMapper` sem expor entidade (remover/substituir `MovimentsDtoAll`, remover `MovementsMapper` não usado em `ProductService`). Regras: qty>0; saída não pode deixar saldo <0; saldo + registro na mesma transação; produto inexistente/excluído → 404; lock T-04. Aceite: ver 4.3 + rollback.
- **MOV-02 `[/]`** `GET /movements` com filtros (4.3), histórico de produto excluído acessível.
- **MOV-03 `[/]`** `PUT` com `quantity`: delega ao `StockMovementService` (diferença → `ENTRY`/`EXIT`, `ATUALIZACAO`), mesma transação; `createProduct` também usa o serviço para a `ENTRY` inicial (T-03).

**Fase 3 — qualidade backend (onda BE-3, depende de BE-2 e de Docker)**

- **QA-01 `[x]`** Unitários (JUnit/Mockito): serviços, mappers (`low_stock`), regras D-1/D-4/D-6.
- **QA-02 `[x]`** `@WebMvcTest` dos controllers e segurança por perfil (status, payload, formato de erro).
- **QA-03 `[!]`** Integração com **Testcontainers MySQL** (executando `database/init.sql`): contexto sobe com `validate`; fluxo completo; **concorrência** (N saídas simultâneas — saldo final nunca <0, soma das saídas aceitas = redução do saldo); **rollback** (falha após atualizar saldo não deixa saldo ou movimentação órfãos); **consistência** (saldo = Σ ENTRY − Σ EXIT por produto); constraints (CHECK, FK, UNIQUE de sku/nome inclusive excluídos); soft delete (histórico acessível, nova movimentação 404, nome/SKU de excluído continua reservado); migração 001 sobre schema antigo. Reescrever `StockflowApiApplicationTests`/`DatabaseConnectionTest` para usar o container. **Bloqueio:** Docker Desktop precisa estar rodando.

### 5.3 Frontend (Next.js + TypeScript + Tailwind; sem biblioteca de UI)

Pastas sugeridas (App Router): `src/app/`, `src/components/` (componentes próprios: Button, Input, Badge, Table, Alert, Spinner, EmptyState, ConfirmDialog), `src/lib/api.ts`, `src/types/`.

- **FE-01 `[x]`** Scaffold, `strict`, ESLint, Tailwind, Vitest + Testing Library, `.env.example` (`NEXT_PUBLIC_API_URL=http://localhost:8080`), cliente de API que interpreta o erro 4.1, tipos de 4.2/4.3, layout responsivo com navegação (Produtos, Movimentações).
- **FE-02 `[x]`** `/products`: tabela/cards responsivos; busca `q`; filtro "Estoque baixo" (`lowStock=true`); **badge "Estoque baixo"** quando `low_stock` e contador de itens a repor; estados carregando/erro/vazio.
- **FE-03 `[x]`** `/products/new` e `/products/[id]/edit`: formulário com validação espelhando FIX-02, erros do backend por campo, 409 de SKU/nome, desabilita envio em andamento; edição de `quantity` avisa que gera movimentação de ajuste.
- **FE-04 `[x]`** `/products/[id]`: dados, badge de estoque baixo, **excluir** com confirmação (→ 204 → volta à lista), histórico do produto, formulário de **entrada/saída** (qty>0; 409 "estoque insuficiente" sem alterar tela; usa `stock_after`/recarrega — nunca calcula saldo localmente).
- **FE-05 `[x]`** `/movements` (+ `?product_id=`): histórico geral/filtrado, com filtro por tipo e produto; produtos excluídos exibidos com marca "excluído" (histórico continua acessível, sem ação de movimentar).
- **QA-04 `[x]`** Testes Vitest/Testing Library: cliente de API/erros, formulários e validações, badge/filtro de estoque baixo, estados loading/erro/vazio, formulário de movimentação, exclusão com confirmação.

### 5.4 Tech Lead

- **ENV-01 `[!]`** Docker Desktop **não está em execução** (daemon inacessível; distros WSL `docker-desktop`/`Ubuntu` paradas). Ação do usuário: abrir o Docker Desktop e aguardar "Engine running". Sem isso QA-03 e INT-01 não executam.
- **CFG-01 `[x]`** Ajustar `.vscode/launch.json` e `.env.example` com `SPRING_PROFILES_ACTIVE=dev` (T-07). **Não toco no `.env` real.**
- **INT-01 `[!]`** Verificação integrada: backend (perfil `dev`) + MySQL (Docker) + frontend; fluxos completos e conferência do banco.
- **QA-05 `[x]`** `README.md` (pré-requisitos, perfis, `.env`, aplicar `init.sql`/migração 001, Docker, rodar testes, contratos).

---

## 6. Dependências e ordem de execução

```
ENV-01 (usuário)  ───────────────────────────────────────────┐ (só para QA-03 / INT-01)
Onda 1 (paralelo, pastas disjuntas)                           │
  BE-1: DB-01, FIX-01..05, SEC-01, PRD-01                     │
  FE-1: FE-01, FE-02 (contrato 4.2)                           │
Revisão Tech Lead de BE-1 e FE-1  (+ CFG-01)                  │
Onda 2 (paralelo)                                             │
  BE-2: MOV-01, MOV-02, MOV-03        (depende de BE-1)       │
  FE-2: FE-03, FE-04, FE-05 (contratos 4.2/4.3 congelados)    │
Revisão Tech Lead de BE-2 e FE-2                              │
Onda 3 (paralelo)                                             │
  BE-3: QA-01..03 (QA-03 exige Docker) ◄──────────────────────┘
  FE-3: QA-04
Onda 4: INT-01 (Tech Lead, exige Docker) → QA-05
```
Motivo de BE-1 → BE-2 → BE-3 em sequência: todas tocam `ProductService`, `ProductRepository` e handlers. Frontend e backend rodam em paralelo porque os contratos da seção 4 estão congelados; a **compatibilidade real** só é confirmada na INT-01.

### Escopo dos agentes
| Agente | Pode modificar | Não pode |
|---|---|---|
| `backend-developer` | `backend/**`; `database/init.sql` e `database/migrations/**` (apenas DB-01) | `frontend/`, `.cursor/`, `.vscode/`, `.env*`, `*.md` da raiz, `comandos/`; DDL destrutivo; commits |
| `frontend-developer` | `frontend/**` | `backend/`, `database/`, raiz do repositório; endpoints fora da seção 4; commits |
| Tech Lead | `IMPLEMENTATION_PLAN.md`, `README.md`, `.vscode/launch.json`, `.env.example` | código de produto; `.env` real |

### Checklist de revisão do Tech Lead
- [ ] Segue plano e contratos; nada inventado.
- [ ] Regras do `AGENTS.md` e decisões D-1…D-10 respeitadas.
- [ ] Testes novos existem **e** reexecutei (`mvnw test` / `npm test`, `lint`, `build`).
- [ ] `git status`/diff sem alterações fora do escopo.
- [ ] Sem dependência nova injustificada; schema só via DB-01.
- [ ] FE × BE compatíveis (nomes, tipos, status).
- [ ] Nenhum commit.

---

## 7. Critérios de aceitação transversais
1. Nenhum caminho altera `products.quantity` sem gerar movimentação (exceto criação com qty 0).
2. Para qualquer produto: `quantity = Σ ENTRY − Σ EXIT` das suas movimentações (para produtos criados pela API).
3. Saída nunca deixa saldo negativo, inclusive sob concorrência.
4. Nenhum `DELETE` físico de produto ou movimentação existe no código.
5. Produto excluído: invisível em `/product*`, 404 em `POST /movements`, histórico visível em `GET /movements`.
6. Em produção (sem perfil) nenhuma rota responde sem autenticação.

---

## 8. Registro de andamento

**Leitura dos status:** `[x]` = implementado, revisado e **testes executados com sucesso**. `[/]` = implementado e revisado; testes unitários verdes, mas a **verificação contra MySQL real ainda não foi executada** (Docker desligado) — não está aprovado. `[!]` = bloqueado.

| Data | Item | Status | Nota |
|---|---|---|---|
| 2026-10-30 | Diagnóstico e planejamento (rev. 1) | `[x]` | |
| 2026-10-30 | Decisões D-1…D-10 incorporadas (rev. 2) | `[x]` | |
| 2026-10-30 | Onda 1 — BE-1 (DB-01, FIX-01..05, SEC-01, PRD-01) | `[/]` | 79 testes verdes na época (reexecutados pelo TL). SQL (`init.sql`, migração 001), `@Generated`, lock: não executados em MySQL |
| 2026-10-30 | Onda 1 — FE-1 (FE-01, FE-02) | `[x]` | lint OK, build OK, testes OK (reexecutados pelo TL) |
| 2026-10-30 | Onda 2 — BE-2 (MOV-01..03) | `[/]` | 124 testes verdes (reexecutados). Lock pessimista, rollback real, concorrência: não executados |
| 2026-10-30 | Onda 2 — FE-2 (FE-03..05) | `[x]` | lint OK, build OK, 122 testes OK (reexecutados). Integração real com o backend: pendente (INT-01) |
| 2026-10-30 | Onda 3 — BE-3 (QA-03) | `[!]` | 68 testes de integração **escritos e compilados, NÃO executados**. Suíte unitária: 128 verdes (`-DexcludedGroups=integration`). `mvnw test` completo falha de forma explícita sem Docker |
| 2026-10-30 | CFG-01, QA-04, QA-05 (README) | `[x]` | |
| — | ENV-01 (Docker Desktop) | `[!]` | Ação do usuário: iniciar o Docker Desktop |
| — | INT-01 (ponta a ponta) | `[!]` | Depende de ENV-01 + MySQL com `init.sql` (ou migração 001 em banco existente) |

### Pendências para fechar o projeto
1. Iniciar o Docker Desktop e executar `cd backend; .\mvnw.cmd clean test` (suíte completa, 196 testes). Qualquer falha de integração deve voltar para o `backend-developer` (BE-3) — esperado ajustar testes, não produção; riscos listados no relatório da BE-3 (proxy `@MockitoSpyBean`, leitura nativa nos testes de rollback, `ScriptUtils` com o `init.sql` real).
2. Só então marcar `[x]`: DB-01, FIX-04, FIX-05, PRD-01, MOV-01..03 e QA-03.
3. INT-01 (TL): subir MySQL, aplicar `database/init.sql`, backend com `SPRING_PROFILES_ACTIVE=dev`, frontend com `npm run dev`; percorrer: listar → criar → editar quantidade (ajuste) → entrada → saída válida → saída inválida (409) → excluir → histórico do excluído; conferir o banco.
4. Se existir banco local antigo: aplicar **uma vez** `database/migrations/001_soft_delete_and_unique_name.sql` (executar antes a query de duplicados).

### Observações técnicas registradas na revisão
- `PUT` não permite limpar `description` (strings informadas não podem ser vazias, por plano); o frontend bloqueia esvaziar descrição existente. Aceito; reavaliar se necessário.
- Entrada que estoure `int` vira 500 genérico (`ArithmeticException`) — regra de limite máximo não definida; fora do escopo.
- `ProductService.createProduct` faz INSERT com saldo 0 e UPDATE com o saldo inicial (resultado igual ao plano; garante que o saldo só muda via `StockMovementService`).
- `frontend/AGENTS.md` e `frontend/README.md` foram gerados pelo `create-next-app` (não editados).
- A API em produção (sem perfil `dev`/`test`) responde 401 em tudo até existir autenticação (FUT-01).

---
## 9. Melhorias futuras (fora do escopo atual)
FUT-01 auth/JWT/roles (substitui o perfil `dev` aberto) · FUT-02 cache Redis só para leitura · FUT-03 Docker Compose da aplicação · FUT-04 etiquetas assíncronas (Redis Streams/PDFBox) · FUT-05 paginação · FUT-06 Actuator/observabilidade · FUT-07 Flyway/Liquibase (substituir migrações manuais) · FUT-08 remover Redis/PDFBox do `pom` se seguirem sem uso · FUT-09 reativação (restore) de produto excluído.
