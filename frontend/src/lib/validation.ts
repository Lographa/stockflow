import type {
  CreateMovementRequest,
  CreateProductRequest,
  MovementType,
  Product,
  UpdateProductRequest,
} from "@/types";

/** Limites espelhando o backend (IMPLEMENTATION_PLAN.md, FIX-02 e seção 4). */
export const LIMITS = {
  sku: 50,
  name: 150,
  description: 500,
  movementDescription: 255,
  priceIntegerDigits: 8,
  priceDecimalDigits: 2,
  /** Maior `Integer` do Java (campos `quantity`/`min_stock`). */
  maxInteger: 2_147_483_647,
} as const;

export interface ProductFormValues {
  sku: string;
  name: string;
  description: string;
  price: string;
  quantity: string;
  min_stock: string;
}

export type ProductField = keyof ProductFormValues;
export type FieldErrors<K extends string> = Partial<Record<K, string>>;

export const EMPTY_PRODUCT_VALUES: ProductFormValues = {
  sku: "",
  name: "",
  description: "",
  price: "",
  quantity: "0",
  min_stock: "0",
};

/** Converte um produto da API nos valores (texto) do formulário. */
export function productToFormValues(product: Product): ProductFormValues {
  return {
    sku: product.sku,
    name: product.name,
    description: product.description ?? "",
    price: product.price.toFixed(2).replace(".", ","),
    quantity: String(product.quantity),
    min_stock: String(product.min_stock),
  };
}

type Parsed<T> = { value: T } | { error: string };

/** Preço ≥ 0, até 8 dígitos inteiros e 2 decimais; aceita vírgula ou ponto. */
export function parsePrice(raw: string): Parsed<number> {
  const text = raw.trim();
  if (text === "") return { error: "O preço é obrigatório" };
  if (text.startsWith("-")) return { error: "O preço não pode ser negativo" };

  const normalized = text.replace(",", ".");
  if (!/^\d+(\.\d+)?$/.test(normalized)) {
    return { error: "Informe um preço válido (ex.: 129,90)" };
  }
  const [integerPart, decimalPart = ""] = normalized.split(".");
  const integerDigits = integerPart.replace(/^0+(?=\d)/, "").length;
  if (integerDigits > LIMITS.priceIntegerDigits) {
    return {
      error: `O preço deve ter no máximo ${LIMITS.priceIntegerDigits} dígitos inteiros`,
    };
  }
  if (decimalPart.length > LIMITS.priceDecimalDigits) {
    return {
      error: `O preço deve ter no máximo ${LIMITS.priceDecimalDigits} casas decimais`,
    };
  }
  return { value: Number(normalized) };
}

/** Inteiro ≥ `min` (0 ou 1), dentro do intervalo de `Integer`. */
export function parseInteger(
  raw: string,
  fieldLabel: string,
  min: 0 | 1,
): Parsed<number> {
  const text = raw.trim();
  if (text === "") return { error: "Informe um número inteiro" };
  if (text.startsWith("-")) {
    return {
      error:
        min === 0
          ? `${fieldLabel} deve ser maior ou igual a zero`
          : `${fieldLabel} deve ser maior que zero`,
    };
  }
  if (!/^\d+$/.test(text)) {
    return { error: `${fieldLabel} deve ser um número inteiro` };
  }
  const value = Number(text);
  if (value > LIMITS.maxInteger) {
    return { error: `${fieldLabel} é grande demais` };
  }
  if (value < min) {
    return { error: `${fieldLabel} deve ser maior que zero` };
  }
  return { value };
}

export interface ProductValidation {
  errors: FieldErrors<ProductField>;
  /** Valores já convertidos; só existe quando `errors` está vazio. */
  parsed?: {
    sku: string;
    name: string;
    description: string;
    price: number;
    quantity: number;
    min_stock: number;
  };
}

/**
 * Valida o formulário de produto (espelha FIX-02).
 * Em edição (`original` informado), a descrição existente não pode ser
 * esvaziada: o backend rejeita strings vazias/em branco e não há como "limpar".
 */
