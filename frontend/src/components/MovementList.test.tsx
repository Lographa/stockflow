import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  apiErrorResponse,
  jsonResponse,
  makeMovement,
  makeProduct,
  routeFetch,
} from "@/test/fixtures";
import { MovementList } from "./MovementList";

const nav = vi.hoisted(() => ({
  replace: vi.fn(),
  search: new URLSearchParams(),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: nav.replace }),
  useSearchParams: () => nav.search,
}));

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  nav.replace.mockReset();
  nav.search = new URLSearchParams();
  vi.stubGlobal("fetch", fetchMock);
  vi.stubEnv("NEXT_PUBLIC_API_URL", "http://api.test");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const mouse = makeProduct({ id: 1, sku: "MOUSE-001", name: "Mouse Gamer" });
const keyboard = makeProduct({ id: 2, sku: "KEYBOARD-001", name: "Teclado" });

const activeEntry = makeMovement({
  id: 3,
  product_id: 1,
  product_sku: "MOUSE-001",
  product_name: "Mouse Gamer",
  type: "ENTRY",
  quantity: 10,
  description: "Reposição semanal",
  created_at: "2026-10-30T10:05:00",
});
const deletedExit = makeMovement({
  id: 2,
  product_id: 9,
  product_sku: "OLD-009",
  product_name: "Webcam Antiga",
  product_deleted: true,
  type: "EXIT",
  quantity: 4,
  description: null,
  created_at: "2026-10-29T15:40:00",
});

function movementRequests(): URL[] {
  return fetchMock.mock.calls
    .map(([url]) => new URL(url as string))
    .filter((url) => url.pathname === "/movements");
}

function mockApi(movements = [activeEntry, deletedExit]) {
  routeFetch(fetchMock, {
    "GET /product": () => jsonResponse([mouse, keyboard]),
    "GET /movements": () => jsonResponse(movements),
  });
}

