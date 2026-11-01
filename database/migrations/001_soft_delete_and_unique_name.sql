-- =============================================================================
-- Migracao 001 - Soft delete (products.deleted_at) e nome de produto unico
-- Tarefa: DB-01 (IMPLEMENTATION_PLAN.md) | Decisoes: D-2, D-6 | Tecnica: T-06
--
-- ATENCAO - ESTE SCRIPT NAO E IDEMPOTENTE. EXECUTE UMA UNICA VEZ.
--   Rodar de novo falha com "Duplicate column name 'deleted_at'" (nada e alterado).
--
-- Aplica em bancos EXISTENTES (criados com o init.sql antigo). Bancos novos ja
-- nascem com estas alteracoes no database/init.sql e NAO devem rodar este script.
--
-- Como executar (MySQL 8, selecionando o banco explicitamente):
--     mysql -u root -p stockflow_db < database/migrations/001_soft_delete_and_unique_name.sql
--
-- Este script NAO contem DROP / DELETE / TRUNCATE: nenhum dado e removido.
--
-- -----------------------------------------------------------------------------
-- PRE-CHECAGEM DE NOMES DUPLICADOS (leia antes de executar)
-- -----------------------------------------------------------------------------
-- A regra "nome unico" (UNIQUE em products.name) nao pode ser criada se ja
-- existirem nomes repetidos. A comparacao do MySQL usa a collation da coluna
-- (utf8mb4_unicode_ci = sem diferenca de maiusculas/minusculas e sem diferenca
-- de espacos no final), a mesma usada pela query abaixo. Para diagnosticar:
--
--     SELECT name, COUNT(*) AS ocorrencias, GROUP_CONCAT(id ORDER BY id) AS ids
--     FROM products
--     GROUP BY name
--     HAVING COUNT(*) > 1;
--
-- Se retornar linhas, corrija os nomes manualmente (ex.: UPDATE products SET
-- name = ... WHERE id = ...) ANTES de rodar a migracao. Nada e corrigido
-- automaticamente aqui.
--
-- -----------------------------------------------------------------------------
-- COMO A MIGRACAO FALHA SEM ALTERAR NADA SE HOUVER DUPLICADOS
-- -----------------------------------------------------------------------------
-- As duas alteracoes estao em UM UNICO "ALTER TABLE". No MySQL 8 o DDL do InnoDB
-- e atomico: se a criacao do indice UNIQUE falhar por duplicados (erro 1062
-- "Duplicate entry ... for key 'products.uk_products_name'"), o comando inteiro
-- e desfeito e a coluna deleted_at TAMBEM nao e criada. O cliente "mysql" para
-- no primeiro erro (nao use --force). Foi escolhido este mecanismo em vez de
-- procedimento com SIGNAL porque nao depende de DELIMITER (que so existe no
-- cliente de linha de comando) e funciona em qualquer cliente/driver.
--
-- A SELECT abaixo apenas LISTA duplicados para facilitar a leitura do log; ela
-- nao bloqueia nada. Quem garante o "tudo ou nada" e o ALTER TABLE unico.
-- =============================================================================

-- Diagnostico informativo (deve retornar 0 linhas)
SELECT name, COUNT(*) AS ocorrencias, GROUP_CONCAT(id ORDER BY id) AS ids
FROM products
GROUP BY name
HAVING COUNT(*) > 1;

-- Alteracao atomica: coluna de soft delete + unicidade de nome
ALTER TABLE products
    ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL,
    ADD CONSTRAINT uk_products_name UNIQUE (name);
