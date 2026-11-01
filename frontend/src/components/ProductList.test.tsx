import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { jsonResponse, makeProduct } from "@/test/fixtures";
import { ProductList } from "./ProductList";

const fetchMock = vi.fn();

function requestedUrls(): URL[] {
  return fetchMock.mock.calls.map(([url]) => new URL(url as string));
}

function lastUrl(): URL {
  return requestedUrls().at(-1) as URL;
}

const normal = makeProduct({ id: 1, sku: "MOUSE-001", name: "Mouse Gamer" });
const low = makeProduct({
  id: 2,
  sku: "KEYBOARD-001",
  name: "Teclado",
  quantity: 8,
  min_stock: 10,
  low_stock: true,
});

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.stubEnv("NEXT_PUBLIC_API_URL", "http://api.test");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("ProductList", () => {
  it("mostra spinner enquanto carrega", () => {
    fetchMock.mockReturnValue(new Promise(() => {}));

    render(<ProductList />);

    expect(screen.getByRole("status")).toHaveTextContent("Carregando produtos");
  });

  it("renderiza produtos com SKU, nome, preço em BRL, quantidade e estoque mínimo", async () => {
    fetchMock.mockResolvedValue(jsonResponse([normal]));

    render(<ProductList />);

    const table = await screen.findByRole("table", {
      name: "Lista de produtos",
    });
    const row = within(table).getByRole("row", { name: /MOUSE-001/ });
    expect(within(row).getByText("MOUSE-001")).toBeInTheDocument();
    expect(within(row).getByText("Mouse Gamer")).toBeInTheDocument();
    expect(within(row).getByText(/R\$\s?129,90/)).toBeInTheDocument();
    expect(within(row).getByText("25")).toBeInTheDocument();
    expect(within(row).getByText("10")).toBeInTheDocument();

    // sem filtros usa GET /product
    expect(lastUrl().pathname).toBe("/product");
    expect(lastUrl().search).toBe("");
  });

  it("renderiza também a versão em cards", async () => {
    fetchMock.mockResolvedValue(jsonResponse([normal, low]));

    render(<ProductList />);

    const cards = await screen.findByRole("list", {
      name: "Lista de produtos em cartões",
    });
    expect(within(cards).getAllByRole("listitem")).toHaveLength(2);
  });

  it("mostra o badge 'Estoque baixo' somente para low_stock=true e o contador", async () => {
    fetchMock.mockResolvedValue(jsonResponse([normal, low]));

    render(<ProductList />);

    const table = await screen.findByRole("table", {
      name: "Lista de produtos",
    });
    const normalRow = within(table).getByRole("row", { name: /MOUSE-001/ });
    const lowRow = within(table).getByRole("row", { name: /KEYBOARD-001/ });
    expect(within(normalRow).queryByText("Estoque baixo")).not.toBeInTheDocument();
    expect(within(lowRow).getByText("Estoque baixo")).toBeInTheDocument();

    // table + cards: um badge em cada visão, apenas para o produto com low_stock
    expect(screen.getAllByText("Estoque baixo")).toHaveLength(2);
    expect(screen.getByTestId("low-stock-counter")).toHaveTextContent(
      "1 produto precisa de reposição",
    );
  });

  it("usa o campo low_stock da API e não recalcula a regra (quantity < min_stock com low_stock=false)", async () => {
    const inconsistent = makeProduct({
      id: 3,
      sku: "X-1",
      quantity: 1,
      min_stock: 100,
      low_stock: false,
    });
    fetchMock.mockResolvedValue(jsonResponse([inconsistent]));

    render(<ProductList />);

    await screen.findByRole("table", { name: "Lista de produtos" });
    expect(screen.queryByText("Estoque baixo")).not.toBeInTheDocument();
    expect(screen.queryByTestId("low-stock-counter")).not.toBeInTheDocument();
  });

  it("contador no plural e ausente quando não há itens a repor", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse([low, makeProduct({ ...low, id: 4, sku: "K-2" })]),
    );

    render(<ProductList />);

    expect(await screen.findByTestId("low-stock-counter")).toHaveTextContent(
      "2 produtos precisam de reposição",
    );
  });

  it("mostra estado vazio quando não há produtos", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    render(<ProductList />);

    expect(
      await screen.findByText("Nenhum produto cadastrado"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(
      screen.getAllByRole("link", { name: "Novo produto" })[0],
    ).toHaveAttribute("href", "/products/new");
  });

  it("mostra erro da API com botão 'Tentar novamente' que refaz a requisição", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: "Erro interno", message: "Falha inesperada no servidor" }, 500),
    );
    fetchMock.mockResolvedValueOnce(jsonResponse([normal]));

    render(<ProductList />);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Falha inesperada no servidor");

    await user.click(screen.getByRole("button", { name: "Tentar novamente" }));

    expect(
      await screen.findByRole("table", { name: "Lista de produtos" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("mostra mensagem amigável em falha de rede", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    render(<ProductList />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /Não foi possível conectar ao servidor/,
    );
  });

  it("filtro 'Somente estoque baixo' chama a API com lowStock=true", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValueOnce(jsonResponse([normal, low]));
    fetchMock.mockResolvedValueOnce(jsonResponse([low]));

    render(<ProductList />);
    await screen.findByRole("table", { name: "Lista de produtos" });

    await user.click(
      screen.getByRole("checkbox", { name: "Somente estoque baixo" }),
    );

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(lastUrl().pathname).toBe("/product/search");
    expect(lastUrl().searchParams.get("lowStock")).toBe("true");
    expect(lastUrl().searchParams.has("q")).toBe(false);

    const table = await screen.findByRole("table", {
      name: "Lista de produtos",
    });
    await waitFor(() =>
      expect(
        within(table).queryByRole("row", { name: /MOUSE-001/ }),
      ).not.toBeInTheDocument(),
    );
    expect(within(table).getByRole("row", { name: /KEYBOARD-001/ })).toBeInTheDocument();
  });

  it("busca por texto envia q (com debounce, uma única requisição)", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValueOnce(jsonResponse([normal, low]));
    fetchMock.mockResolvedValue(jsonResponse([normal]));

    render(<ProductList />);
    await screen.findByRole("table", { name: "Lista de produtos" });

    await user.type(screen.getByRole("searchbox", { name: "Buscar produtos" }), "mouse");

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(lastUrl().pathname).toBe("/product/search");
    expect(lastUrl().searchParams.get("q")).toBe("mouse");
    expect(lastUrl().searchParams.has("lowStock")).toBe(false);

    // o debounce evita uma requisição por tecla digitada
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("mostra estado vazio específico quando a busca não encontra nada", async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValueOnce(jsonResponse([normal]));
    fetchMock.mockResolvedValueOnce(jsonResponse([]));

    render(<ProductList />);
    await screen.findByRole("table", { name: "Lista de produtos" });

    await user.type(screen.getByRole("searchbox", { name: "Buscar produtos" }), "zzz");

    expect(
      await screen.findByText("Nenhum produto encontrado"),
    ).toBeInTheDocument();
  });
});
