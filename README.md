# StockFlow

Gerenciamento de produtos e estoque.

- **Backend:** Java 21, Spring Boot 4, Spring Data JPA, MySQL (Redis presente, ainda sem uso).
- **Frontend:** Next.js, TypeScript, Tailwind CSS, Vitest + Testing Library.
- Plano, decisões e contratos de API: [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md). Regras do projeto: [`AGENTS.md`](AGENTS.md).

## Regras principais

- SKU e nome de produto são únicos (inclusive entre produtos excluídos). Preço não pode ser negativo.
- Todo estoque muda **somente** por movimentações (entrada/saída/ajuste), na mesma transação; saída não deixa saldo negativo. O MySQL é a fonte da verdade.
- Exclusão de produto é **lógica** (`deleted_at`): o histórico de movimentações é preservado e continua consultável.
- Estoque baixo: `quantity < min_stock` (campo `low_stock` da API).

## Pré-requisitos

- JDK 21+ e Maven Wrapper (incluso), Node.js 22+ (ou 24+), MySQL 8.
- Docker Desktop **em execução** para os testes de integração (Testcontainers).

## Configuração

1. Copie `.env.example` para `.env` (na raiz) e preencha `DB_PASSWORD`. O `.env` é ignorado pelo git.
2. **Banco novo:** execute `database/init.sql` em um MySQL 8 vazio.
   **Banco já existente** (criado antes do soft delete): execute **uma única vez** `database/migrations/001_soft_delete_and_unique_name.sql`
   (antes, rode a query de nomes duplicados descrita no cabeçalho do script; ele não remove dados).
3. Frontend: `cp frontend/.env.example frontend/.env.local` (`NEXT_PUBLIC_API_URL=http://localhost:8080`).

## Perfis de segurança

| Perfil | Comportamento |
|---|---|
| `dev` / `test` | `/product/**` e `/movements/**` abertos + CORS para `http://localhost:3000` (`stockflow.cors.allowed-origins`) |
| *(nenhum — padrão/produção)* | Tudo exige autenticação (401) e não há CORS. **Não use `dev` em produção.** |

`SPRING_PROFILES_ACTIVE=dev` já está em `.vscode/launch.json` e `.env.example`.

## Executar

```powershell
# Backend (http://localhost:8080)
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"   # e variáveis do .env (DB_*, MYSQL_*)
.\mvnw.cmd spring-boot:run

# Frontend (http://localhost:3000)
cd frontend
npm install
npm run dev
```

## Testes

```powershell
# Backend — somente unitários (não precisa de Docker)
cd backend; .\mvnw.cmd clean test -DexcludedGroups=integration

# Backend — suíte completa, incluindo integração com MySQL real via Testcontainers (exige Docker Desktop ligado)
cd backend; .\mvnw.cmd clean test

# Frontend
cd frontend; npm run lint; npm test; npm run build
```

Sem Docker, os testes marcados `@Tag("integration")` **falham de forma explícita** (não são ignorados).

## API (resumo — contratos completos na seção 4 do plano)

| Método | Rota | Resposta |
|---|---|---|
| GET | `/product` · `/product/search?ids&createdFrom&createdTo&q&lowStock` · `/product/{id}` | 200 |
| POST | `/product/create` | 201 |
| PUT | `/product/{id}` (alterar `quantity` gera movimentação de ajuste) | 200 |
| DELETE | `/product/{id}` (exclusão lógica) | 204 |
| POST | `/movements` `{product_id, type: ENTRY\|EXIT, quantity, description?}` | 201 (409 se saldo insuficiente) |
| GET | `/movements?product_id&type&from&to` | 200 |

Erros: `{ "error", "message", "field_errors"? }` (400, 404, 409, 500).

## Limitações conhecidas

Sem autenticação ainda (planejada), sem paginação, sem reativação de produto excluído, Redis/PDFBox ainda não utilizados.