describe("MovementList (/movements)", () => {
  it("mostra carregando e depois data, produto (nome + SKU), tipo, quantidade e descrição", async () => {
    mockApi();
    render(<MovementList />);

    expect(screen.getByRole("status")).toHaveTextContent("Carregando movimentações");

    const table = await screen.findByRole("table", { name: "Lista de movimentações" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);

    // ordem da API preservada
    expect(rows[0]).toHaveTextContent("Mouse Gamer");
    expect(rows[0]).toHaveTextContent("MOUSE-001");
    expect(rows[0]).toHaveTextContent("Entrada");
    expect(rows[0]).toHaveTextContent("10");
    expect(rows[0]).toHaveTextContent("Reposição semanal");
    expect(rows[0]).toHaveTextContent(/30\/10\/2026.*10:05/);
    expect(rows[1]).toHaveTextContent("Webcam Antiga");
    expect(rows[1]).toHaveTextContent("Saída");
    expect(rows[1]).toHaveTextContent(/29\/10\/2026.*15:40/);

    // sem filtros: GET /movements sem query
    expect(movementRequests()[0].search).toBe("");
    // versão em cartões também é renderizada
    expect(screen.getByRole("list", { name: /Lista de movimentações em cartões/ })).toBeInTheDocument();
  });

  it("produto ativo tem link para o detalhe; produto excluído mostra 'Produto excluído' e NÃO tem link", async () => {
    mockApi();
    render(<MovementList />);

    const table = await screen.findByRole("table", { name: "Lista de movimentações" });
    const [activeRow, deletedRow] = within(table).getAllByRole("row").slice(1);

    expect(within(activeRow).getByRole("link", { name: "Mouse Gamer" })).toHaveAttribute(
      "href",
      "/products/1",
    );
    expect(within(activeRow).queryByText("Produto excluído")).not.toBeInTheDocument();

    expect(within(deletedRow).getByText("Produto excluído")).toBeInTheDocument();
    expect(within(deletedRow).queryByRole("link")).not.toBeInTheDocument();
    expect(within(deletedRow).getByText("Webcam Antiga")).toBeInTheDocument();

    // o mesmo vale na lista de cartões
    const cards = screen.getByRole("list", { name: /em cartões/ });
    const deletedCard = within(cards).getAllByRole("listitem")[1];
    expect(within(deletedCard).getByText("Produto excluído")).toBeInTheDocument();
    expect(within(deletedCard).queryByRole("link")).not.toBeInTheDocument();
  });

  it("filtro por tipo envia type=ENTRY/EXIT e 'Todos' remove o parâmetro", async () => {
    const user = userEvent.setup();
    mockApi();
    render(<MovementList />);
    await screen.findByRole("table", { name: "Lista de movimentações" });

    await user.selectOptions(screen.getByLabelText("Tipo"), "EXIT");
    await screen.findByRole("table", { name: "Lista de movimentações" });
    expect(movementRequests().at(-1)?.searchParams.get("type")).toBe("EXIT");

    await user.selectOptions(screen.getByLabelText("Tipo"), "ENTRY");
    await screen.findByRole("table", { name: "Lista de movimentações" });
    expect(movementRequests().at(-1)?.searchParams.get("type")).toBe("ENTRY");

    await user.selectOptions(screen.getByLabelText("Tipo"), "");
    await screen.findByRole("table", { name: "Lista de movimentações" });
    expect(movementRequests().at(-1)?.searchParams.has("type")).toBe(false);
  });

  it("product_id da URL filtra a consulta e seleciona o produto ativo no seletor", async () => {
    nav.search = new URLSearchParams("product_id=2");
    mockApi([makeMovement({ id: 7, product_id: 2, product_sku: "KEYBOARD-001", product_name: "Teclado" })]);
    render(<MovementList />);

    await screen.findByRole("table", { name: "Lista de movimentações" });
    expect(movementRequests()[0].searchParams.get("product_id")).toBe("2");
    expect(screen.getByLabelText("Produto")).toHaveValue("2");
  });

  it("mantém como filtro ativo um product_id de produto EXCLUÍDO (fora de GET /product), com o nome vindo das movimentações", async () => {
    nav.search = new URLSearchParams("product_id=9");
    mockApi([deletedExit]);
    render(<MovementList />);

    const table = await screen.findByRole("table", { name: "Lista de movimentações" });
    expect(movementRequests()[0].searchParams.get("product_id")).toBe("9");

    const select = screen.getByLabelText("Produto");
    expect(select).toHaveValue("9");
    expect(
      within(select).getByRole("option", { name: "Webcam Antiga (OLD-009) — excluído" }),
    ).toBeInTheDocument();
    expect(within(table).getByText("Produto excluído")).toBeInTheDocument();
    expect(within(table).queryByRole("link")).not.toBeInTheDocument();
  });

  it("o nome do produto filtrado persiste ao trocar o filtro de tipo", async () => {
    const user = userEvent.setup();
    nav.search = new URLSearchParams("product_id=9");
    mockApi([deletedExit]);
    render(<MovementList />);
    await screen.findByRole("table", { name: "Lista de movimentações" });

    await user.selectOptions(screen.getByLabelText("Tipo"), "EXIT");

    const select = screen.getByLabelText("Produto");
    expect(select).toHaveValue("9");
    expect(
      within(select).getByRole("option", { name: "Webcam Antiga (OLD-009) — excluído" }),
    ).toBeInTheDocument();
  });

  it("trocar o produto navega via URL (product_id) e 'Todos' limpa o filtro", async () => {
    const user = userEvent.setup();
    mockApi();
    render(<MovementList />);
    await screen.findByRole("table", { name: "Lista de movimentações" });

    await user.selectOptions(screen.getByLabelText("Produto"), "2");
    expect(nav.replace).toHaveBeenLastCalledWith("/movements?product_id=2");

    await user.selectOptions(screen.getByLabelText("Produto"), "");
    expect(nav.replace).toHaveBeenLastCalledWith("/movements");
  });

  it("ignora product_id inválido na URL", async () => {
    nav.search = new URLSearchParams("product_id=abc");
    mockApi();
    render(<MovementList />);

    await screen.findByRole("table", { name: "Lista de movimentações" });
    expect(movementRequests()[0].searchParams.has("product_id")).toBe(false);
  });

  it("estado vazio (sem filtros e com filtros)", async () => {
    mockApi([]);
    const { unmount } = render(<MovementList />);
    expect(await screen.findByText("Nenhuma movimentação registrada")).toBeInTheDocument();
    unmount();

    nav.search = new URLSearchParams("product_id=1");
    render(<MovementList />);
    expect(await screen.findByText("Nenhuma movimentação encontrada")).toBeInTheDocument();
  });

  it("erro com 'Tentar novamente'", async () => {
    const user = userEvent.setup();
    let n = 0;
    routeFetch(fetchMock, {
      "GET /product": () => jsonResponse([mouse]),
      "GET /movements": () => {
        n += 1;
        return n === 1 ? apiErrorResponse(500, "Erro interno do servidor") : jsonResponse([activeEntry]);
      },
    });
    render(<MovementList />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Erro interno do servidor");
    await user.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(await screen.findByRole("table", { name: "Lista de movimentações" })).toBeInTheDocument();
  });

  it("product_id que nunca existiu (404): mensagem da API e link para ver todas", async () => {
    nav.search = new URLSearchParams("product_id=404");
    routeFetch(fetchMock, {
      "GET /product": () => jsonResponse([mouse]),
      "GET /movements": () => apiErrorResponse(404, "Produto não encontrado"),
    });
    render(<MovementList />);

    expect(await screen.findByText("Produto não encontrado", { selector: "p.font-semibold" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver todas as movimentações" })).toHaveAttribute(
      "href",
      "/movements",
    );
  });

  it("falha em GET /product não impede a lista de movimentações", async () => {
    routeFetch(fetchMock, {
      "GET /product": () => apiErrorResponse(500, "Erro"),
      "GET /movements": () => jsonResponse([activeEntry]),
    });
    render(<MovementList />);

    expect(await screen.findByRole("table", { name: "Lista de movimentações" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