export function validateProductForm(
  values: ProductFormValues,
  original?: Product,
): ProductValidation {
  const errors: FieldErrors<ProductField> = {};

  const sku = values.sku.trim();
  if (sku === "") errors.sku = "O SKU é obrigatório";
  else if (sku.length > LIMITS.sku) {
    errors.sku = `O SKU deve ter no máximo ${LIMITS.sku} caracteres`;
  }

  const name = values.name.trim();
  if (name === "") errors.name = "O nome é obrigatório";
  else if (name.length > LIMITS.name) {
    errors.name = `O nome deve ter no máximo ${LIMITS.name} caracteres`;
  }

  const description = values.description.trim();
  if (description.length > LIMITS.description) {
    errors.description = `A descrição deve ter no máximo ${LIMITS.description} caracteres`;
  } else if (original?.description?.trim() && description === "") {
    errors.description =
      "A descrição não pode ficar vazia: a API não permite remover uma descrição existente";
  }

  const price = parsePrice(values.price);
  if ("error" in price) errors.price = price.error;

  const quantity = parseInteger(values.quantity, "A quantidade", 0);
  if ("error" in quantity) errors.quantity = quantity.error;

  const minStock = parseInteger(values.min_stock, "O estoque mínimo", 0);
  if ("error" in minStock) errors.min_stock = minStock.error;

  if (
    Object.keys(errors).length > 0 ||
    "error" in price ||
    "error" in quantity ||
    "error" in minStock
  ) {
    return { errors };
  }

  return {
    errors,
    parsed: {
      sku,
      name,
      description,
      price: price.value,
      quantity: quantity.value,
      min_stock: minStock.value,
    },
  };
}

type ParsedProduct = NonNullable<ProductValidation["parsed"]>;

/** Corpo do POST: `description` vazia é omitida (o backend rejeita em branco). */
export function toCreateRequest(parsed: ParsedProduct): CreateProductRequest {
  const request: CreateProductRequest = {
    sku: parsed.sku,
    name: parsed.name,
    price: parsed.price,
    quantity: parsed.quantity,
    min_stock: parsed.min_stock,
  };
  if (parsed.description !== "") request.description = parsed.description;
  return request;
}

/**
 * Corpo do PUT: somente os campos que mudaram em relação ao produto original
 * (nunca strings vazias). Vazio `{}` significa "nada a salvar".
 */
export function toUpdateRequest(
  parsed: ParsedProduct,
  original: Product,
): UpdateProductRequest {
  const request: UpdateProductRequest = {};
  if (parsed.sku !== original.sku) request.sku = parsed.sku;
  if (parsed.name !== original.name) request.name = parsed.name;
  if (parsed.description !== (original.description ?? "").trim()) {
    request.description = parsed.description;
  }
  if (parsed.price !== original.price) request.price = parsed.price;
  if (parsed.quantity !== original.quantity) request.quantity = parsed.quantity;
  if (parsed.min_stock !== original.min_stock) {
    request.min_stock = parsed.min_stock;
  }
  return request;
}

// ---------------------------------------------------------------------------
// Movimentação
// ---------------------------------------------------------------------------

export interface MovementFormValues {
  type: MovementType;
  quantity: string;
  description: string;
}

export type MovementField = keyof MovementFormValues;

export interface MovementValidation {
  errors: FieldErrors<MovementField>;
  request?: Omit<CreateMovementRequest, "product_id">;
}

export function validateMovementForm(
  values: MovementFormValues,
): MovementValidation {
  const errors: FieldErrors<MovementField> = {};

  const quantity = parseInteger(values.quantity, "A quantidade", 1);
  if ("error" in quantity) errors.quantity = quantity.error;

  const description = values.description.trim();
  if (description.length > LIMITS.movementDescription) {
    errors.description = `A descrição deve ter no máximo ${LIMITS.movementDescription} caracteres`;
  }

  if (Object.keys(errors).length > 0 || "error" in quantity) {
    return { errors };
  }

  const request: Omit<CreateMovementRequest, "product_id"> = {
    type: values.type,
    quantity: quantity.value,
  };
  if (description !== "") request.description = description;
  return { errors, request };
}
