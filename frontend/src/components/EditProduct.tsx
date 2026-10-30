"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiRequestError, getProduct, updateProduct } from "@/lib/api";
import { parseId } from "@/lib/ids";
import { useApiData } from "@/lib/useApiData";
import type { UpdateProductRequest } from "@/types";
import { Alert } from "./Alert";
import { Button } from "./Button";
import { ProductForm } from "./ProductForm";
import { ProductNotFound } from "./ProductNotFound";
import { Spinner } from "./Spinner";

/** /products/[id]/edit — pré-carrega o produto e envia o PUT. */
export function EditProduct({ productId: rawId }: { productId: string }) {
  const productId = parseId(rawId);

  if (productId === undefined) {
    return <ProductNotFound />;
  }
  return <EditProductLoader productId={productId} />;
}

function EditProductLoader({ productId }: { productId: number }) {
  const router = useRouter();
  const { data: product, error, loading, reload } = useApiData(
    (signal) => getProduct(productId, { signal }),
    String(productId),
  );

  async function handleSubmit(changes: UpdateProductRequest) {
    await updateProduct(productId, changes);
    router.push(`/products/${productId}`);
  }

  if (error instanceof ApiRequestError && error.status === 404) {
    return <ProductNotFound productId={productId} />;
  }

  return (
    <section aria-labelledby="titulo-editar-produto" className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h1 id="titulo-editar-produto" className="text-2xl font-bold">
          Editar produto
        </h1>
        <Link
          href={`/products/${productId}`}
          className="text-sm text-indigo-700 hover:underline"
        >
          ← Voltar ao detalhe
        </Link>
      </div>

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
              <Button variant="secondary" onClick={reload}>
                Tentar novamente
              </Button>
            }
          >
            {error.message}
          </Alert>
        )}
      </div>

      {product && (
        <ProductForm
          mode="edit"
          product={product}
          onSubmit={handleSubmit}
          cancelHref={`/products/${productId}`}
        />
      )}
    </section>
  );
}
