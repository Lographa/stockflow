import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { jsonResponse, makeProduct } from "@/test/fixtures";
import {
  ApiRequestError,
  NETWORK_ERROR_MESSAGE,
  createMovement,
  createProduct,
  deleteProduct,
  getProduct,
  listMovements,
  listProducts,
  searchProducts,
  updateProduct,
} from "./api";

const fetchMock = vi.fn();

function lastCall() {
  const [url, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit];
  return { url, init };
}

async function catchError(promise: Promise<unknown>): Promise<ApiRequestError> {
  try {
    await promise;
  } catch (err) {
    expect(err).toBeInstanceOf(ApiRequestError);
    return err as ApiRequestError;
  }
  throw new Error("A promessa deveria ter sido rejeitada");
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.stubEnv("NEXT_PUBLIC_API_URL", "http://api.test");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("api — produtos", () => {
  it("listProducts chama GET /product usando NEXT_PUBLIC_API_URL e devolve a lista", async () => {
    const products = [makeProduct(), makeProduct({ id: 2, sku: "KB-1" })];
    fetchMock.mockResolvedValue(jsonResponse(products));

    await expect(listProducts()).resolves.toEqual(products);

    const { url } = lastCall();
    expect(url).toBe("http://api.test/product");
  });

  it("ignora barra final na URL base", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_URL", "http://api.test/");
    fetchMock.mockResolvedValue(jsonResponse([]));

    await listProducts();

    expect(lastCall().url).toBe("http://api.test/product");
  });

  it("searchProducts envia q e lowStock=true", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await searchProducts({ q: "mouse", lowStock: true });

    const url = new URL(lastCall().url);
    expect(url.pathname).toBe("/product/search");
    expect(url.searchParams.get("q")).toBe("mouse");
    expect(url.searchParams.get("lowStock")).toBe("true");
  });

  it("searchProducts omite parâmetros vazios ou falsos", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await searchProducts({ q: "   ", lowStock: false });

    expect(lastCall().url).toBe("http://api.test/product/search");
  });

  it("devolve lista vazia quando a API responde []", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await expect(listProducts()).resolves.toEqual([]);
  });

  it("createProduct/updateProduct enviam JSON nos endpoints do plano", async () => {
    fetchMock.mockResolvedValue(jsonResponse(makeProduct(), 201));

    await createProduct({
      sku: "A-1",
      name: "A",
      price: 1,
      quantity: 0,
      min_stock: 0,
    });
    let call = lastCall();
    expect(call.url).toBe("http://api.test/product/create");
    expect(call.init.method).toBe("POST");
    expect(new Headers(call.init.headers).get("Content-Type")).toBe(
      "application/json",
    );
    expect(JSON.parse(call.init.body as string)).toMatchObject({ sku: "A-1" });

    fetchMock.mockResolvedValue(jsonResponse(makeProduct()));
    await updateProduct(7, { price: 10 });
    call = lastCall();
    expect(call.url).toBe("http://api.test/product/7");
    expect(call.init.method).toBe("PUT");
    expect(JSON.parse(call.init.body as string)).toEqual({ price: 10 });

    fetchMock.mockResolvedValue(jsonResponse(makeProduct()));
    await getProduct(7);
    expect(lastCall().url).toBe("http://api.test/product/7");
  });
});

describe("api — movimentações", () => {
  it("listMovements usa os nomes de parâmetro do contrato", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await listMovements({ productId: 3, type: "EXIT" });

    const url = new URL(lastCall().url);
    expect(url.pathname).toBe("/movements");
    expect(url.searchParams.get("product_id")).toBe("3");
    expect(url.searchParams.get("type")).toBe("EXIT");
  });

  it("createMovement faz POST /movements", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: 1 }, 201));

    await createMovement({ product_id: 1, type: "ENTRY", quantity: 5 });

    const { url, init } = lastCall();
    expect(url).toBe("http://api.test/movements");
    expect(init.method).toBe("POST");
  });
});

describe("api — tratamento de erros", () => {
  it("interpreta 400 de validação com field_errors", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        {
          error: "Validação falhou",
          message: "Dados inválidos",
          field_errors: { price: "O preço não pode ser negativo" },
        },
        400,
      ),
    );

    const err = await catchError(
      createProduct({ sku: "A", name: "A", price: -1, quantity: 0, min_stock: 0 }),
    );

    expect(err.status).toBe(400);
    expect(err.error).toBe("Validação falhou");
    expect(err.message).toBe("Dados inválidos");
    expect(err.fieldErrors).toEqual({ price: "O preço não pode ser negativo" });
    expect(err.isNetworkError).toBe(false);
  });

  it("interpreta 404 sem field_errors", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        { error: "Não encontrado", message: "Produto 99 não encontrado" },
        404,
      ),
    );

    const err = await catchError(getProduct(99));

    expect(err.status).toBe(404);
    expect(err.error).toBe("Não encontrado");
    expect(err.message).toBe("Produto 99 não encontrado");
    expect(err.fieldErrors).toBeUndefined();
  });

  it("interpreta 409", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ error: "Conflito", message: "SKU já cadastrado" }, 409),
    );

    const err = await catchError(
      createProduct({ sku: "A", name: "A", price: 1, quantity: 0, min_stock: 0 }),
    );

    expect(err.status).toBe(409);
    expect(err.message).toBe("SKU já cadastrado");
  });

  it("usa mensagem genérica quando o erro não tem corpo JSON (ex.: 500/401)", async () => {
    fetchMock.mockResolvedValue(new Response("<html>boom</html>", { status: 500 }));

    const err = await catchError(listProducts());

    expect(err.status).toBe(500);
    expect(err.message).toMatch(/HTTP 500/);
  });

  it("trata 204 sem corpo (DELETE)", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(deleteProduct(5)).resolves.toBeUndefined();

    const { url, init } = lastCall();
    expect(url).toBe("http://api.test/product/5");
    expect(init.method).toBe("DELETE");
  });

  it("converte falha de rede em ApiRequestError amigável", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    const err = await catchError(listProducts());

    expect(err.status).toBe(0);
    expect(err.isNetworkError).toBe(true);
    expect(err.message).toBe(NETWORK_ERROR_MESSAGE);
  });

  it("repassa AbortError sem convertê-lo em erro de rede", async () => {
    fetchMock.mockRejectedValue(new DOMException("Aborted", "AbortError"));

    await expect(listProducts()).rejects.toMatchObject({ name: "AbortError" });
  });
});
