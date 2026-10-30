import Link from "next/link";
import { formatDateTime } from "@/lib/format";
import type { Movement } from "@/types";
import { Badge } from "./Badge";

interface MovementTableProps {
  movements: Movement[];
  /** Mostra a coluna/linha de produto (nome + SKU). Desligue no histórico de um produto. */
  showProduct?: boolean;
  /** Nome acessível das listas (tabela e cartões). */
  label: string;
}

export function MovementTypeBadge({ type }: { type: Movement["type"] }) {
  return type === "ENTRY" ? (
    <Badge tone="success">Entrada</Badge>
  ) : (
    <Badge tone="warning">Saída</Badge>
  );
}

/**
 * Produto da movimentação. O link para o detalhe só existe quando o produto
 * está ativo; produtos excluídos recebem a marca "Produto excluído".
 */
function ProductCell({ movement }: { movement: Movement }) {
  return (
    <div className="flex flex-col gap-1">
      {movement.product_deleted ? (
        <>
          <span className="font-medium">{movement.product_name}</span>
          <span>
            <Badge tone="neutral">Produto excluído</Badge>
          </span>
        </>
      ) : (
        <Link
          href={`/products/${movement.product_id}`}
          className="font-medium text-indigo-700 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
        >
          {movement.product_name}
        </Link>
      )}
      <span className="font-mono text-xs text-slate-500">
        {movement.product_sku}
      </span>
    </div>
  );
}

/** Tabela em telas largas (md+) e cartões em telas estreitas. */
export function MovementTable({
  movements,
  showProduct = true,
  label,
}: MovementTableProps) {
  return (
    <>
      <div className="hidden overflow-x-auto rounded-lg border border-slate-200 bg-white md:block">
        <table className="w-full text-left text-sm" aria-label={label}>
          <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">
                Data
              </th>
              {showProduct && (
                <th scope="col" className="px-4 py-3 font-medium">
                  Produto
                </th>
              )}
              <th scope="col" className="px-4 py-3 font-medium">
                Tipo
              </th>
              <th scope="col" className="px-4 py-3 text-right font-medium">
                Quantidade
              </th>
              <th scope="col" className="px-4 py-3 font-medium">
                Descrição
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {movements.map((movement) => (
              <tr key={movement.id}>
                <td className="whitespace-nowrap px-4 py-3">
                  {formatDateTime(movement.created_at)}
                </td>
                {showProduct && (
                  <td className="px-4 py-3">
                    <ProductCell movement={movement} />
                  </td>
                )}
                <td className="px-4 py-3">
                  <MovementTypeBadge type={movement.type} />
                </td>
                <td className="px-4 py-3 text-right">{movement.quantity}</td>
                <td className="px-4 py-3 text-slate-700">
                  {movement.description || "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul
        aria-label={`${label} em cartões`}
        className="flex flex-col gap-3 md:hidden"
      >
        {movements.map((movement) => (
          <li
            key={movement.id}
            className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-4"
          >
            <div className="flex items-start justify-between gap-2">
              {showProduct ? (
                <ProductCell movement={movement} />
              ) : (
                <span className="text-sm text-slate-600">
                  {formatDateTime(movement.created_at)}
                </span>
              )}
              <MovementTypeBadge type={movement.type} />
            </div>
            {showProduct && (
              <p className="text-xs text-slate-500">
                {formatDateTime(movement.created_at)}
              </p>
            )}
            <p className="text-sm">
              <span className="text-slate-500">Quantidade: </span>
              <span className="font-medium">{movement.quantity}</span>
            </p>
            {movement.description && (
              <p className="text-sm text-slate-700">{movement.description}</p>
            )}
          </li>
        ))}
      </ul>
    </>
  );
}
