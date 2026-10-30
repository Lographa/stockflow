"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { ApiRequestError, listMovements, listProducts } from "@/lib/api";
import { parseId } from "@/lib/ids";
import { useApiData } from "@/lib/useApiData";
import type { MovementType } from "@/types";
import { Alert } from "./Alert";
import { Button, buttonClasses } from "./Button";
import { EmptyState } from "./EmptyState";
import { MovementTable } from "./MovementTable";
import { Select } from "./Select";
import { Spinner } from "./Spinner";

type TypeFilter = "" | MovementType;

/**
 * /movements — histórico geral ou de um produto (`?product_id=`).
 * O produto selecionado vem SEMPRE da URL (fonte única): o seletor apenas
 * navega. Se o id não estiver em `GET /product` (ex.: produto excluído), o
 * filtro continua ativo e o nome é obtido das próprias movimentações.
 */
export function MovementList() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const productId = parseId(searchParams.get("product_id"));
  const [type, setType] = useState<TypeFilter>("");

  const movementsData = useApiData(
    (signal) =>
      listMovements(
        { productId, type: type === "" ? undefined : type },
        { signal },
      ),
    `${productId ?? ""}|${type}`,
  );
  // Falha ao carregar produtos não impede o histórico: o seletor só perde opções.
  const productsData = useApiData((signal) => listProducts({ signal }), "all");

  const { data: movements, error, loading, reload } = movementsData;
  const products = productsData.data ?? [];

  function selectProduct(value: string) {
    router.replace(value === "" ? "/movements" : `/movements?product_id=${value}`);
  }

  const fromMovements =
    productId !== undefined
      ? movements?.find((movement) => movement.product_id === productId)
      : undefined;
  // Lembra o nome do produto filtrado mesmo enquanto o filtro de tipo recarrega
  // a lista (ajuste de estado durante a renderização, padrão suportado pelo React).
  const [knownProduct, setKnownProduct] = useState<{
    id: number;
    label: string;
  } | null>(null);
  if (fromMovements && productId !== undefined) {
    const label = `${fromMovements.product_name} (${fromMovements.product_sku})${
      fromMovements.product_deleted ? " — excluído" : ""
    }`;
    if (!knownProduct || knownProduct.id !== productId || knownProduct.label !== label) {
      setKnownProduct({ id: productId, label });
    }
  }
  const filteredProductLabel =
    knownProduct && knownProduct.id === productId
      ? knownProduct.label
      : `Produto #${productId}`;
  const activeInList = products.some((product) => product.id === productId);
  const hasFilters = productId !== undefined || type !== "";
  const productNotFound =
    error instanceof ApiRequestError &&
    error.status === 404 &&
    productId !== undefined;

  return (
    <section aria-labelledby="titulo-movimentacoes" className="flex flex-col gap-4">
      <h1 id="titulo-movimentacoes" className="text-2xl font-bold">
        Movimentações
      </h1>

      <form
        role="search"
        aria-label="Filtrar movimentações"
        onSubmit={(event) => event.preventDefault()}
        className="grid gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2"
      >
        <Select
          label="Produto"
          name="product_id"
          value={productId !== undefined ? String(productId) : ""}
          onChange={(event) => selectProduct(event.target.value)}
        >
          <option value="">Todos os produtos</option>
          {productId !== undefined && !activeInList && (
            <option value={productId}>{filteredProductLabel}</option>
          )}
          {products.map((product) => (
            <option key={product.id} value={product.id}>
              {product.name} ({product.sku})
            </option>
          ))}
        </Select>
        <Select
          label="Tipo"
          name="type"
          value={type}
          onChange={(event) => setType(event.target.value as TypeFilter)}
        >
          <option value="">Todos</option>
          <option value="ENTRY">Entrada</option>
          <option value="EXIT">Saída</option>
        </Select>
      </form>

      <div aria-live="polite" aria-busy={loading}>
        {loading && !movements && (
          <div className="flex justify-center py-12">
            <Spinner label="Carregando movimentações…" />
          </div>
        )}

        {error && (
          <Alert
            variant="error"
            title={
              productNotFound
                ? "Produto não encontrado"
                : "Não foi possível carregar as movimentações"
            }
            action={
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" onClick={reload}>
                  Tentar novamente
                </Button>
                {productNotFound && (
                  <Link href="/movements" className={buttonClasses("secondary")}>
                    Ver todas as movimentações
                  </Link>
                )}
              </div>
            }
          >
            {error.message}
          </Alert>
        )}

        {movements && movements.length === 0 && (
          <EmptyState
            title={
              hasFilters
                ? "Nenhuma movimentação encontrada"
                : "Nenhuma movimentação registrada"
            }
            description={
              hasFilters
                ? "Ajuste os filtros de produto e tipo."
                : "As entradas e saídas de estoque aparecerão aqui."
            }
          />
        )}

        {movements && movements.length > 0 && (
          <MovementTable movements={movements} label="Lista de movimentações" />
        )}
      </div>
    </section>
  );
}
