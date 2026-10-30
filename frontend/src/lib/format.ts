const brlFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

const dateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

/** Formata um valor numérico como moeda brasileira (apenas exibição). */
export function formatBRL(value: number): string {
  return brlFormatter.format(value);
}

/**
 * Formata um date-time ISO-8601 (sem fuso, como a API devolve) em pt-BR,
 * ex.: "30/10/2026 10:05". Valores inválidos são devolvidos como vieram.
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return dateTimeFormatter.format(date);
}
