import { cn } from "@/lib/cn";

interface SpinnerProps {
  size?: "sm" | "md";
  /** Texto lido por leitores de tela. Use "" para um spinner puramente decorativo. */
  label?: string;
  className?: string;
}

export function Spinner({
  size = "md",
  label = "Carregando…",
  className,
}: SpinnerProps) {
  const decorative = label === "";
  return (
    <span
      role={decorative ? undefined : "status"}
      aria-hidden={decorative || undefined}
      className={cn("inline-flex items-center gap-2", className)}
    >
      <span
        aria-hidden="true"
        className={cn(
          "inline-block animate-spin rounded-full border-2 border-current border-t-transparent",
          size === "sm" ? "h-4 w-4" : "h-6 w-6 text-indigo-600",
        )}
      />
      {!decorative && <span className="sr-only">{label}</span>}
    </span>
  );
}
