import { useId } from "react";
import type { InputHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** Mensagem de erro do campo (validação local ou `field_errors` da API). */
  error?: string;
  /** Texto de ajuda exibido abaixo do campo. */
  hint?: string;
  /** Esconde o label visualmente, mantendo-o acessível a leitores de tela. */
  hideLabel?: boolean;
}

export function Input({
  label,
  error,
  hint,
  hideLabel = false,
  id,
  className,
  ...props
}: InputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorId = `${inputId}-error`;
  const hintId = `${inputId}-hint`;
  const describedBy =
    [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(" ") ||
    undefined;

  return (
    <div className="flex flex-col gap-1">
      <label
        htmlFor={inputId}
        className={cn(
          "text-sm font-medium text-slate-700",
          hideLabel && "sr-only",
        )}
      >
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(
          "w-full rounded-md border bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400",
          "focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-indigo-600",
          "disabled:cursor-not-allowed disabled:bg-slate-100",
          error ? "border-red-500" : "border-slate-300",
          className,
        )}
        {...props}
      />
      {hint && (
        <p id={hintId} className="text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}
