import type { ButtonHTMLAttributes, Ref } from "react";
import { cn } from "@/lib/cn";
import { Spinner } from "./Spinner";

export type ButtonVariant = "primary" | "secondary" | "danger";
export type ButtonSize = "sm" | "md";

/** Classes compartilhadas para que links (`<Link>`) tenham a mesma aparência de botões. */
export function buttonClasses(
  variant: ButtonVariant = "primary",
  size: ButtonSize = "md",
  className?: string,
): string {
  return cn(
    "inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600",
    "disabled:cursor-not-allowed disabled:opacity-60",
    size === "sm" ? "px-3 py-1.5 text-sm" : "px-4 py-2 text-sm",
    variant === "primary" && "bg-indigo-600 text-white hover:bg-indigo-700",
    variant === "secondary" &&
      "border border-slate-300 bg-white text-slate-700 hover:bg-slate-50",
    variant === "danger" && "bg-red-600 text-white hover:bg-red-700",
    className,
  );
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Desabilita o botão e mostra um indicador de carregamento. */
  loading?: boolean;
  ref?: Ref<HTMLButtonElement>;
}

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  disabled,
  className,
  children,
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={buttonClasses(variant, size, className)}
      {...props}
    >
      {loading && <Spinner size="sm" label="" />}
      {children}
    </button>
  );
}
