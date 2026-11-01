import type { Mock } from "vitest";
import type { Movement, Product } from "@/types";

export function makeProduct(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    sku: "MOUSE-001",
    name: "Mouse Gamer",
    description: "Mouse com fio",
    price: 129.9,
    quantity: 25,
    min_stock: 10,
    low_stock: false,
    created_at: "2026-10-30T10:00:00",
    updated_at: "2026-10-30T10:00:00",
    ...overrides,
  };
}

/** Cria uma `Response` JSON para mocks de `fetch`. */
export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function makeMovement(overrides: Partial<Movement> = {}): Movement {
  return {
    id: 10,
    product_id: 1,
    product_sku: "MOUSE-001",
    product_name: "Mouse Gamer",
    product_deleted: false,
    type: "ENTRY",
    quantity: 5,
    description: "Entrada de estoque",
    stock_after: null,
    created_at: "2026-10-30T10:05:00",
    ...overrides,
  };
}

/** Corpo de erro da API (seção 4.1). */
export function apiErrorResponse(
  status: number,
  message: string,
  fieldErrors?: Record<string, string>,
): Response {
  return jsonResponse(
    {
      error: status === 409 ? "Conflito" : status === 404 ? "Não encontrado" : "Validação falhou",
      message,
      ...(fieldErrors ? { field_errors: fieldErrors } : {}),
    },
    status,
  );
}

export interface FakeRequest {
  url: URL;
  method: string;
  /** Corpo JSON já interpretado (ou undefined). */
  body: unknown;
}

type RouteHandler = (request: FakeRequest) => Response | Promise<Response>;

/**
 * Configura um mock de `fetch` que roteia por "MÉTODO /caminho" (sem query).
 * Cada chamada executa o handler novamente, então ele deve criar uma
 * `Response` nova a cada vez. Rotas não declaradas falham o teste (500).
 */
export function routeFetch(
  fetchMock: Mock,
  routes: Record<string, RouteHandler>,
): void {
  fetchMock.mockImplementation(async (input: string, init?: RequestInit) => {
    const url = new URL(input);
    const method = (init?.method ?? "GET").toUpperCase();
    const handler = routes[`${method} ${url.pathname}`];
    if (!handler) {
      return jsonResponse(
        { error: "Teste", message: `Rota sem mock: ${method} ${url.pathname}` },
        500,
      );
    }
    const body =
      typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    return handler({ url, method, body });
  });
}
