import type { Metadata } from "next";
import { Header } from "@/components/Header";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "StockFlow",
    template: "%s | StockFlow",
  },
  description: "Gerenciamento de produtos e estoque.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="pt-BR" className="h-full antialiased">
      <body className="flex min-h-full flex-col bg-slate-50 font-sans text-slate-900">
        <a
          href="#conteudo"
          className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:rounded-md focus:bg-white focus:px-3 focus:py-2"
        >
          Ir para o conteúdo
        </a>
        <Header />
        <main
          id="conteudo"
          className="mx-auto w-full max-w-5xl flex-1 px-4 py-6"
        >
          {children}
        </main>
      </body>
    </html>
  );
}
