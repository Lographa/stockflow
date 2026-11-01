import type { Metadata } from "next";
import { Suspense } from "react";
import { EditProduct } from "@/components/EditProduct";
import { Spinner } from "@/components/Spinner";

export const metadata: Metadata = {
  title: "Editar produto",
};

type RouteParams = Promise<{ id: string }>;

async function EditProductRoute({ params }: { params: RouteParams }) {
  const { id } = await params;
  return <EditProduct productId={id} />;
}

export default function EditProductPage({
  params,
}: {
  params: RouteParams;
}) {
  return (
    <Suspense
      fallback={
        <div className="flex justify-center py-12">
          <Spinner label="Carregando produto…" />
        </div>
      }
    >
      <EditProductRoute params={params} />
    </Suspense>
  );
}
