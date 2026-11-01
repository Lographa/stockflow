import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export type AlertVariant = "error" | "warning" | "info" | "success";

interface AlertProps {
  variant?: AlertVariant;
  title?: string;
  children: ReactNode;
  /** Área de ação (ex.: botão "Tentar novamente"). */
  action?: ReactNode;
  className?: string;
}

const variants: Record<AlertVariant, string> = {
  error: "border-red-200 bg-red-50 text-red-800",
  warning: "border-amber-200 bg-amber-50 text-amber-900",
  info: "border-sky-200 bg-sky-50 text-sky-900",
  success: "border-green-200 bg-green-50 text-green-800",
};

export function Alert({
  variant = "info",
  title,
  children,
  action,
  className,
}: AlertProps) {
  return (
    <div
      role={variant === "error" ? "alert" : "status"}
      className={cn(
        "flex flex-col gap-3 rounded-md border p-4 text-sm sm:flex-row sm:items-center sm:justify-between",
        variants[variant],
        className,
      )}
    >
      <div>
        {title && <p className="font-semibold">{title}</p>}
        <div>{children}</div>
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}
