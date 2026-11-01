"use client";

import { useSearchParams } from "next/navigation";
import { Alert } from "./Alert";

/** Feedback exibido em /products após excluir um produto (`?deleted=1`). */
export function DeletedNotice() {
  const searchParams = useSearchParams();
  if (searchParams.get("deleted") !== "1") return null;

  return (
    <Alert variant="success" className="mb-4">
      Produto excluído com sucesso. O histórico de movimentações foi preservado.
    </Alert>
  );
}
