import type { Metadata } from "next";
import { Suspense } from "react";
import { DeletedNotice } from "@/components/DeletedNotice";
import { ProductList } from "@/components/ProductList";

export const metadata: Metadata = {
  title: "Produtos",
};

export default function ProductsPage() {
  return (
    <>
      <Suspense fallback={null}>
        <DeletedNotice />
      </Suspense>
      <ProductList />
    </>
  );
}
