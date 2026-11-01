"use client";

import { useRouter } from "next/navigation";
import { createProduct } from "@/lib/api";
import type { CreateProductRequest } from "@/types";
import { ProductForm } from "./ProductForm";

/** /products/new — cadastra o produto (201) e abre o detalhe. */
export function NewProduct() {
  const router = useRouter();

  async function handleSubmit(data: CreateProductRequest) {
    const created = await createProduct(data);
    router.push(`/products/${created.id}`);
  }

  return (
    <section aria-labelledby="titulo-novo-produto" className="flex flex-col gap-4">
      <h1 id="titulo-novo-produto" className="text-2xl font-bold">
        Novo produto
      </h1>
      <ProductForm
        mode="create"
        onSubmit={handleSubmit}
        cancelHref="/products"
      />
    </section>
  );
}
