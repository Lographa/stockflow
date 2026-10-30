import type { Metadata } from "next";
import { Suspense } from "react";
import { MovementList } from "@/components/MovementList";
import { Spinner } from "@/components/Spinner";

export const metadata: Metadata = {
  title: "Movimentações",
};

export default function MovementsPage() {
  // useSearchParams (?product_id=) exige <Suspense> em rotas pré-renderizadas.
  return (
    <Suspense
      fallback={
        <div className="flex justify-center py-12">
          <Spinner label="Carregando movimentações…" />
        </div>
      }
    >
      <MovementList />
    </Suspense>
  );
}
