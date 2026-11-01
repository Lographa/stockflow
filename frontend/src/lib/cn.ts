/** Junta nomes de classe ignorando valores falsos (substitui clsx, sem dependência extra). */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(" ");
}
