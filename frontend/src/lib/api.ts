import type {
  ApiError,
  CreateMovementRequest,
  CreateProductRequest,
  Movement,
  MovementType,
  Product,
  UpdateProductRequest,
} from "@/types";

const DEFAULT_API_URL = "http://localhost:8080";

export const NETWORK_ERROR_MESSAGE =
  "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.";

/**
 * Erro tipado lançado para qualquer falha de requisição à API.
 * - Respostas de erro da API (seção 4.1): `status` HTTP, `error`, `message` e `fieldErrors`.
 * - Falha de rede (servidor fora do ar, CORS, offline): `status === 0` e `isNetworkError === true`.
 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly error: string;
  readonly fieldErrors?: Record<string, string>;

  constructor(init: {
    status: number;
    error: string;
    message: string;
    fieldErrors?: Record<string, string>;
  }) {
    super(init.message);
    this.name = "ApiRequestError";
    this.status = init.status;
    this.error = init.error;
    this.fieldErrors = init.fieldErrors;
  }

  get isNetworkError(): boolean {
    return this.status === 0;
  }
}

function getBaseUrl(): string {
  const url = process.env.NEXT_PUBLIC_API_URL || DEFAULT_API_URL;
  return url.replace(/\/+$/, "");
}

function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError";
}

async function parseErrorBody(response: Response): Promise<Partial<ApiError>> {
  try {
    const body: unknown = await response.json();
    if (body && typeof body === "object") {
      return body as Partial<ApiError>;
    }
  } catch {
    // corpo ausente ou que não é JSON: usa mensagem genérica abaixo
  }
  return {};
}

async function request<T>(
  path: string,
  init: RequestInit & { json?: unknown } = {},
): Promise<T> {
  const { json, headers, ...rest } = init;
  const finalHeaders = new Headers(headers);
  finalHeaders.set("Accept", "application/json");
  let body = rest.body;
  if (json !== undefined) {
    finalHeaders.set("Content-Type", "application/json");
    body = JSON.stringify(json);
  }

  let response: Response;
  try {
    response = await fetch(`${getBaseUrl()}${path}`, {
      ...rest,
      headers: finalHeaders,
      body,
    });
  } catch (err) {
    if (isAbortError(err)) {
      throw err;
    }
    throw new ApiRequestError({
      status: 0,
      error: "Erro de rede",
      message: NETWORK_ERROR_MESSAGE,
    });
  }

  if (!response.ok) {
    const apiError = await parseErrorBody(response);
    throw new ApiRequestError({
      status: response.status,
      error: apiError.error || `Erro ${response.status}`,
      message:
        apiError.message ||
        `A requisição falhou (HTTP ${response.status}). Tente novamente.`,
      fieldErrors: apiError.field_errors,
    });
  }

  // 204 No Content (ex.: DELETE /product/{id}) não possui corpo.
  if (response.status === 204) {
    return undefined as T;
  }

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiRequestError({
      status: response.status,
      error: "Resposta inválida",
      message: "O servidor retornou uma resposta inesperada.",
    });
  }
}

function toQueryString(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      search.set(key, value);
    }
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}

interface RequestOptions {
  signal?: AbortSignal;
}

// ---------------------------------------------------------------------------
// Produtos (seção 4.2)
// ---------------------------------------------------------------------------

/** GET /product — apenas produtos ativos; `[]` se não houver. */
export function listProducts(options: RequestOptions = {}): Promise<Product[]> {
  return request<Product[]>("/product", { signal: options.signal });
}

export interface SearchProductsParams {
  /** Texto de busca (`q`). Vazio/em branco é omitido da requisição. */
  q?: string;
  /** `true` envia `lowStock=true`; `false`/ausente não envia o parâmetro. */
  lowStock?: boolean;
}

/** GET /product/search?q=&lowStock= */
export function searchProducts(
  { q, lowStock }: SearchProductsParams = {},
  options: RequestOptions = {},
): Promise<Product[]> {
  const query = toQueryString({
    q: q?.trim() || undefined,
    lowStock: lowStock ? "true" : undefined,
  });
  return request<Product[]>(`/product/search${query}`, {
    signal: options.signal,
  });
}

/** GET /product/{id} */
export function getProduct(
  id: number,
  options: RequestOptions = {},
): Promise<Product> {
  return request<Product>(`/product/${id}`, { signal: options.signal });
}

/** POST /product/create → 201 */
export function createProduct(data: CreateProductRequest): Promise<Product> {
  return request<Product>("/product/create", { method: "POST", json: data });
}

/** PUT /product/{id} */
export function updateProduct(
  id: number,
  data: UpdateProductRequest,
): Promise<Product> {
  return request<Product>(`/product/${id}`, { method: "PUT", json: data });
}

/** DELETE /product/{id} → 204 (soft delete) */
export function deleteProduct(id: number): Promise<void> {
  return request<void>(`/product/${id}`, { method: "DELETE" });
}

// ---------------------------------------------------------------------------
// Movimentações (seção 4.3)
// ---------------------------------------------------------------------------

export interface ListMovementsParams {
  productId?: number;
  type?: MovementType;
  /** ISO date-time. */
  from?: string;
  /** ISO date-time. */
  to?: string;
}

/** GET /movements?product_id=&type=&from=&to= */
export function listMovements(
  { productId, type, from, to }: ListMovementsParams = {},
  options: RequestOptions = {},
): Promise<Movement[]> {
  const query = toQueryString({
    product_id: productId !== undefined ? String(productId) : undefined,
    type,
    from,
    to,
  });
  return request<Movement[]>(`/movements${query}`, { signal: options.signal });
}

/** POST /movements → 201 (inclui `stock_after`). */
export function createMovement(data: CreateMovementRequest): Promise<Movement> {
  return request<Movement>("/movements", { method: "POST", json: data });
}
