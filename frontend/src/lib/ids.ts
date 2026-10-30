/** Converte um id vindo da URL em número; `undefined` se não for um inteiro positivo. */
export function parseId(raw: string | null | undefined): number | undefined {
  if (!raw || !/^\d+$/.test(raw)) return undefined;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : undefined;
}
