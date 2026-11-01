import Link from "next/link";
import { Suspense } from "react";
import { NAV_ITEMS } from "@/lib/navigation";
import { NavLinks } from "./NavLinks";

/** Fallback estático (sem destaque do item ativo) enquanto o pathname não está disponível. */
function NavFallback() {
  return (
    <ul className="flex items-center gap-1">
      {NAV_ITEMS.map((item) => (
        <li key={item.href}>
          <Link
            href={item.href}
            className="rounded-md px-3 py-2 text-sm font-medium text-slate-600"
          >
            {item.label}
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function Header() {
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <Link
          href="/products"
          className="text-lg font-bold text-indigo-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
        >
          StockFlow
        </Link>
        <nav aria-label="Navegação principal">
          <Suspense fallback={<NavFallback />}>
            <NavLinks />
          </Suspense>
        </nav>
      </div>
    </header>
  );
}
