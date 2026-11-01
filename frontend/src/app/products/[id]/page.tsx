import type { Metadata } from "next";
import { Suspense } from "react";
import { ProductDetail } from "@/components/ProductDetail";
import { Spinner } from "@/components/Spinner";

export const metadata: Metadata = {
  title: "Detalhe do produto",
};

type RouteParams = Promise<{ id: string }>;

// `params` é assíncrono e, sem generateStaticParams, é dado de runtime
// (cacheComponents): o acesso fica dentro de <Suspense>.
async function ProductDetailRoute({ params }: { params: RouteParams }) {
  const { id } = await params;
  return <ProductDetail productId={id} />;
}

export default function ProductDetailPage({
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
      <ProductDetailRoute params={params} />
    </Suspense>
  );
}
