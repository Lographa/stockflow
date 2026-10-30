/**
 * Tipos dos DTOs da API StockFlow (IMPLEMENTATION_PLAN.md, seção 4).
 * Os nomes dos campos são mantidos em snake_case exatamente como a API os entrega.
 */

/** Seção 4.2 — ProductResponse / ProductResponseAll. */
export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  price: number;
  quantity: number;
  min_stock: number;
  /** Calculado pelo backend (quantity < min_stock). O frontend apenas exibe. */
  low_stock: boolean;
  /** ISO-8601 sem fuso. */
  created_at: string;
  /** ISO-8601 sem fuso. */
  updated_at: string;
}

export type MovementType = "ENTRY" | "EXIT";

/** Seção 4.3 — MovementResponse. */
export interface Movement {
  id: number;
  product_id: number;
  product_sku: string;
  product_name: string;
  product_deleted: boolean;
  type: MovementType;
  quantity: number;
  description: string | null;
  /** Preenchido apenas na resposta do POST /movements; nos GET vem null. */
  stock_after: number | null;
  /** ISO-8601 sem fuso. */
  created_at: string;
}

/** Seção 4.1 — corpo de erro devolvido pela API. */
export interface ApiError {
  error: string;
  message: string;
  /** Presente apenas em 400 de validação. */
  field_errors?: Record<string, string>;
}

/** POST /product/create */
export interface CreateProductRequest {
  sku: string;
  name: string;
  description?: string;
  price: number;
  quantity: number;
  min_stock: number;
}

/** PUT /product/{id} — todos os campos são opcionais. */
export interface UpdateProductRequest {
  sku?: string;
  name?: string;
  description?: string;
  price?: number;
  quantity?: number;
  min_stock?: number;
}

/** POST /movements — o cliente nunca envia saldo. */
export interface CreateMovementRequest {
  product_id: number;
  type: MovementType;
  quantity: number;
  description?: string;
}
