import { useId } from "react";
import type { SelectHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string;
  hint?: string;
}

/** Select nativo com o mesmo visual/acessibilidade do `Input`. */
export function Select({
  label,
  error,
  hint,
  id,
  className,
  children,
  ...props
}: SelectProps) {
  const generatedId = useId();
  const selectId = id ?? generatedId;
  const errorId = `${selectId}-error`;
  const hintId = `${selectId}-hint`;
  const describedBy =
    [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(" ") ||
    undefined;

  return (
    <div className="flex flex-col gap-1">
      <label
        htmlFor={selectId}
        className="text-sm font-medium text-slate-700"
      >
        {label}
      </label>
      <select
        id={selectId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(
          "w-full rounded-md border bg-white px-3 py-2 text-sm text-slate-900",
          "focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-indigo-600",
          "disabled:cursor-not-allowed disabled:bg-slate-100",
          error ? "border-red-500" : "border-slate-300",
          className,
        )}
        {...props}
      >
        {children}
      </select>
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
