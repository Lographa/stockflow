"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { listProducts, searchProducts } from "@/lib/api";
import { formatBRL } from "@/lib/format";
import type { Product } from "@/types";
import { Alert } from "./Alert";
import { Badge } from "./Badge";
import { Button, buttonClasses } from "./Button";
import { EmptyState } from "./EmptyState";
import { Input } from "./Input";
import { Spinner } from "./Spinner";

const SEARCH_DEBOUNCE_MS = 300;

interface RequestResult {
  key: string;
  products?: Product[];
  errorMessage?: string;
}

function LowStockBadge() {
  return <Badge tone="danger">Estoque baixo</Badge>;
}

function replenishmentText(count: number): string {
  return count === 1
    ? "1 produto precisa de reposição"
    : `${count} produtos precisam de reposição`;
}

export function ProductList() {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);
  const [result, setResult] = useState<RequestResult | null>(null);

  // Debounce curto da busca por texto.
  useEffect(() => {
    const timer = setTimeout(
      () => setDebouncedQuery(query.trim()),
      SEARCH_DEBOUNCE_MS,
    );
    return () => clearTimeout(timer);
  }, [query]);

  // O resultado carrega a chave da requisição que o gerou: "carregando" é
  // derivado de (chave atual ≠ chave do resultado), sem setState síncrono no efeito.
  const requestKey = `${debouncedQuery}|${lowStockOnly}|${reloadCount}`;

  useEffect(() => {
    const controller = new AbortController();
    const hasFilters = debouncedQuery !== "" || lowStockOnly;
    const options = { signal: controller.signal };
    const request = hasFilters
      ? searchProducts({ q: debouncedQuery, lowStock: lowStockOnly }, options)
      : listProducts(options);

    request
      .then((products) => {
        if (!controller.signal.aborted) {
          setResult({ key: requestKey, products });
        }
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setResult({
          key: requestKey,
          errorMessage:
            err instanceof Error
              ? err.message
              : "Ocorreu um erro inesperado ao carregar os produtos.",
        });
      });

    return () => controller.abort();
  }, [debouncedQuery, lowStockOnly, requestKey]);

  const loading = result?.key !== requestKey;
  const products = !loading ? result?.products : undefined;
  const errorMessage = !loading ? result?.errorMessage : undefined;
  const hasFilters = debouncedQuery !== "" || lowStockOnly;
  const lowStockCount = products?.filter((p) => p.low_stock).length ?? 0;

  return (
    <section aria-labelledby="titulo-produtos" className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1 id="titulo-produtos" className="text-2xl font-bold">
          Produtos
        </h1>
        <Link href="/products/new" className={buttonClasses("primary")}>
          Novo produto
        </Link>
      </div>

      <form
        role="search"
        aria-label="Filtrar produtos"
        onSubmit={(event) => event.preventDefault()}
        className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:flex-row sm:items-end"
      >
        <div className="flex-1">
          <Input
            type="search"
            label="Buscar produtos"
            placeholder="Digite para buscar"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <label className="flex items-center gap-2 pb-2 text-sm font-medium text-slate-700">
          <input
            type="checkbox"
            checked={lowStockOnly}
            onChange={(event) => setLowStockOnly(event.target.checked)}
            className="h-4 w-4 rounded border-slate-300 accent-indigo-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
          />
          Somente estoque baixo
        </label>
      </form>

      <div aria-live="polite" aria-busy={loading}>
        {loading && (
          <div className="flex justify-center py-12">
            <Spinner label="Carregando produtos…" />
          </div>
        )}

        {errorMessage !== undefined && (
          <Alert
            variant="error"
            title="Não foi possível carregar os produtos"
            action={
              <Button
                variant="secondary"
                onClick={() => setReloadCount((count) => count + 1)}
              >
                Tentar novamente
              </Button>
            }
          >
            {errorMessage}
          </Alert>
        )}

        {products !== undefined && products.length === 0 && (
          <EmptyState
            title={
              hasFilters
                ? "Nenhum produto encontrado"
                : "Nenhum produto cadastrado"
            }
            description={
              hasFilters
                ? "Ajuste a busca ou remova o filtro de estoque baixo."
                : "Cadastre o primeiro produto para começar a controlar o estoque."
            }
            action={
              hasFilters ? undefined : (
                <Link href="/products/new" className={buttonClasses("primary")}>
                  Novo produto
                </Link>
              )
            }
          />
        )}

        {products !== undefined && products.length > 0 && (
          <div className="flex flex-col gap-3">
            {lowStockCount > 0 && (
              <p
                data-testid="low-stock-counter"
                className="text-sm font-medium text-red-700"
              >
                {replenishmentText(lowStockCount)}
              </p>
            )}
            <ProductTable products={products} />
            <ProductCards products={products} />
          </div>
        )}
      </div>
    </section>
  );
}

/** Tabela para telas largas (md+). */
function ProductTable({ products }: { products: Product[] }) {
  return (
    <div className="hidden overflow-x-auto rounded-lg border border-slate-200 bg-white md:block">
      <table className="w-full text-left text-sm" aria-label="Lista de produtos">
        <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">
              SKU
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Nome
            </th>
            <th scope="col" className="px-4 py-3 text-right font-medium">
              Preço
            </th>
            <th scope="col" className="px-4 py-3 text-right font-medium">
              Quantidade
            </th>
            <th scope="col" className="px-4 py-3 text-right font-medium">
              Estoque mínimo
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {products.map((product) => (
            <tr key={product.id}>
              <td className="px-4 py-3 font-mono text-xs">{product.sku}</td>
              <td className="px-4 py-3">
                <span className="mr-2 font-medium">{product.name}</span>
                {product.low_stock && <LowStockBadge />}
              </td>
              <td className="px-4 py-3 text-right">
                {formatBRL(product.price)}
              </td>
              <td className="px-4 py-3 text-right">{product.quantity}</td>
              <td className="px-4 py-3 text-right">{product.min_stock}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Cards para telas estreitas (< md). */
function ProductCards({ products }: { products: Product[] }) {
  return (
    <ul
      aria-label="Lista de produtos em cartões"
      className="flex flex-col gap-3 md:hidden"
    >
      {products.map((product) => (
        <li
          key={product.id}
          className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-4"
        >
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className="font-medium">{product.name}</p>
              <p className="font-mono text-xs text-slate-500">{product.sku}</p>
            </div>
            {product.low_stock && <LowStockBadge />}
          </div>
          <dl className="grid grid-cols-3 gap-2 text-sm">
            <div>
              <dt className="text-xs text-slate-500">Preço</dt>
              <dd>{formatBRL(product.price)}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Quantidade</dt>
              <dd>{product.quantity}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Mínimo</dt>
              <dd>{product.min_stock}</dd>
            </div>
          </dl>
        </li>
      ))}
    </ul>
  );
}
