import Link from "next/link";
import { buttonClasses } from "./Button";
import { EmptyState } from "./EmptyState";

interface ProductNotFoundProps {
  /** Id numérico válido, quando existir (habilita o link do histórico). */
  productId?: number;
  message?: string;
}

/**
 * Produto inexistente ou excluído (404). O histórico de movimentações de um
 * produto excluído continua acessível, então oferecemos o link para ele.
 */
export function ProductNotFound({ productId, message }: ProductNotFoundProps) {
  return (
    <EmptyState
      title="Produto não encontrado"
      description={
        message ??
        "Este produto não existe ou foi excluído. Produtos excluídos não aparecem na lista nem aceitam movimentações."
      }
      action={
        <div className="flex flex-col gap-2 sm:flex-row">
          <Link href="/products" className={buttonClasses("primary")}>
            Voltar para produtos
          </Link>
          {productId !== undefined && (
            <Link
              href={`/movements?product_id=${productId}`}
              className={buttonClasses("secondary")}
            >
              Ver histórico de movimentações
            </Link>
          )}
        </div>
      }
    />
  );
}
