import { describe, expect, it } from "vitest";
import { makeProduct } from "@/test/fixtures";
import {
  parsePrice,
  toCreateRequest,
  toUpdateRequest,
  validateMovementForm,
  validateProductForm,
} from "./validation";
import type { ProductFormValues } from "./validation";

const valid: ProductFormValues = {
  sku: "MOUSE-001",
  name: "Mouse Gamer",
  description: "",
  price: "129,90",
  quantity: "25",
  min_stock: "10",
};

describe("validateProductForm", () => {
  it("aceita valores válidos e converte para números", () => {
    const result = validateProductForm(valid);
    expect(result.errors).toEqual({});
    expect(result.parsed).toEqual({
      sku: "MOUSE-001",
      name: "Mouse Gamer",
      description: "",
      price: 129.9,
      quantity: 25,
      min_stock: 10,
    });
  });

  it("exige sku e name (inclusive em branco) e respeita 50/150 caracteres", () => {
    expect(validateProductForm({ ...valid, sku: "  ", name: "" }).errors).toMatchObject({
      sku: "O SKU é obrigatório",
      name: "O nome é obrigatório",
    });
    expect(validateProductForm({ ...valid, sku: "a".repeat(50) }).errors.sku).toBeUndefined();
    expect(validateProductForm({ ...valid, sku: "a".repeat(51) }).errors.sku).toMatch(/50/);
    expect(validateProductForm({ ...valid, name: "a".repeat(150) }).errors.name).toBeUndefined();
    expect(validateProductForm({ ...valid, name: "a".repeat(151) }).errors.name).toMatch(/150/);
  });

  it("limita a descrição a 500 caracteres", () => {
    expect(
      validateProductForm({ ...valid, description: "a".repeat(500) }).errors.description,
    ).toBeUndefined();
    expect(
      validateProductForm({ ...valid, description: "a".repeat(501) }).errors.description,
    ).toMatch(/500/);
  });

  it("não devolve `parsed` quando há erro", () => {
    expect(validateProductForm({ ...valid, sku: "" }).parsed).toBeUndefined();
  });

  it("não permite esvaziar uma descrição existente na edição", () => {
    const original = makeProduct({ description: "Mouse com fio" });
    expect(
      validateProductForm({ ...valid, description: "  " }, original).errors.description,
    ).toMatch(/não pode ficar vazia/);
    expect(
      validateProductForm(
        { ...valid, description: "" },
        makeProduct({ description: null }),
      ).errors.description,
    ).toBeUndefined();
  });
});

describe("parsePrice", () => {
  it.each([
    ["0", 0],
    ["0,00", 0],
    ["129,90", 129.9],
    ["129.90", 129.9],
    ["12345678", 12345678],
    ["12345678,99", 12345678.99],
    ["  10  ", 10],
  ])("aceita %s", (input, expected) => {
    expect(parsePrice(input)).toEqual({ value: expected });
  });

  it.each([
    ["", /obrigatório/],
    ["-1", /negativo/],
    ["-0,01", /negativo/],
    ["abc", /válido/],
    ["1.299,90", /válido/],
    ["123456789", /8 dígitos inteiros/],
    ["1,999", /2 casas decimais/],
  ])("rejeita %s", (input, message) => {
    const result = parsePrice(input);
    expect("error" in result && result.error).toMatch(message);
  });
});

describe("quantity e min_stock", () => {
  it.each(["quantity", "min_stock"] as const)("%s: aceita 0 e rejeita negativo, decimal, texto e vazio", (field) => {
    expect(validateProductForm({ ...valid, [field]: "0" }).errors[field]).toBeUndefined();
    expect(validateProductForm({ ...valid, [field]: "-1" }).errors[field]).toMatch(/maior ou igual a zero/);
    expect(validateProductForm({ ...valid, [field]: "1,5" }).errors[field]).toMatch(/inteiro/);
    expect(validateProductForm({ ...valid, [field]: "abc" }).errors[field]).toMatch(/inteiro/);
    expect(validateProductForm({ ...valid, [field]: "" }).errors[field]).toMatch(/Informe/);
    expect(validateProductForm({ ...valid, [field]: "2147483648" }).errors[field]).toMatch(/grande/);
  });
});

describe("toCreateRequest / toUpdateRequest", () => {
  it("omite a descrição vazia no POST", () => {
    const { parsed } = validateProductForm(valid);
    expect(toCreateRequest(parsed!)).toEqual({
      sku: "MOUSE-001",
      name: "Mouse Gamer",
      price: 129.9,
      quantity: 25,
      min_stock: 10,
    });
  });

  it("envia somente campos alterados e nunca strings vazias no PUT", () => {
    const original = makeProduct();
    const { parsed } = validateProductForm({
      sku: "MOUSE-001",
      name: "Mouse Novo",
      description: "Mouse com fio",
      price: "129,90",
      quantity: "30",
      min_stock: "10",
    });
    expect(toUpdateRequest(parsed!, original)).toEqual({
      name: "Mouse Novo",
      quantity: 30,
    });
  });

  it("devolve objeto vazio quando nada mudou", () => {
    const original = makeProduct();
    const { parsed } = validateProductForm({
      sku: original.sku,
      name: original.name,
      description: original.description ?? "",
      price: "129,90",
      quantity: "25",
      min_stock: "10",
    });
    expect(toUpdateRequest(parsed!, original)).toEqual({});
  });
});

describe("validateMovementForm", () => {
  it("exige quantidade inteira > 0", () => {
    for (const quantity of ["", "0", "-3", "1,5", "abc"]) {
      const result = validateMovementForm({ type: "ENTRY", quantity, description: "" });
      expect(result.errors.quantity).toBeTruthy();
      expect(result.request).toBeUndefined();
    }
    expect(
      validateMovementForm({ type: "EXIT", quantity: "0", description: "" }).errors.quantity,
    ).toBe("A quantidade deve ser maior que zero");
  });

  it("limita a descrição a 255 e omite quando vazia", () => {
    expect(
      validateMovementForm({ type: "ENTRY", quantity: "1", description: "a".repeat(256) }).errors
        .description,
    ).toMatch(/255/);
    expect(
      validateMovementForm({ type: "EXIT", quantity: "2", description: "  " }).request,
    ).toEqual({ type: "EXIT", quantity: 2 });
    expect(
      validateMovementForm({ type: "ENTRY", quantity: "3", description: " Compra " }).request,
    ).toEqual({ type: "ENTRY", quantity: 3, description: "Compra" });
  });
});
