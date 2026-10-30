"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { ReactNode } from "react";
import {
  ApiRequestError,
  deleteProduct,
  getProduct,
  listMovements,
} from "@/lib/api";
import { formatBRL, formatDateTime } from "@/lib/format";
import { parseId } from "@/lib/ids";
import { useApiData } from "@/lib/useApiData";
import type { ApiData } from "@/lib/useApiData";
import type { Movement, Product } from "@/types";
import { Alert } from "./Alert";
import { Badge } from "./Badge";
import { Button, buttonClasses } from "./Button";
import { ConfirmDialog } from "./ConfirmDialog";
import { EmptyState } from "./EmptyState";
import { MovementForm } from "./MovementForm";
import { MovementTable } from "./MovementTable";
import { ProductNotFound } from "./ProductNotFound";
import { Spinner } from "./Spinner";

/** Quantidade de movimentações exibidas no detalhe (as mais recentes). */
const HISTORY_LIMIT = 10;

/** /products/[id] — dados, exclusão, registro de entrada/saída e histórico. */
export function ProductDetail({ productId: rawId }: { productId: string }) {
  const productId = parseId(rawId);
  if (productId === undefined) {
    return <ProductNotFound />;
  }
  return <ProductDetailLoader productId={productId} />;
}

function ProductDetailLoader({ productId }: { productId: number }) {
  const router = useRouter();
  const productData = useApiData(
    (signal) => getProduct(productId, { signal }),
    String(productId),
  );
  const historyData = useApiData(
    (signal) => listMovements({ productId }, { signal }),
    String(productId),
  );

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const { data: product, error, loading, reload: reloadProduct } = productData;

  async function handleDelete() {
    if (deleting) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteProduct(productId);
      router.push("/products?deleted=1");
    } catch (err) {
      setConfirmOpen(false);
      setDeleting(false);
      setDeleteError(
        err instanceof Error
          ? err.message
          : "Ocorreu um erro inesperado ao excluir o produto.",
      );
    }
  }

  function handleMovementCreated() {
    // O saldo exibido SEMPRE vem do backend: recarregamos produto e histórico.
    productData.reload();
    historyData.reload();
  }

  if (error instanceof ApiRequestError && error.status === 404) {
    return <ProductNotFound productId={productId} />;
  }

  return (
    <div className="flex flex-col gap-6">
      <div aria-live="polite" aria-busy={loading}>
        {loading && !product && (
          <div className="flex justify-center py-12">
            <Spinner label="Carregando produto…" />
          </div>
        )}
        {error && (
          <Alert
            variant="error"
            title="Não foi possível carregar o produto"
            action={
              <Button variant="secondary" onClick={reloadProduct}>
                Tentar novamente
              </Button>
            }
          >
            {error.message}
          </Alert>
        )}
      </div>

      {deleteError && (
        <Alert variant="error" title="Não foi possível excluir o produto">
          {deleteError}
        </Alert>
      )}

      {product && (
        <>
          <ProductSummary
            product={product}
            onDelete={() => setConfirmOpen(true)}
          />

          <section
            aria-labelledby="titulo-movimentar"
            className="flex flex-col gap-3"
          >
            <h2 id="titulo-movimentar" className="text-lg font-semibold">
              Registrar entrada ou saída
            </h2>
            <MovementForm
              productId={productId}
              onCreated={handleMovementCreated}
            />
          </section>
        </>
      )}

      <section
        aria-labelledby="titulo-historico"
        className="flex flex-col gap-3"
      >
        <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <h2 id="titulo-historico" className="text-lg font-semibold">
            Histórico de movimentações
          </h2>
          <Link
            href={`/movements?product_id=${productId}`}
            className="text-sm text-indigo-700 hover:underline"
          >
            Ver histórico completo
          </Link>
        </div>
        <HistorySection data={historyData} />
      </section>

      <ConfirmDialog
        open={confirmOpen}
        title="Excluir produto?"
        confirmLabel="Excluir produto"
        busy={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmOpen(false)}
      >
        <p>
          Você está prestes a excluir{" "}
          <strong>{product ? product.name : "este produto"}</strong>.
        </p>
        <p className="mt-2">
          A exclusão é <strong>lógica</strong>: o produto deixa de aparecer na
          lista e não aceita novas movimentações, mas o estoque e o{" "}
          <strong>histórico de movimentações são preservados</strong> e
          continuam consultáveis.
        </p>
      </ConfirmDialog>
    </div>
  );
}

function ProductSummary({
  product,
  onDelete,
}: {
  product: Product;
  onDelete: () => void;
}) {
  return (
    <section
      aria-labelledby="titulo-produto"
      className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-4 sm:p-6"
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 id="titulo-produto" className="text-2xl font-bold">
              {product.name}
            </h1>
            {product.low_stock && <Badge tone="danger">Estoque baixo</Badge>}
          </div>
          <p className="font-mono text-sm text-slate-500">{product.sku}</p>
        </div>
        <div className="flex gap-2">
          <Link
            href={`/products/${product.id}/edit`}
            className={buttonClasses("secondary")}
          >
            Editar
          </Link>
          <Button variant="danger" onClick={onDelete}>
            Excluir
          </Button>
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
        <Field label="Preço">{formatBRL(product.price)}</Field>
        <Field label="Quantidade em estoque">{product.quantity}</Field>
        <Field label="Estoque mínimo">{product.min_stock}</Field>
        <Field label="Situação">
          {product.low_stock ? "Abaixo do estoque mínimo" : "Estoque adequado"}
        </Field>
        <div className="col-span-2 sm:col-span-4">
          <Field label="Descrição">{product.description || "—"}</Field>
        </div>
        <Field label="Criado em">{formatDateTime(product.created_at)}</Field>
        <Field label="Atualizado em">
          {formatDateTime(product.updated_at)}
        </Field>
      </dl>
    </section>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="mt-0.5 break-words font-medium text-slate-900">
        {children}
      </dd>
    </div>
  );
}

function HistorySection({
  data,
}: {
  data: ApiData<Movement[]>;
}) {
  const { data: movements, error, loading, reload } = data;

  return (
    <div aria-live="polite" aria-busy={loading}>
      {loading && !movements && (
        <div className="flex justify-center py-8">
          <Spinner label="Carregando histórico…" />
        </div>
      )}

      {error && (
        <Alert
          variant="error"
          title="Não foi possível carregar o histórico"
          action={
            <Button variant="secondary" onClick={reload}>
              Tentar novamente
            </Button>
          }
        >
          {error.message}
        </Alert>
      )}

      {movements && movements.length === 0 && (
        <EmptyState
          title="Nenhuma movimentação registrada"
          description="As entradas e saídas deste produto aparecerão aqui."
        />
      )}

      {movements && movements.length > 0 && (
        <div className="flex flex-col gap-3">
          <MovementTable
            movements={movements.slice(0, HISTORY_LIMIT)}
            showProduct={false}
            label="Histórico do produto"
          />
          {movements.length > HISTORY_LIMIT && (
            <p className="text-xs text-slate-500">
              Exibindo as {HISTORY_LIMIT} movimentações mais recentes de{" "}
              {movements.length}.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
